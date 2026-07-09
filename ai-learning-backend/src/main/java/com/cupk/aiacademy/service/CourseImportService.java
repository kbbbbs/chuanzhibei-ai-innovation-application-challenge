package com.cupk.aiacademy.service;

import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.Lesson;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextParagraph;
import org.apache.poi.hslf.usermodel.HSLFTextRun;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.sl.usermodel.Placeholder;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
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
import java.util.regex.Pattern;

@Service
public class CourseImportService {
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("(?i).*(第\\s*[一二三四五六七八九十百千万0-9]+\\s*章|chapter\\s*\\d+|章节).*?");
    private static final int FALLBACK_SLIDES_PER_CHAPTER = 5;
    private static final int MAX_LESSONS = 50;

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
    public Map<String, Object> createCourseWithOutline(Course course, MultipartFile file) {
        stampCourseForCreate(course);
        courseMapper.insert(course);

        int chaptersCreated = 0;
        int lessonsCreated = 0;
        String resourceUrl = null;
        List<String> warnings = new ArrayList<>();

        if (file != null && !file.isEmpty()) {
            validateUpload(file);
            StoredFile storedFile = storeFile(file, "lesson-resource");
            resourceUrl = storedFile.url();
            List<SlideOutline> slides = parseSlides(file);
            if (slides.isEmpty()) {
                warnings.add("PPT 未提取到可用标题，未生成章节和课时");
            } else {
                GenerationResult result = generateOutline(course.getId(), resourceUrl, slides, warnings);
                chaptersCreated = result.chaptersCreated();
                lessonsCreated = result.lessonsCreated();
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("courseId", course.getId());
        data.put("chaptersCreated", chaptersCreated);
        data.put("lessonsCreated", lessonsCreated);
        data.put("resourceUrl", resourceUrl);
        data.put("warnings", warnings);
        return data;
    }

    private void validateUpload(MultipartFile file) {
        if (file.getSize() > maxFileSizeBytes) {
            throw new IllegalArgumentException("上传文件过大");
        }
        String extension = getExtension(file.getOriginalFilename());
        if (!"ppt".equals(extension) && !"pptx".equals(extension)) {
            throw new IllegalArgumentException("仅支持上传 PPT 或 PPTX 文件");
        }
    }

    private StoredFile storeFile(MultipartFile file, String bizType) {
        String extension = getExtension(file.getOriginalFilename());
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String savedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path folder = root.resolve(bizType).resolve(day).normalize();
        Path target = folder.resolve(savedName).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("文件路径不合法");
        }
        try {
            Files.createDirectories(folder);
            file.transferTo(target);
        } catch (IOException e) {
            throw new IllegalStateException("文件保存失败", e);
        }
        String url = normalizeUrlPrefix(urlPrefix) + "/" + bizType + "/" + day + "/" + savedName;
        return new StoredFile(savedName, url);
    }

    private List<SlideOutline> parseSlides(MultipartFile file) {
        String extension = getExtension(file.getOriginalFilename());
        try (InputStream inputStream = file.getInputStream()) {
            if ("pptx".equals(extension)) {
                return parsePptx(inputStream);
            }
            return parsePpt(inputStream);
        } catch (IOException | InvalidFormatException e) {
            throw new IllegalArgumentException("PPT 解析失败");
        }
    }

    private List<SlideOutline> parsePptx(InputStream inputStream) throws IOException, InvalidFormatException {
        try (XMLSlideShow slideShow = new XMLSlideShow(OPCPackage.open(inputStream))) {
            List<SlideOutline> slides = new ArrayList<>();
            int index = 1;
            for (XSLFSlide slide : slideShow.getSlides()) {
                String title = cleanText(slide.getTitle());
                List<String> body = new ArrayList<>();
                for (XSLFShape shape : slide.getShapes()) {
                    if (!(shape instanceof XSLFTextShape textShape)) {
                        continue;
                    }
                    String text = cleanText(textShape.getText());
                    if (text.isEmpty()) {
                        continue;
                    }
                    if (title.isEmpty() && isTitlePlaceholder(textShape)) {
                        title = text;
                        continue;
                    }
                    if (!text.equals(title)) {
                        body.add(text);
                    }
                }
                slides.add(new SlideOutline(index++, title, String.join("\n\n", body)));
            }
            return slides;
        }
    }

    private List<SlideOutline> parsePpt(InputStream inputStream) throws IOException {
        try (HSLFSlideShow slideShow = new HSLFSlideShow(inputStream)) {
            List<SlideOutline> slides = new ArrayList<>();
            int index = 1;
            for (HSLFSlide slide : slideShow.getSlides()) {
                String title = cleanText(slide.getTitle());
                List<String> body = new ArrayList<>();
                for (HSLFShape shape : slide.getShapes()) {
                    String text = cleanText(extractHslfText(shape));
                    if (text.isEmpty()) {
                        continue;
                    }
                    if (title.isEmpty() && isTitlePlaceholder(shape, text)) {
                        title = text;
                        continue;
                    }
                    if (!text.equals(title)) {
                        body.add(text);
                    }
                }
                slides.add(new SlideOutline(index++, title, String.join("\n\n", body)));
            }
            return slides;
        }
    }

    private GenerationResult generateOutline(Integer courseId,
                                             String resourceUrl,
                                             List<SlideOutline> slides,
                                             List<String> warnings) {
        List<ChapterPlan> chapterPlans = buildChapterPlans(slides, warnings);
        LocalDateTime now = LocalDateTime.now();
        int lessonsCreated = 0;
        int chapterSort = 1;

        for (ChapterPlan plan : chapterPlans) {
            if (plan.lessons().isEmpty()) {
                warnings.add(plan.title() + " 未识别到课时，已跳过");
                continue;
            }
            Chapter chapter = new Chapter();
            chapter.setCourseId(courseId);
            chapter.setTitle(plan.title());
            chapter.setDescription(plan.description());
            chapter.setSortOrder(chapterSort++);
            chapter.setStatus(Chapter.STATUS_NORMAL);
            chapter.setCreatedAt(now);
            chapter.setUpdatedAt(now);
            chapterMapper.insert(chapter);

            int lessonSort = 1;
            for (LessonPlan lessonPlan : plan.lessons()) {
                if (lessonsCreated >= MAX_LESSONS) {
                    warnings.add("课时数量超过限制，最多生成 50 个课时");
                    return new GenerationResult(chapterSort - 1, lessonsCreated);
                }
                Lesson lesson = new Lesson();
                lesson.setChapterId(chapter.getId());
                lesson.setTitle(lessonPlan.title());
                lesson.setResourceType("ppt");
                lesson.setResourceUrl(resourceUrl);
                lesson.setContent(lessonPlan.content());
                lesson.setDuration(0);
                lesson.setSortOrder(lessonSort++);
                lesson.setViewCount(0);
                lesson.setStatus(1);
                lesson.setVideoUrl("");
                lesson.setVideoResourceId(null);
                lesson.setCreatedAt(now);
                lesson.setUpdatedAt(now);
                lessonMapper.insert(lesson);
                lessonsCreated++;
            }
        }
        return new GenerationResult(chapterSort - 1, lessonsCreated);
    }

    private List<ChapterPlan> buildChapterPlans(List<SlideOutline> slides, List<String> warnings) {
        List<ChapterPlan> plans = new ArrayList<>();
        ChapterPlan current = null;
        int fallbackChapterIndex = 1;
        int untitledCount = 0;

        for (int i = 0; i < slides.size(); i++) {
            SlideOutline slide = slides.get(i);
            String title = slide.title();
            if (title.isBlank()) {
                untitledCount++;
                title = "第" + slide.index() + "页";
            }
            boolean chapterSlide = isChapterTitle(title);
            if (chapterSlide) {
                current = new ChapterPlan(title, slide.body(), new ArrayList<>());
                plans.add(current);
                continue;
            }
            if (current == null) {
                current = new ChapterPlan("第" + fallbackChapterIndex++ + "章", "根据 PPT 自动生成", new ArrayList<>());
                plans.add(current);
            }
            current.lessons().add(new LessonPlan(title, buildLessonContent(slide)));
            if ((i + 1) % FALLBACK_SLIDES_PER_CHAPTER == 0) {
                current = null;
            }
        }

        if (untitledCount > 0) {
            warnings.add("有 " + untitledCount + " 页未识别到标题，已按页码命名");
        }
        return plans;
    }

    private String buildLessonContent(SlideOutline slide) {
        StringBuilder content = new StringBuilder();
        content.append("来源页码：第").append(slide.index()).append("页");
        if (!slide.body().isBlank()) {
            content.append("\n\n").append(slide.body());
        }
        return content.toString();
    }

    private boolean isChapterTitle(String title) {
        return CHAPTER_PATTERN.matcher(title).matches();
    }

    private boolean isTitlePlaceholder(XSLFTextShape textShape) {
        Placeholder placeholder = textShape.getTextType();
        return placeholder == Placeholder.TITLE || placeholder == Placeholder.CENTERED_TITLE;
    }

    private boolean isTitlePlaceholder(HSLFShape shape, String text) {
        return shape instanceof org.apache.poi.hslf.usermodel.HSLFTextShape && text.length() <= 40;
    }

    private String extractHslfText(HSLFShape shape) {
        if (!(shape instanceof org.apache.poi.hslf.usermodel.HSLFTextShape textShape)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (HSLFTextParagraph paragraph : textShape.getTextParagraphs()) {
            for (HSLFTextRun run : paragraph.getTextRuns()) {
                builder.append(run.getRawText());
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private void stampCourseForCreate(Course course) {
        course.setTitle(course.getTitle().trim());
        LocalDateTime now = LocalDateTime.now();
        course.setCreatedAt(now);
        course.setUpdatedAt(now);
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("", "\n").replaceAll("\\s+", " ").trim();
    }

    private String getExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
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

    private record StoredFile(String fileName, String url) {}

    private record SlideOutline(int index, String title, String body) {}

    private record LessonPlan(String title, String content) {}

    private record ChapterPlan(String title, String description, List<LessonPlan> lessons) {}

    private record GenerationResult(int chaptersCreated, int lessonsCreated) {}
}
