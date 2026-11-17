package com.cupk.aiacademy.service;

import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.Lesson;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextParagraph;
import org.apache.poi.hslf.usermodel.HSLFTextRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CourseImportService {
    private static final int MAX_LESSONS = 60;
    private static final int MAX_FILES = 10;

    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final LessonMapper lessonMapper;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.url-prefix:/uploads}")
    private String urlPrefix;

    @Value("${app.upload.max-file-size-bytes:104857600}")
    private long maxFileSizeBytes;

    public CourseImportService(CourseMapper courseMapper,
                               ChapterMapper chapterMapper,
                               LessonMapper lessonMapper) {
        this.courseMapper = courseMapper;
        this.chapterMapper = chapterMapper;
        this.lessonMapper = lessonMapper;
    }

    @Transactional
    public Map<String, Object> createCourseWithOutline(Course course, List<MultipartFile> files) {
        LocalDateTime now = LocalDateTime.now();
        course.setTitle(course.getTitle().trim());
        course.setCreatedAt(now);
        course.setUpdatedAt(now);

        List<String> warnings = new ArrayList<>();
        List<String> resourceUrls = new ArrayList<>();
        List<FileGroup> fileGroups = new ArrayList<>();

        if (files != null && !files.isEmpty()) {
            if (files.size() > MAX_FILES) {
                warnings.add("最多上传 " + MAX_FILES + " 个文件，仅处理前 " + MAX_FILES + " 个");
            }
            int count = 0;
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) continue;
                if (count >= MAX_FILES) break;
                count++;
                validateUpload(file);
                StoredFile sf = storeFile(file, "lesson-resource");
                resourceUrls.add(sf.url());
                String ext = getExtension(file.getOriginalFilename());
                String type = "pdf".equals(ext) ? "pdf" : "ppt";

                String chapterTitle = fileNameToChapterTitle(file.getOriginalFilename());
                List<SlideData> slides = parseFile(file, type, sf.url());
                fileGroups.add(new FileGroup(chapterTitle, sf.url(), type, slides));
            }
        }

        // auto-fill description from file names
        if (course.getDescription() == null || course.getDescription().isBlank()) {
            course.setDescription(buildDescription(fileGroups));
        }

        courseMapper.insert(course);

        return appendGroups(course, fileGroups, warnings, resourceUrls, 1);
    }

    public record StoredCourseDocument(Path path, String filename, String resourceUrl) { }

    @Transactional
    public Map<String, Object> appendStoredDocuments(Course course, List<StoredCourseDocument> documents) {
        List<FileGroup> groups = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for (StoredCourseDocument document : documents) {
            String extension = getExtension(document.filename());
            String type = "pdf".equals(extension) ? "pdf" : "ppt";
            try (InputStream input = Files.newInputStream(document.path())) {
                List<SlideData> parsed = switch (extension) {
                    case "pdf" -> parsePdf(input);
                    case "pptx" -> parsePptx(input);
                    case "ppt" -> parsePpt(input);
                    default -> throw new IllegalArgumentException("请选择 PDF 或 PPT 课件");
                };
                if (parsed.isEmpty()) throw new IllegalArgumentException("资料中没有可组织的页面");
                groups.add(new FileGroup(fileNameToChapterTitle(document.filename()), document.resourceUrl(), type, parsed));
                urls.add(document.resourceUrl());
            } catch (IOException | InvalidFormatException error) {
                throw new IllegalArgumentException("课件解析失败，请检查文件后重试", error);
            }
        }
        int nextOrder = chapterMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Chapter>()
                .eq("course_id", course.getId())).stream().map(Chapter::getSortOrder).filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(0) + 1;
        return appendGroups(course, groups, new ArrayList<>(), urls, nextOrder);
    }

    private Map<String, Object> appendGroups(Course course, List<FileGroup> fileGroups, List<String> warnings, List<String> resourceUrls, int chapterSort) {
        LocalDateTime now = LocalDateTime.now();
        // Each source remains a separate chapter in the existing course.
        int lessonCount = 0;

        for (FileGroup fg : fileGroups) {
            Chapter ch = new Chapter();
            ch.setCourseId(course.getId());
            ch.setTitle(fg.title());
            ch.setDescription(fg.topContent());
            ch.setSortOrder(chapterSort++);
            ch.setStatus(Chapter.STATUS_NORMAL);
            ch.setCreatedAt(now);
            ch.setUpdatedAt(now);
            chapterMapper.insert(ch);

            int ls = 1;
            for (SlideData s : fg.slides()) {
                if (lessonCount >= MAX_LESSONS) {
                    warnings.add("课时已达上限 " + MAX_LESSONS);
                    break;
                }
                String lessonTitle = s.title().isBlank()
                    ? "第 " + s.index() + " 页"
                    : s.title();
                if (lessonTitle.length() > 50) lessonTitle = lessonTitle.substring(0, 50);

                String content = "来源：" + fg.title() + " 第 " + s.index() + " 页\n\n" + s.body();
                if (content.length() > 5000) content = content.substring(0, 5000);

                Lesson lesson = new Lesson();
                lesson.setChapterId(ch.getId());
                lesson.setTitle(lessonTitle);
                lesson.setResourceType(fg.type());
                lesson.setResourceUrl(fg.url());
                lesson.setContent(content);
                lesson.setDuration(0);
                lesson.setSortOrder(ls++);
                lesson.setViewCount(0);
                lesson.setStatus(1);
                lesson.setVideoUrl("");
                lesson.setVideoResourceId(null);
                lesson.setCreatedAt(now);
                lesson.setUpdatedAt(now);
                lessonMapper.insert(lesson);
                lessonCount++;
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("courseId", course.getId());
        data.put("chaptersCreated", fileGroups.size());
        data.put("lessonsCreated", lessonCount);
        data.put("resourceUrls", resourceUrls);
        data.put("warnings", warnings);
        return data;
    }

    // ===== filename → chapter title =====

    private String fileNameToChapterTitle(String originalName) {
        if (originalName == null || originalName.isBlank()) return "未命名章节";
        String name = originalName;
        // strip extension
        int dot = name.lastIndexOf('.');
        if (dot >= 0) name = name.substring(0, dot);
        // strip leading numbers / markers like "01", "1-", "1.", "第1章 "
        name = name.replaceAll("^[0-9]{1,2}[-_.、)）\\s]*", "");
        // keep "第N章 xxx" or "Chapter N" as-is but strip trailing artifacts
        name = name.trim();
        if (name.isEmpty()) name = "未命名章节";
        return name;
    }

    // ===== description =====

    private String buildDescription(List<FileGroup> fileGroups) {
        StringBuilder sb = new StringBuilder("本课程涵盖：");
        for (int i = 0; i < fileGroups.size() && i < 8; i++) {
            if (i > 0) sb.append("、");
            sb.append(fileGroups.get(i).title());
        }
        if (sb.length() > 500) return sb.substring(0, 500) + "…";
        return sb.toString();
    }

    // ===== validation & storage =====

    private void validateUpload(MultipartFile file) {
        if (file.getSize() > maxFileSizeBytes) {
            throw new IllegalArgumentException(file.getOriginalFilename() + " 文件过大");
        }
        String ext = getExtension(file.getOriginalFilename());
        if (!List.of("ppt", "pptx", "pdf").contains(ext)) {
            throw new IllegalArgumentException("仅支持 PPT/PPTX/PDF 文件，不支持: " + ext);
        }
    }

    private StoredFile storeFile(MultipartFile file, String bizType) {
        String ext = getExtension(file.getOriginalFilename());
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String base = file.getOriginalFilename();
        if (base != null) {
            int d = base.lastIndexOf('.');
            if (d >= 0) base = base.substring(0, d);
            base = base.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]", "_").replaceAll("_+", "_");
        } else {
            base = "file";
        }
        String name = base + "-" + UUID.randomUUID().toString().substring(0, 8) + "." + ext;
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path folder = root.resolve(bizType).resolve(day).normalize();
        Path target = folder.resolve(name).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("文件路径不合法");
        try {
            Files.createDirectories(folder);
            file.transferTo(target);
        } catch (IOException e) {
            throw new IllegalStateException("文件保存失败", e);
        }
        String prefix = urlPrefix;
        if (prefix == null || prefix.isBlank()) prefix = "/uploads";
        if (!prefix.startsWith("/")) prefix = "/" + prefix;
        while (prefix.endsWith("/") && prefix.length() > 1) prefix = prefix.substring(0, prefix.length() - 1);
        return new StoredFile(name, prefix + "/" + bizType + "/" + day + "/" + name);
    }

    // ===== parse =====

    private List<SlideData> parseFile(MultipartFile file, String type, String url) {
        String ext = getExtension(file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            List<SlideData> raw = switch (ext) {
                case "pptx" -> parsePptx(in);
                case "ppt" -> parsePpt(in);
                case "pdf" -> parsePdf(in);
                default -> List.of();
            };
            // enrich with resource info
            return raw.stream()
                .map(s -> new SlideData(s.index(), s.title(), s.body(), type, url))
                .toList();
        } catch (Exception e) {
            throw new IllegalArgumentException(file.getOriginalFilename() + " 解析失败: " + e.getMessage(), e);
        }
    }

    // ---- PPTX ----

    private List<SlideData> parsePptx(InputStream in) throws IOException, InvalidFormatException {
        try (XMLSlideShow ss = new XMLSlideShow(OPCPackage.open(in))) {
            List<SlideData> out = new ArrayList<>();
            int idx = 1;
            for (XSLFSlide slide : ss.getSlides()) {
                String title = extractPptxTitle(slide);
                List<String> bodies = new ArrayList<>();
                for (XSLFShape shape : slide.getShapes()) {
                    if (!(shape instanceof XSLFTextShape ts)) continue;
                    String text = clean(ts.getText());
                    if (text.isEmpty()) continue;
                    if (title != null && title.equals(text)) continue;
                    if (title == null && looksLikeTitle(text, ts)) { title = text; continue; }
                    bodies.add(text);
                }
                if (title == null || title.isBlank()) title = "";
                out.add(new SlideData(idx++, title, String.join("\n\n", bodies), null, null));
            }
            return out;
        }
    }

    private String extractPptxTitle(XSLFSlide slide) {
        String t = clean(slide.getTitle());
        if (!t.isBlank()) return t;
        String best = "";
        double bestFont = 0;
        for (XSLFShape shape : slide.getShapes()) {
            if (!(shape instanceof XSLFTextShape ts)) continue;
            String text = clean(ts.getText());
            if (text.isBlank()) continue;
            if (!ts.getTextParagraphs().isEmpty()) {
                for (XSLFTextRun run : ts.getTextParagraphs().get(0).getTextRuns()) {
                    if (run.getFontSize() > bestFont && text.length() <= 100) {
                        bestFont = run.getFontSize();
                        best = text;
                        break;
                    }
                }
            }
        }
        if (!best.isBlank()) return best;
        for (XSLFShape shape : slide.getShapes()) {
            if (!(shape instanceof XSLFTextShape ts)) continue;
            String text = clean(ts.getText());
            if (!text.isBlank()) {
                int nl = text.indexOf('\n');
                if (nl > 0) text = text.substring(0, nl);
                if (text.length() <= 100) return text;
            }
        }
        return "";
    }

    private boolean looksLikeTitle(String text, XSLFTextShape ts) {
        if (text.length() > 80) return false;
        try {
            for (XSLFTextRun run : ts.getTextParagraphs().get(0).getTextRuns()) {
                if (run.getFontSize() >= 18) return true;
            }
        } catch (Exception ignored) {}
        if (ts.getAnchor() != null && ts.getAnchor().getY() < 100 && text.length() <= 60) return true;
        return false;
    }

    // ---- PPT ----

    private List<SlideData> parsePpt(InputStream in) throws IOException {
        try (HSLFSlideShow ss = new HSLFSlideShow(in)) {
            List<SlideData> out = new ArrayList<>();
            int idx = 1;
            for (HSLFSlide slide : ss.getSlides()) {
                String title = clean(slide.getTitle());
                List<String> bodies = new ArrayList<>();
                for (HSLFShape shape : slide.getShapes()) {
                    String text = clean(extractHslfText(shape));
                    if (text.isEmpty()) continue;
                    if (!title.isBlank() && title.equals(text)) continue;
                    if (title.isBlank() && text.length() <= 60) { title = text; continue; }
                    bodies.add(text);
                }
                if (title.isBlank()) title = "";
                out.add(new SlideData(idx++, title, String.join("\n\n", bodies), null, null));
            }
            return out;
        }
    }

    // ---- PDF ----

    private List<SlideData> parsePdf(InputStream in) throws IOException {
        try (PDDocument doc = Loader.loadPDF(in.readAllBytes())) {
            List<SlideData> out = new ArrayList<>();
            PDFTextStripper s = new PDFTextStripper();
            s.setSortByPosition(true);
            int total = doc.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                s.setStartPage(i + 1);
                s.setEndPage(i + 1);
                String text = s.getText(doc).trim();
                if (text.isEmpty()) {
                    out.add(new SlideData(i + 1, "", "", null, null));
                    continue;
                }
                String[] lines = text.split("\\n");
                String title = "";
                int bodyStart = 0;
                for (int j = 0; j < lines.length; j++) {
                    String l = clean(lines[j]);
                    if (l.length() >= 2 && l.length() <= 80
                            && !l.matches("(?i)^(slide|page|\\d+)$")
                            && Character.isLetterOrDigit(l.codePointAt(0))) {
                        title = l;
                        bodyStart = j + 1;
                        break;
                    }
                }
                StringBuilder body = new StringBuilder();
                for (int j = bodyStart; j < lines.length; j++) {
                    String l = clean(lines[j]);
                    if (!l.isBlank()) body.append(l).append("\n");
                }
                out.add(new SlideData(i + 1, title, body.toString().trim(), null, null));
            }
            return out;
        }
    }

    // ===== helpers =====

    private String extractHslfText(HSLFShape shape) {
        if (!(shape instanceof org.apache.poi.hslf.usermodel.HSLFTextShape ts)) return "";
        StringBuilder sb = new StringBuilder();
        for (HSLFTextParagraph p : ts.getTextParagraphs()) {
            for (HSLFTextRun r : p.getTextRuns()) sb.append(r.getRawText());
            sb.append('\n');
        }
        return sb.toString();
    }

    private String clean(String text) {
        if (text == null) return "";
        return text.replace("\013", "\n").replaceAll("\\s+", " ").trim();
    }

    private String getExtension(String fileName) {
        if (fileName == null) return "";
        int i = fileName.lastIndexOf('.');
        if (i < 0 || i == fileName.length() - 1) return "";
        return fileName.substring(i + 1).toLowerCase();
    }

    // ===== records =====

    private record StoredFile(String name, String url) {}
    private record SlideData(int index, String title, String body, String resourceType, String resourceUrl) {}
    private record FileGroup(String title, String url, String type, List<SlideData> slides) {
        String topContent() {
            for (SlideData s : slides) {
                if (!s.body().isBlank()) {
                    String b = s.body();
                    return b.length() > 300 ? b.substring(0, 300) + "…" : b;
                }
            }
            return "由文件 " + title + " 导入";
        }
    }
}
