from __future__ import annotations

import ipaddress
import socket
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.parse import quote, urlparse

import yt_dlp

from config import Settings


class DownloadError(RuntimeError):
    pass


@dataclass(frozen=True)
class DownloadResult:
    relative_path: str
    play_url: str
    title: str | None
    duration: int | None
    thumbnail: str | None
    file_size: int


def validate_source_url(url: str, allow_private_hosts: bool) -> None:
    parsed = urlparse(url)
    if parsed.scheme not in {"http", "https"}:
        raise DownloadError("Only http and https URLs are allowed")
    if not parsed.hostname:
        raise DownloadError("URL host is required")
    if allow_private_hosts:
        return

    host = parsed.hostname.strip().lower()
    if host in {"localhost", "127.0.0.1", "::1"}:
        raise DownloadError("Localhost URLs are not allowed")

    try:
        addresses = socket.getaddrinfo(host, None)
    except socket.gaierror as exc:
        raise DownloadError(f"Cannot resolve URL host: {host}") from exc

    for address in addresses:
        ip = ipaddress.ip_address(address[4][0])
        if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_multicast:
            raise DownloadError("Private, loopback, link-local and multicast hosts are not allowed")


def resolve_output_dir(media_root: Path, output_dir: str) -> Path:
    normalized = output_dir.replace("\\", "/").strip("/")
    target = (media_root / normalized).resolve()
    media_root_resolved = media_root.resolve()
    if target != media_root_resolved and media_root_resolved not in target.parents:
        raise DownloadError("Output directory must stay inside media root")
    target.mkdir(parents=True, exist_ok=True)
    return target


def _relative_url(url_prefix: str, media_root: Path, file_path: Path) -> tuple[str, str]:
    relative = file_path.resolve().relative_to(media_root.resolve()).as_posix()
    play_url = f"{url_prefix}/{quote(relative, safe='/')}"
    return relative, play_url


def _pick_downloaded_file(info: dict[str, Any], output_dir: Path) -> Path:
    requested = info.get("requested_downloads") or []
    for item in requested:
        candidate = item.get("filepath") or item.get("_filename")
        if candidate and Path(candidate).exists():
            return Path(candidate)

    candidate = info.get("filepath") or info.get("_filename")
    if candidate and Path(candidate).exists():
        return Path(candidate)

    files = sorted(
        (path for path in output_dir.iterdir() if path.is_file() and path.suffix.lower() in {".mp4", ".mkv", ".webm", ".mov"}),
        key=lambda path: path.stat().st_mtime,
        reverse=True,
    )
    if not files:
        raise DownloadError("Download completed but no media file was found")
    return files[0]


def _download_thumbnail(info: dict[str, Any], settings: Settings, downloaded_file: Path) -> str | None:
    thumbnail = info.get("thumbnail")
    if not thumbnail:
        return None
    for image in downloaded_file.parent.glob(f"{downloaded_file.stem}.*"):
        if image.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"}:
            _, image_url = _relative_url(settings.url_prefix, settings.media_root, image)
            return image_url
    return thumbnail


def _duration_seconds(value: Any) -> int | None:
    if value is None:
        return None
    try:
        return int(round(float(value)))
    except (TypeError, ValueError):
        return None


def download_video(url: str, output_dir: str, settings: Settings) -> DownloadResult:
    validate_source_url(url, settings.allow_private_hosts)
    target_dir = resolve_output_dir(settings.media_root, output_dir)

    ydl_opts: dict[str, Any] = {
        "outtmpl": str(target_dir / "%(title).180B-%(id)s.%(ext)s"),
        "format": "bv*+ba/b",
        "merge_output_format": "mp4",
        "noplaylist": True,
        "restrictfilenames": True,
        "socket_timeout": settings.socket_timeout_seconds,
        "max_filesize": settings.max_file_size_bytes,
        "writethumbnail": True,
        "quiet": True,
        "no_warnings": True,
    }
    if settings.ffmpeg_location:
        ydl_opts["ffmpeg_location"] = settings.ffmpeg_location

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)
    except Exception as exc:  # yt-dlp raises several library-specific errors.
        raise DownloadError(str(exc)) from exc

    if not isinstance(info, dict):
        raise DownloadError("yt-dlp returned an unexpected result")

    downloaded_file = _pick_downloaded_file(info, target_dir)
    relative_path, play_url = _relative_url(settings.url_prefix, settings.media_root, downloaded_file)

    return DownloadResult(
        relative_path=relative_path,
        play_url=play_url,
        title=info.get("title"),
        duration=_duration_seconds(info.get("duration")),
        thumbnail=_download_thumbnail(info, settings, downloaded_file),
        file_size=downloaded_file.stat().st_size,
    )
