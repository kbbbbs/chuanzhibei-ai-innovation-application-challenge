from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


def _env_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _env_int(name: str, default: int) -> int:
    value = os.getenv(name)
    if value is None or value.strip() == "":
        return default
    return int(value)


@dataclass(frozen=True)
class Settings:
    media_root: Path
    url_prefix: str
    host: str
    port: int
    ffmpeg_location: str | None
    max_file_size_mb: int
    socket_timeout_seconds: int
    allow_private_hosts: bool

    @property
    def max_file_size_bytes(self) -> int:
        return self.max_file_size_mb * 1024 * 1024


def load_settings() -> Settings:
    media_root = Path(os.getenv("YTDLP_MEDIA_ROOT", "media")).resolve()
    url_prefix = os.getenv("YTDLP_URL_PREFIX", "/media").strip() or "/media"
    if not url_prefix.startswith("/"):
        url_prefix = f"/{url_prefix}"
    ffmpeg_location = (
        os.getenv("YTDLP_FFMPEG_LOCATION")
        or os.getenv("FFMPEG_PATH")
        or os.getenv("FFMPEG_HOME")
    )
    return Settings(
        media_root=media_root,
        url_prefix=url_prefix.rstrip("/"),
        host=os.getenv("YTDLP_HOST", "127.0.0.1"),
        port=_env_int("YTDLP_PORT", 8091),
        ffmpeg_location=ffmpeg_location.strip() if ffmpeg_location and ffmpeg_location.strip() else None,
        max_file_size_mb=_env_int("YTDLP_MAX_FILE_SIZE_MB", 1024),
        socket_timeout_seconds=_env_int("YTDLP_SOCKET_TIMEOUT_SECONDS", 30),
        allow_private_hosts=_env_bool("YTDLP_ALLOW_PRIVATE_HOSTS", False),
    )


settings = load_settings()
