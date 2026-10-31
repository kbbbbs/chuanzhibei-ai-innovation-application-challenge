from __future__ import annotations

import argparse
import os
import platform
import shutil
import subprocess
import sys
import tarfile
import urllib.request
import zipfile
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
VENV_DIR = SCRIPT_DIR / ".venv"
REQUIREMENTS_FILE = SCRIPT_DIR / "requirements.txt"
TOOLS_DIR = SCRIPT_DIR / "tools"
LOCAL_FFMPEG_DIR = TOOLS_DIR / "ffmpeg"
RUNTIME_ENV_FILE = SCRIPT_DIR / ".service_env"
DEFAULT_FFMPEG_URL = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip"


def is_windows() -> bool:
    return platform.system().lower() == "windows"


def exe_name(name: str) -> str:
    return f"{name}.exe" if is_windows() else name


def venv_python() -> Path:
    if is_windows():
        return VENV_DIR / "Scripts" / "python.exe"
    return VENV_DIR / "bin" / "python"


def step(message: str) -> None:
    print(f"[STEP] {message}", flush=True)


def ok(message: str) -> None:
    print(f"[OK] {message}", flush=True)


def warn(message: str) -> None:
    print(f"[WARN] {message}", flush=True)


def run(command: list[str | os.PathLike[str]], *, env: dict[str, str] | None = None) -> None:
    printable = " ".join(str(part) for part in command)
    step(printable)
    subprocess.run([str(part) for part in command], check=True, env=env)


def resolve_ffmpeg_bin_from_value(value: str | None) -> Path | None:
    if not value or not value.strip():
        return None

    candidate = Path(value.strip().strip('"')).expanduser()
    ffmpeg_file = exe_name("ffmpeg")

    if candidate.is_file() and candidate.name.lower() == ffmpeg_file.lower():
        return candidate.parent.resolve()

    if candidate.is_dir():
        direct = candidate / ffmpeg_file
        nested = candidate / "bin" / ffmpeg_file
        if direct.is_file():
            return candidate.resolve()
        if nested.is_file():
            return nested.parent.resolve()

    return None


def ffmpeg_executable_from_bin(bin_dir: Path | None) -> Path | None:
    if not bin_dir:
        return None
    candidate = bin_dir / exe_name("ffmpeg")
    return candidate if candidate.is_file() else None


def test_ffmpeg_bin(bin_dir: Path | None) -> bool:
    executable = ffmpeg_executable_from_bin(bin_dir)
    if not executable:
        return False
    try:
        subprocess.run(
            [str(executable), "-version"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=True,
        )
        return True
    except (OSError, subprocess.CalledProcessError):
        return False


def resolve_existing_ffmpeg_bin() -> Path | None:
    for name in ("YTDLP_FFMPEG_LOCATION", "FFMPEG_PATH", "FFMPEG_HOME"):
        for value in iter_environment_values(name):
            resolved = resolve_ffmpeg_bin_from_value(value)
            if resolved:
                return resolved

    path_command = shutil.which(exe_name("ffmpeg")) or shutil.which("ffmpeg")
    if path_command:
        return Path(path_command).resolve().parent

    if is_windows():
        registry_path = os.pathsep.join(iter_environment_values("Path"))
        path_command = shutil.which("ffmpeg.exe", path=registry_path) if registry_path else None
        if path_command:
            return Path(path_command).resolve().parent

    local = LOCAL_FFMPEG_DIR / "bin"
    if test_ffmpeg_bin(local):
        return local.resolve()

    return None


def iter_environment_values(name: str) -> list[str]:
    values: list[str] = []
    process_value = os.environ.get(name)
    if process_value:
        values.append(process_value)

    if is_windows():
        values.extend(read_windows_environment_value(name, current_user=True))
        values.extend(read_windows_environment_value(name, current_user=False))

    seen: set[str] = set()
    unique_values: list[str] = []
    for value in values:
        normalized = value.strip()
        if normalized and normalized.lower() not in seen:
            seen.add(normalized.lower())
            unique_values.append(normalized)
    return unique_values


def read_windows_environment_value(name: str, *, current_user: bool) -> list[str]:
    try:
        import winreg
    except ImportError:
        return []

    hive = winreg.HKEY_CURRENT_USER if current_user else winreg.HKEY_LOCAL_MACHINE
    subkey = "Environment" if current_user else r"SYSTEM\CurrentControlSet\Control\Session Manager\Environment"

    try:
        with winreg.OpenKey(hive, subkey) as key:
            value, _ = winreg.QueryValueEx(key, name)
    except OSError:
        return []

    if not isinstance(value, str):
        return []

    expanded = os.path.expandvars(value)
    if name.lower() == "path":
        return [part for part in expanded.split(os.pathsep) if part.strip()]
    return [expanded]


def clear_path_if_inside(path: Path, allowed_root: Path) -> None:
    if not path.exists():
        return

    root = allowed_root.resolve()
    target = path.resolve()
    if root != target and root not in target.parents:
        raise RuntimeError(f"Refusing to remove path outside tools directory: {target}")

    if target.is_dir():
        shutil.rmtree(target)
    else:
        target.unlink()


def download_file(url: str, target: Path) -> None:
    with urllib.request.urlopen(url) as response:
        with target.open("wb") as output:
            shutil.copyfileobj(response, output)


def extract_archive(archive_path: Path, extract_dir: Path) -> None:
    suffixes = "".join(archive_path.suffixes).lower()
    if suffixes.endswith(".zip"):
        with zipfile.ZipFile(archive_path) as archive:
            archive.extractall(extract_dir)
        return
    if suffixes.endswith((".tar.gz", ".tgz", ".tar.xz", ".tar.bz2", ".tar")):
        with tarfile.open(archive_path) as archive:
            archive.extractall(extract_dir)
        return
    raise RuntimeError(f"Unsupported ffmpeg archive format: {archive_path.name}")


def find_ffmpeg_binary(root: Path) -> Path | None:
    ffmpeg_file = exe_name("ffmpeg")
    for path in root.rglob(ffmpeg_file):
        if path.is_file():
            return path
    if not is_windows():
        for path in root.rglob("ffmpeg"):
            if path.is_file():
                return path
    return None


def install_local_ffmpeg(force: bool, skip_download: bool) -> Path:
    if skip_download:
        raise RuntimeError(
            "ffmpeg was not found and --skip-ffmpeg-download was set. "
            "Set YTDLP_FFMPEG_LOCATION/FFMPEG_PATH/FFMPEG_HOME or remove the flag."
        )

    download_url = os.environ.get("YTDLP_FFMPEG_DOWNLOAD_URL")
    if not download_url:
        if not is_windows():
            raise RuntimeError(
                "ffmpeg was not found. Install it with your OS package manager, "
                "or set YTDLP_FFMPEG_DOWNLOAD_URL to a zip/tar archive that contains ffmpeg."
            )
        download_url = DEFAULT_FFMPEG_URL

    TOOLS_DIR.mkdir(parents=True, exist_ok=True)
    archive_path = TOOLS_DIR / ("ffmpeg-download.zip" if download_url.lower().endswith(".zip") else "ffmpeg-download.archive")
    extract_dir = TOOLS_DIR / "_ffmpeg_extract"

    step("Downloading ffmpeg into local tools directory")
    clear_path_if_inside(archive_path, TOOLS_DIR)
    clear_path_if_inside(extract_dir, TOOLS_DIR)
    if force:
        clear_path_if_inside(LOCAL_FFMPEG_DIR, TOOLS_DIR)

    download_file(download_url, archive_path)
    extract_dir.mkdir(parents=True, exist_ok=True)
    extract_archive(archive_path, extract_dir)

    ffmpeg_binary = find_ffmpeg_binary(extract_dir)
    if not ffmpeg_binary:
        raise RuntimeError(f"ffmpeg binary was not found in downloaded archive: {download_url}")

    source_bin = ffmpeg_binary.parent
    source_root = source_bin.parent
    clear_path_if_inside(LOCAL_FFMPEG_DIR, TOOLS_DIR)
    shutil.copytree(source_root, LOCAL_FFMPEG_DIR)
    if not is_windows():
        local_binary = LOCAL_FFMPEG_DIR / source_bin.name / "ffmpeg"
        if local_binary.exists():
            local_binary.chmod(local_binary.stat().st_mode | 0o111)

    clear_path_if_inside(archive_path, TOOLS_DIR)
    clear_path_if_inside(extract_dir, TOOLS_DIR)

    local_bin = LOCAL_FFMPEG_DIR / source_bin.name
    if not test_ffmpeg_bin(local_bin):
        raise RuntimeError(f"Local ffmpeg validation failed: {local_bin}")
    return local_bin.resolve()


def write_runtime_env(ffmpeg_bin: Path) -> None:
    RUNTIME_ENV_FILE.write_text(
        f"YTDLP_FFMPEG_LOCATION={ffmpeg_bin}\n",
        encoding="utf-8",
    )
    ok(f"runtime env written: {RUNTIME_ENV_FILE}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Prepare dependencies for ai-learning-ytdlp-service.")
    parser.add_argument("--force-python-deps", action="store_true", help="Force reinstall Python dependencies.")
    parser.add_argument("--force-ffmpeg-download", action="store_true", help="Download ffmpeg even if one is available.")
    parser.add_argument("--skip-ffmpeg-download", action="store_true", help="Fail instead of downloading ffmpeg.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    print("==========================================")
    print("  AI Academy - yt-dlp service setup")
    print("==========================================")
    print()

    python = venv_python()
    if not python.is_file():
        step("Creating Python virtual environment")
        run([sys.executable, "-m", "venv", VENV_DIR])
    else:
        ok(f"Python virtual environment exists: {VENV_DIR}")

    if not REQUIREMENTS_FILE.is_file():
        raise RuntimeError(f"requirements.txt was not found: {REQUIREMENTS_FILE}")

    step("Installing/updating Python dependencies")
    run([python, "-m", "pip", "install", "--upgrade", "pip"])
    pip_args = [python, "-m", "pip", "install"]
    if args.force_python_deps:
        pip_args += ["--upgrade", "--force-reinstall"]
    pip_args += ["-r", REQUIREMENTS_FILE]
    run(pip_args)

    ffmpeg_bin = resolve_existing_ffmpeg_bin()
    if args.force_ffmpeg_download or not test_ffmpeg_bin(ffmpeg_bin):
        ffmpeg_bin = install_local_ffmpeg(args.force_ffmpeg_download, args.skip_ffmpeg_download)
    else:
        ok(f"ffmpeg is available: {ffmpeg_bin}")

    os.environ["YTDLP_FFMPEG_LOCATION"] = str(ffmpeg_bin)
    write_runtime_env(ffmpeg_bin)
    ok(f"YTDLP_FFMPEG_LOCATION={ffmpeg_bin}")
    print()
    ok("Dependency setup complete")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"[ERROR] {exc}", file=sys.stderr)
        raise SystemExit(1)
