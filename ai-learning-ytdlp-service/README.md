# ai-learning-ytdlp-service

本服务是在线学习平台内部使用的 yt-dlp 下载服务。它只应部署在后端可访问的内网或本机地址，默认监听 `127.0.0.1:8091`。

## 一键补全依赖

通用方式：

```bash
python setup_service.py
```

该脚本会自动：

- 创建 `.venv` 虚拟环境。
- 安装 `requirements.txt` 中的 Python 依赖，包括 `yt-dlp`。
- 查找本机已有的 `ffmpeg`。
- 如果未找到 `ffmpeg`，Windows 下会自动下载到 `tools/ffmpeg`；macOS/Linux 请通过系统包管理器安装，或设置 `YTDLP_FFMPEG_DOWNLOAD_URL` 指向包含 `ffmpeg` 的 zip/tar 包。
- 将解析出的 `YTDLP_FFMPEG_LOCATION` 写入本地 `.service_env`，启动脚本会自动读取。

## 启动

通用方式：

```bash
python start_service.py
```

`start_service.py` 会先调用 `setup_service.py` 补全依赖，再启动服务。

或手动启动：

```bash
uvicorn main:app --host 127.0.0.1 --port 8091
```

## 环境变量

```text
YTDLP_MEDIA_ROOT=media
YTDLP_URL_PREFIX=/media
YTDLP_HOST=127.0.0.1
YTDLP_PORT=8091
YTDLP_FFMPEG_LOCATION=D:\tools\ffmpeg\bin
YTDLP_MAX_FILE_SIZE_MB=1024
YTDLP_SOCKET_TIMEOUT_SECONDS=30
YTDLP_ALLOW_PRIVATE_HOSTS=false
```

`YTDLP_FFMPEG_LOCATION` 可以指向 `ffmpeg.exe`，也可以指向包含 `ffmpeg.exe` 的目录。未设置时会使用系统 `PATH`；兼容读取 `FFMPEG_PATH` 和 `FFMPEG_HOME`。

如需指定 ffmpeg 下载源，可设置：

```powershell
$env:YTDLP_FFMPEG_DOWNLOAD_URL="https://example.com/ffmpeg.zip"
python setup_service.py --force-ffmpeg-download
```

其他可选参数：

```bash
python setup_service.py --force-python-deps
python setup_service.py --skip-ffmpeg-download
```

## 接口

健康检查：

```http
GET /api/ytdlp/health
```

下载视频：

```http
POST /api/ytdlp/download
Content-Type: application/json

{
  "url": "https://example.com/video",
  "outputDir": "videos/2026/07/06",
  "resourceId": 1
}
```

成功响应：

```json
{
  "success": true,
  "resourceId": 1,
  "relativePath": "videos/2026/07/06/title.mp4",
  "playUrl": "/media/videos/2026/07/06/title.mp4",
  "title": "视频标题",
  "duration": 3600,
  "thumbnail": null,
  "fileSize": 123456
}
```

## 安全边界

- 仅允许 `http` 和 `https` URL。
- 默认拒绝 localhost、内网地址和链路本地地址。
- 输出目录会限制在 `YTDLP_MEDIA_ROOT` 内，防止路径穿越。
- 使用 yt-dlp Python API，不拼接 shell 命令。
- 本服务只用于下载教师拥有版权或已获得授权的视频资源。
