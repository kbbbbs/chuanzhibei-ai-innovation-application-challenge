# TODO: 视频 URL 到知识库链路

当前 `ai-learning-ytdlp-service` 只负责下载视频和封面，并返回播放地址、标题、时长、文件大小等元数据。

还需要补齐的链路：

1. 下载字幕或生成转写文本
   - 优先使用 yt-dlp 下载原站字幕/自动字幕。
   - 没有字幕时，再考虑接入 Whisper 或其他语音转文字服务。
   - 输出稳定的 transcript 文本或 Markdown。

2. 返回知识处理所需字段
   - 在下载响应中增加字幕/转写文件路径，或直接返回转写文本。
   - 保持现有播放字段兼容，避免影响前端视频播放。

3. 后端下载完成后触发知识处理
   - `VideoResourceService.markReady()` 之后触发异步任务。
   - 读取转写文本，生成知识点 Markdown。
   - 写入 `video_resource.summary_md`。

4. 保存到 WeKnora 知识库
   - URL 上传时需要确定目标 `knowledgeBaseId`。
   - 后端调用 `WeKnoraService.uploadKnowledgeText()` 上传知识点 Markdown。
   - 记录上传状态和错误信息，便于前端展示。

5. 验收点
   - 教师上传视频 URL 后，视频可以正常播放。
   - 课时详情能看到自动生成的知识点摘要。
   - WeKnora 对应知识库能检索到该视频的知识点内容。
   - WeKnora 未启动或上传失败时，不影响视频下载和播放。
