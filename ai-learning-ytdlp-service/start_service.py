from __future__ import annotations

import os
import platform
import subprocess
import sys
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
VENV_DIR = SCRIPT_DIR / ".venv"
SETUP_SCRIPT = SCRIPT_DIR / "setup_service.py"
RUNTIME_ENV_FILE = SCRIPT_DIR / ".service_env"


def is_windows() -> bool:
    return platform.system().lower() == "windows"


def venv_python() -> Path:
    if is_windows():
        return VENV_DIR / "Scripts" / "python.exe"
    return VENV_DIR / "bin" / "python"


def load_runtime_env(env: dict[str, str]) -> None:
    if not RUNTIME_ENV_FILE.is_file():
        return
    for line in RUNTIME_ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        env.setdefault(key.strip(), value.strip())


def main() -> int:
    if not SETUP_SCRIPT.is_file():
        print(f"[ERROR] setup script was not found: {SETUP_SCRIPT}", file=sys.stderr)
        return 1

    subprocess.run([sys.executable, str(SETUP_SCRIPT)], cwd=SCRIPT_DIR, check=True)

    python = venv_python()
    if not python.is_file():
        print(f"[ERROR] Python virtual environment was not created: {python}", file=sys.stderr)
        return 1

    env = os.environ.copy()
    load_runtime_env(env)

    host = env.get("YTDLP_HOST") or "127.0.0.1"
    port = env.get("YTDLP_PORT") or "8091"
    ffmpeg = env.get("YTDLP_FFMPEG_LOCATION") or env.get("FFMPEG_PATH") or env.get("FFMPEG_HOME") or "PATH"

    print(f"[START] yt-dlp service: http://{host}:{port}", flush=True)
    print(f"[CONFIG] ffmpeg: {ffmpeg}", flush=True)
    print("Press Ctrl+C to stop the service", flush=True)

    subprocess.run(
        [str(python), "-m", "uvicorn", "main:app", "--host", host, "--port", str(port)],
        cwd=SCRIPT_DIR,
        env=env,
        check=True,
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print()
        raise SystemExit(130)
    except subprocess.CalledProcessError as exc:
        raise SystemExit(exc.returncode)
