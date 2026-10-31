from __future__ import annotations

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from config import settings
from downloader import DownloadError, download_video


app = FastAPI(title="AI Learning yt-dlp Service", version="0.1.0")
print(f"yt-dlp media root: {settings.media_root}", flush=True)
print(f"yt-dlp ffmpeg location: {settings.ffmpeg_location or 'PATH'}", flush=True)


class HealthResponse(BaseModel):
    success: bool = True
    service: str = "ai-learning-ytdlp-service"
    mediaRoot: str


class DownloadRequest(BaseModel):
    url: str = Field(..., min_length=1, max_length=4096)
    outputDir: str = Field(default="videos", max_length=255)
    resourceId: int | None = Field(default=None, ge=1)


class DownloadResponse(BaseModel):
    success: bool
    resourceId: int | None = None
    relativePath: str
    playUrl: str
    title: str | None = None
    duration: int | None = None
    thumbnail: str | None = None
    fileSize: int


@app.get("/api/ytdlp/health", response_model=HealthResponse)
def health() -> HealthResponse:
    settings.media_root.mkdir(parents=True, exist_ok=True)
    return HealthResponse(mediaRoot=str(settings.media_root))


@app.post("/api/ytdlp/download", response_model=DownloadResponse)
def download(request: DownloadRequest) -> DownloadResponse:
    try:
        result = download_video(request.url, request.outputDir, settings)
    except DownloadError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return DownloadResponse(
        success=True,
        resourceId=request.resourceId,
        relativePath=result.relative_path,
        playUrl=result.play_url,
        title=result.title,
        duration=result.duration,
        thumbnail=result.thumbnail,
        fileSize=result.file_size,
    )
