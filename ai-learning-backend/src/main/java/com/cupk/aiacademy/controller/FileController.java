package com.cupk.aiacademy.controller;

import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/files")
public class FileController {
    private static final Set<String> ALLOWED_BIZ_TYPES = new HashSet<>(Arrays.asList(
            "course-cover", "lesson-resource", "homework", "grade", "common"
    ));

    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md",
            "zip", "rar", "7z",
            "mp4", "webm", "mov", "avi", "mkv"
    ));

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.url-prefix:/uploads}")
    private String urlPrefix;

    @Value("${app.upload.max-file-size-bytes:104857600}")
    private long maxFileSizeBytes;

    @Autowired
    private AuthUtils authUtils;

    @PostMapping("/upload")
    public Result upload(@RequestParam("file") MultipartFile file,
                         @RequestParam(defaultValue = "common") String bizType,
                         @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            authUtils.requireLogin(authorization);
        } catch (Exception e) {
            return Result.error("401", "登录状态已失效");
        }

        if (file == null || file.isEmpty()) {
            return Result.error("400", "上传文件不能为空");
        }
        if (file.getSize() > maxFileSizeBytes) {
            return Result.error("400", "上传文件过大");
        }

        String fixedBizType = fixBizType(bizType);
        if (fixedBizType == null) {
            return Result.error("400", "文件业务类型不支持");
        }

        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = getExtension(originalName);
        if (extension.isEmpty() || !ALLOWED_EXTENSIONS.contains(extension)) {
            return Result.error("400", "文件类型不支持");
        }

        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String savedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path root = getUploadRoot();
        Path folder = root.resolve(fixedBizType).resolve(day).normalize();
        Path target = folder.resolve(savedName).normalize();
        if (!target.startsWith(root)) {
            return Result.error("400", "文件路径不合法");
        }

        try {
            Files.createDirectories(folder);
            file.transferTo(target);
        } catch (IOException e) {
            return Result.error("500", "文件保存失败");
        }

        String prefix = normalizeUrlPrefix(urlPrefix);
        String url = prefix + "/" + fixedBizType + "/" + day + "/" + savedName;
        Map<String, Object> data = new HashMap<>();
        data.put("url", url);
        data.put("fileName", savedName);
        data.put("originalName", originalName);
        data.put("contentType", file.getContentType());
        data.put("size", file.getSize());
        return Result.success(data);
    }

    @DeleteMapping
    public Result delete(@RequestParam String url,
                         @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            authUtils.requireLogin(authorization);
        } catch (Exception e) {
            return Result.error("401", "登录状态已失效");
        }

        String prefix = normalizeUrlPrefix(urlPrefix);
        if (url == null || !url.startsWith(prefix + "/")) {
            return Result.error("400", "只能删除上传目录下的文件");
        }

        String relativeUrl = URLDecoder.decode(url.substring(prefix.length() + 1), StandardCharsets.UTF_8);
        Path root = getUploadRoot();
        Path target = root.resolve(relativeUrl).normalize();
        if (!target.startsWith(root)) {
            return Result.error("400", "文件路径不合法");
        }
        if (!Files.isRegularFile(target)) {
            return Result.error("404", "文件不存在");
        }

        try {
            Files.delete(target);
            return Result.success();
        } catch (IOException e) {
            return Result.error("500", "文件删除失败");
        }
    }

    private Path getUploadRoot() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    private String fixBizType(String bizType) {
        String value = bizType == null || bizType.trim().isEmpty() ? "common" : bizType.trim();
        return ALLOWED_BIZ_TYPES.contains(value) ? value : null;
    }

    private String getExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase();
    }

    private String normalizeUrlPrefix(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "/uploads";
        }
        String prefix = value.trim();
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        while (prefix.endsWith("/") && prefix.length() > 1) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }
}
