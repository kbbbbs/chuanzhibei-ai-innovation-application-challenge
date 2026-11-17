package com.cupk.aiacademy.videoresource;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.ForbiddenException;
import com.cupk.aiacademy.dto.LessonPlaybackDto;
import com.cupk.aiacademy.dto.VideoResourceVO;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.mapper.VideoResourceMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.pojo.VideoResource;
import com.cupk.aiacademy.service.PermissionService;
import com.cupk.aiacademy.videoresource.client.YtDlpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Service
public class VideoResourceService {
    private final VideoResourceMapper videoResourceMapper;
    private final LessonMapper lessonMapper;
    private final ChapterMapper chapterMapper;
    private final CourseMapper courseMapper;
    private final PermissionService permissionService;
    private final YtDlpClient ytDlpClient;
    private final boolean allowPrivateHosts;

    public VideoResourceService(VideoResourceMapper videoResourceMapper,
                                LessonMapper lessonMapper,
                                ChapterMapper chapterMapper,
                                CourseMapper courseMapper,
                                PermissionService permissionService,
                                YtDlpClient ytDlpClient,
                                @Value("${ytdlp.allow-private-hosts:false}") boolean allowPrivateHosts) {
        this.videoResourceMapper = videoResourceMapper;
        this.lessonMapper = lessonMapper;
        this.chapterMapper = chapterMapper;
        this.courseMapper = courseMapper;
        this.permissionService = permissionService;
        this.ytDlpClient = ytDlpClient;
        this.allowPrivateHosts = allowPrivateHosts;
    }

    @Transactional
    public VideoResourceVO submitUrl(Integer lessonId, String url) {
        AuthUtils.LoginUser user = permissionService.currentUser();
        Lesson lesson = getLesson(lessonId);
        Course course = getCourseByLesson(lesson);
        permissionService.requireCourseTeacher(course);

        String normalizedUrl = normalizeAndValidateUrl(url);
        String hash = sha256(normalizedUrl);
        VideoResource resource = videoResourceMapper.selectOne(new QueryWrapper<VideoResource>()
                .eq("source_url_hash", hash)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (resource == null) {
            resource = new VideoResource();
            resource.setSourceUrl(normalizedUrl);
            resource.setSourceUrlHash(hash);
            resource.setStatus(VideoResourceStatus.PENDING);
            resource.setDuration(0);
            resource.setFileSize(0L);
            resource.setCreatedBy(user.getUserId());
            resource.setCreatedAt(now);
            resource.setUpdatedAt(now);
            videoResourceMapper.insert(resource);
        } else if (VideoResourceStatus.FAILED.equals(resource.getStatus())) {
            resource.setStatus(VideoResourceStatus.PENDING);
            resource.setErrorMessage("");
            resource.setUpdatedAt(now);
            videoResourceMapper.updateById(resource);
        }

        Lesson update = new Lesson();
        update.setId(lesson.getId());
        update.setResourceType("video_url");
        update.setResourceUrl(normalizedUrl);
        update.setVideoResourceId(resource.getId());
        if (VideoResourceStatus.READY.equals(resource.getStatus())) {
            update.setVideoUrl(resource.getPlayUrl());
            update.setDuration(resource.getDuration());
        }
        update.setUpdatedAt(now);
        lessonMapper.updateById(update);

        if (!VideoResourceStatus.READY.equals(resource.getStatus())
                && !VideoResourceStatus.DOWNLOADING.equals(resource.getStatus())) {
            scheduleDownload(resource.getId());
        }
        return VideoResourceVO.from(videoResourceMapper.selectById(resource.getId()));
    }

    @Transactional(readOnly = true)
    public VideoResourceVO getResource(Long id) {
        VideoResource resource = getVideoResource(id);
        requireResourceAccess(resource);
        return VideoResourceVO.from(resource);
    }

    @Transactional
    public VideoResourceVO retry(Long id) {
        VideoResource resource = getVideoResource(id);
        requireResourceManagePermission(resource);
        resource.setStatus(VideoResourceStatus.PENDING);
        resource.setErrorMessage("");
        resource.setUpdatedAt(LocalDateTime.now());
        videoResourceMapper.updateById(resource);
        scheduleDownload(id);
        return VideoResourceVO.from(resource);
    }

    @Transactional(readOnly = true)
    public LessonPlaybackDto getLessonPlayback(Integer lessonId) {
        Lesson lesson = getLesson(lessonId);
        requireLessonAccess(lesson);

        LessonPlaybackDto dto = new LessonPlaybackDto();
        dto.setLessonId(lesson.getId());
        dto.setTitle(lesson.getTitle());
        dto.setDuration(lesson.getDuration());
        dto.setPlayUrl(lesson.getVideoUrl());
        dto.setStatus(lesson.getVideoUrl() == null || lesson.getVideoUrl().isBlank()
                ? VideoResourceStatus.PENDING
                : VideoResourceStatus.READY);

        if (lesson.getVideoResourceId() != null) {
            VideoResource resource = videoResourceMapper.selectById(lesson.getVideoResourceId());
            if (resource != null) {
                dto.setVideoResourceId(resource.getId());
                dto.setStatus(resource.getStatus());
                dto.setPlayUrl(resource.getPlayUrl());
                dto.setDuration(resource.getDuration());
                dto.setSummaryMd(resource.getSummaryMd());
                dto.setErrorMessage(resource.getErrorMessage());
            }
        }
        return dto;
    }

    public void downloadResource(Long id) {
        VideoResource resource = videoResourceMapper.selectById(id);
        if (resource == null) {
            return;
        }
        markDownloading(id);
        try {
            YtDlpClient.DownloadResponse response = ytDlpClient.download(
                    resource.getSourceUrl(),
                    buildOutputDir(),
                    resource.getId());
            markReady(id, response);
        } catch (Exception e) {
            markFailed(id, e.getMessage());
        }
    }

    private void scheduleDownload(Long id) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(() -> downloadResource(id));
                }
            });
            return;
        }
        CompletableFuture.runAsync(() -> downloadResource(id));
    }

    private void markDownloading(Long id) {
        VideoResource update = new VideoResource();
        update.setId(id);
        update.setStatus(VideoResourceStatus.DOWNLOADING);
        update.setErrorMessage("");
        update.setUpdatedAt(LocalDateTime.now());
        videoResourceMapper.updateById(update);
    }

    private void markReady(Long id, YtDlpClient.DownloadResponse response) {
        VideoResource update = new VideoResource();
        update.setId(id);
        update.setStatus(VideoResourceStatus.READY);
        update.setStoragePath(response.getRelativePath());
        update.setPlayUrl(response.getPlayUrl());
        update.setTitle(response.getTitle());
        update.setDuration(response.getDuration() == null ? 0 : response.getDuration());
        update.setCoverUrl(response.getThumbnail());
        update.setFileSize(response.getFileSize() == null ? 0L : response.getFileSize());
        update.setErrorMessage("");
        update.setUpdatedAt(LocalDateTime.now());
        videoResourceMapper.updateById(update);

        VideoResource ready = videoResourceMapper.selectById(id);
        List<Lesson> lessons = lessonMapper.selectList(new QueryWrapper<Lesson>().eq("video_resource_id", id));
        for (Lesson lesson : lessons) {
            Lesson lessonUpdate = new Lesson();
            lessonUpdate.setId(lesson.getId());
            lessonUpdate.setVideoUrl(ready.getPlayUrl());
            lessonUpdate.setDuration(ready.getDuration());
            lessonUpdate.setUpdatedAt(LocalDateTime.now());
            lessonMapper.updateById(lessonUpdate);
        }
    }

    private void markFailed(Long id, String message) {
        VideoResource update = new VideoResource();
        update.setId(id);
        update.setStatus(VideoResourceStatus.FAILED);
        update.setErrorMessage(message == null || message.isBlank() ? "视频下载失败" : message);
        update.setUpdatedAt(LocalDateTime.now());
        videoResourceMapper.updateById(update);
    }

    private Lesson getLesson(Integer lessonId) {
        if (lessonId == null || lessonId <= 0) {
            throw new BusinessException("课时不能为空");
        }
        Lesson lesson = lessonMapper.selectById(lessonId);
        if (lesson == null) {
            throw new BusinessException("课时不存在");
        }
        return lesson;
    }

    private VideoResource getVideoResource(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException("视频资源不能为空");
        }
        VideoResource resource = videoResourceMapper.selectById(id);
        if (resource == null) {
            throw new BusinessException("视频资源不存在");
        }
        return resource;
    }

    private Course getCourseByLesson(Lesson lesson) {
        Chapter chapter = chapterMapper.selectById(lesson.getChapterId());
        if (chapter == null) {
            throw new BusinessException("章节不存在");
        }
        Course course = courseMapper.selectById(chapter.getCourseId());
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        return course;
    }

    private void requireLessonAccess(Lesson lesson) {
        if (canAccessLesson(lesson)) {
            return;
        }
        throw new ForbiddenException("无权访问该课时资源");
    }

    private boolean canAccessLesson(Lesson lesson) {
        AuthUtils.LoginUser user = permissionService.currentUser();
        Course course = getCourseByLesson(lesson);
        if (permissionService.isTeacher(user) && Objects.equals(course.getTeacherId(), user.getUserId())) {
            return true;
        }
        return permissionService.isStudent(user) && permissionService.studentCourseIds(user.getUserId()).contains(course.getId());
    }

    private void requireResourceAccess(VideoResource resource) {
        List<Lesson> lessons = lessonMapper.selectList(new QueryWrapper<Lesson>().eq("video_resource_id", resource.getId()));
        if (lessons.isEmpty()) {
            requireResourceManagePermission(resource);
            return;
        }
        for (Lesson lesson : lessons) {
            if (canAccessLesson(lesson)) {
                return;
            }
        }
        throw new ForbiddenException("无权访问该视频资源");
    }

    private void requireResourceManagePermission(VideoResource resource) {
        AuthUtils.LoginUser user = permissionService.currentUser();
        if (permissionService.isAdmin(user) || Objects.equals(resource.getCreatedBy(), user.getUserId())) {
            return;
        }
        List<Lesson> lessons = lessonMapper.selectList(new QueryWrapper<Lesson>().eq("video_resource_id", resource.getId()));
        for (Lesson lesson : lessons) {
            Course course = getCourseByLesson(lesson);
            if (permissionService.isTeacher(user) && Objects.equals(course.getTeacherId(), user.getUserId())) {
                return;
            }
        }
        throw new ForbiddenException("无权维护该视频资源");
    }

    private String normalizeAndValidateUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("视频 URL 不能为空");
        }
        String url = value.trim();
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new BusinessException("视频 URL 格式不正确");
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new BusinessException("视频 URL 仅支持 http 和 https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BusinessException("视频 URL 缺少域名");
        }
        if (!allowPrivateHosts) {
            rejectPrivateHost(uri.getHost());
        }
        return url;
    }

    private void rejectPrivateHost(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw new BusinessException("不允许下载内网或本机地址的视频");
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("无法解析视频 URL 域名");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private String buildOutputDir() {
        LocalDate today = LocalDate.now();
        return "videos/%04d/%02d/%02d".formatted(today.getYear(), today.getMonthValue(), today.getDayOfMonth());
    }
}
