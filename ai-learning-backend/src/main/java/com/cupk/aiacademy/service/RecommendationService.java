package com.cupk.aiacademy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.common.AuthUtils;
import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.dto.RecommendationItemDTO;
import com.cupk.aiacademy.dto.RecommendationResponseDTO;
import com.cupk.aiacademy.entity.Paper;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.CourseCategoryMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.GradeRecordMapper;
import com.cupk.aiacademy.mapper.LearningProgressMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.mapper.PaperMapper;
import com.cupk.aiacademy.mapper.QuestionMapper;
import com.cupk.aiacademy.mapper.TeachingClassMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.CourseCategory;
import com.cupk.aiacademy.pojo.GradeRecord;
import com.cupk.aiacademy.pojo.LearningProgress;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.pojo.Question;
import com.cupk.aiacademy.pojo.TeachingClass;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecommendationService {
    private static final String TYPE_ALL = "all";
    private static final String TYPE_COURSE = "course";
    private static final String TYPE_LESSON = "lesson";
    private static final String TYPE_EXERCISE = "exercise";
    private static final String TYPE_PAPER = "paper";

    private final PermissionService permissionService;
    private final CourseMapper courseMapper;
    private final CourseCategoryMapper categoryMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final ChapterMapper chapterMapper;
    private final LessonMapper lessonMapper;
    private final LearningProgressMapper progressMapper;
    private final GradeRecordMapper gradeRecordMapper;
    private final QuestionMapper questionMapper;
    private final PaperMapper paperMapper;

    public RecommendationService(PermissionService permissionService,
                                 CourseMapper courseMapper,
                                 CourseCategoryMapper categoryMapper,
                                 TeachingClassMapper teachingClassMapper,
                                 ChapterMapper chapterMapper,
                                 LessonMapper lessonMapper,
                                 LearningProgressMapper progressMapper,
                                 GradeRecordMapper gradeRecordMapper,
                                 QuestionMapper questionMapper,
                                 PaperMapper paperMapper) {
        this.permissionService = permissionService;
        this.courseMapper = courseMapper;
        this.categoryMapper = categoryMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.chapterMapper = chapterMapper;
        this.lessonMapper = lessonMapper;
        this.progressMapper = progressMapper;
        this.gradeRecordMapper = gradeRecordMapper;
        this.questionMapper = questionMapper;
        this.paperMapper = paperMapper;
    }

    public RecommendationResponseDTO recommendForCurrentUser(String type, Integer limit) {
        AuthUtils.LoginUser user = permissionService.currentUser();
        String normalizedType = normalizeType(type);
        int maxItems = clamp(limit, 1, 40, 24);

        RecommendationResponseDTO response = permissionService.isStudent(user)
                ? recommendForStudent(user, normalizedType, maxItems)
                : recommendForWorkspaceUser(user, normalizedType, maxItems);

        List<RecommendationItemDTO> filtered = filterByType(response.getItems(), normalizedType);
        filtered.sort(Comparator.comparingDouble(RecommendationItemDTO::getScore).reversed());
        if (filtered.size() > maxItems) {
            filtered = new ArrayList<>(filtered.subList(0, maxItems));
        }
        response.setItems(filtered);
        response.setCounts(countByType(filtered));
        return response;
    }

    private RecommendationResponseDTO recommendForStudent(AuthUtils.LoginUser user, String type, int maxItems) {
        Integer studentId = user.getUserId();
        List<Integer> classIds = permissionService.studentClassIds(studentId);
        List<Integer> enrolledCourseIds = permissionService.studentCourseIds(studentId);
        Map<Integer, Integer> courseClassMap = mapCourseToClass(classIds);
        List<Course> enrolledCourses = selectCourses(enrolledCourseIds);
        Map<Integer, Course> enrolledCourseMap = enrolledCourses.stream()
                .collect(Collectors.toMap(Course::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<Integer, String> categoryNames = categoryNames(enrolledCourses.stream().map(Course::getCategoryId).toList());

        List<LearningProgress> progressList = listStudentProgress(studentId, classIds);
        Map<Integer, LearningProgress> progressByLesson = latestProgressByLesson(progressList);
        List<GradeRecord> grades = listStudentGrades(studentId);
        Set<Integer> weakCourseIds = weakCourseIds(grades);
        Set<Integer> enrolledCategoryIds = enrolledCourses.stream()
                .map(Course::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Integer> weakCategoryIds = enrolledCourses.stream()
                .filter(course -> weakCourseIds.contains(course.getId()))
                .map(Course::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<RecommendationItemDTO> items = new ArrayList<>();
        if (matches(type, TYPE_COURSE)) {
            items.addAll(recommendCourses(enrolledCourseIds, enrolledCategoryIds, weakCategoryIds, categoryNames, maxItems));
        }
        if (matches(type, TYPE_LESSON)) {
            items.addAll(recommendLessons(enrolledCourseMap, courseClassMap, progressByLesson, weakCourseIds, maxItems));
        }
        if (matches(type, TYPE_EXERCISE)) {
            items.addAll(recommendExercises(enrolledCourseMap, weakCourseIds, grades, maxItems));
        }
        if (matches(type, TYPE_PAPER)) {
            items.addAll(recommendPapers(enrolledCourses, grades, maxItems));
        }

        RecommendationResponseDTO response = new RecommendationResponseDTO();
        response.setSummary(buildStudentSummary(enrolledCourses, progressList, grades));
        response.setProfileTags(buildProfileTags(enrolledCourses, weakCourseIds, categoryNames));
        response.setItems(items);
        return response;
    }

    private RecommendationResponseDTO recommendForWorkspaceUser(AuthUtils.LoginUser user, String type, int maxItems) {
        if (permissionService.isTeacher(user)) {
            return recommendPopularCoursesForTeacher(maxItems);
        }
        List<RecommendationItemDTO> items = new ArrayList<>();
        if (matches(type, TYPE_COURSE)) {
            items.add(workspaceItem(TYPE_COURSE, "建设课程内容", "课程与章节",
                    "补充课程基础信息、章节、课时和资源。",
                    "课程内容越完整，学生推荐页的可召回资源越充分。", "/courses", 86));
        }
        if (matches(type, TYPE_LESSON)) {
            items.add(workspaceItem(TYPE_LESSON, "完善学习路径", "课时资源",
                    "优先补齐课时内容和视频资源。",
                    "学生侧会根据未完成课时生成继续学习推荐。", "/courses", 82));
        }
        if (matches(type, TYPE_EXERCISE)) {
            items.add(workspaceItem(TYPE_EXERCISE, "维护题库知识点", "习题与测验",
                    "为题目填写知识点、难度和课程关联。",
                    "后续可以根据低分课程推荐更准确的巩固练习。", "/questions", 78));
        }
        if (matches(type, TYPE_PAPER)) {
            items.add(workspaceItem(TYPE_PAPER, "准备课程论文资料", "资料研究",
                    "导入与课程方向相关的本地论文。",
                    "论文推荐会优先使用本地资料库作为拓展阅读来源。", "/paper-search", 74));
        }

        RecommendationResponseDTO response = new RecommendationResponseDTO();
        response.setSummary(permissionService.isAdmin(user)
                ? "管理员视角推荐优先展示能提升平台内容质量的运营入口。"
                : "教师视角推荐优先展示课程建设、题库维护和资料研究入口。");
        response.setProfileTags(List.of(permissionService.isAdmin(user) ? "平台运营" : "教学建设", "内容质量", "推荐数据准备"));
        response.setItems(items.stream().limit(maxItems).toList());
        return response;
    }

    private RecommendationResponseDTO recommendPopularCoursesForTeacher(int maxItems) {
        QueryWrapper<Course> query = new QueryWrapper<>();
        query.eq("status", 1)
                .orderByDesc("view_count")
                .orderByDesc("id")
                .last("LIMIT " + Math.min(maxItems, 40));
        List<Course> courses = courseMapper.selectList(query);
        Map<Integer, String> categoryNames = categoryNames(courses.stream().map(Course::getCategoryId).toList());

        List<RecommendationItemDTO> items = courses.stream()
                .map(course -> {
                    int viewCount = defaultInt(course.getViewCount());
                    RecommendationItemDTO item = item(TYPE_COURSE, course.getId(), course.getTitle(),
                            categoryNames.get(course.getCategoryId()), trim(course.getDescription(), 96),
                            "平台浏览量较高，适合参考其课程内容与组织方式。",
                            "/courses/" + course.getId(), "查看课程", viewCount);
                    item.getMeta().put("viewCount", viewCount);
                    item.getMeta().put("coverUrl", firstNonBlank(course.getCoverUrl(), course.getCover()));
                    return item;
                })
                .toList();

        RecommendationResponseDTO response = new RecommendationResponseDTO();
        response.setSummary("按已发布课程的浏览量推荐，帮助你了解平台上的热门课程。");
        response.setProfileTags(List.of("热门课程", "浏览量排序"));
        response.setItems(items);
        return response;
    }

    private List<RecommendationItemDTO> recommendCourses(List<Integer> enrolledCourseIds,
                                                         Set<Integer> enrolledCategoryIds,
                                                         Set<Integer> weakCategoryIds,
                                                         Map<Integer, String> knownCategoryNames,
                                                         int maxItems) {
        QueryWrapper<Course> query = new QueryWrapper<>();
        query.eq("status", 1);
        if (!enrolledCourseIds.isEmpty()) {
            query.notIn("id", enrolledCourseIds);
        }
        query.orderByDesc("rating")
                .orderByDesc("student_count")
                .orderByDesc("view_count")
                .last("LIMIT 80");
        List<Course> candidates = courseMapper.selectList(query);
        Map<Integer, String> categoryNames = categoryNames(candidates.stream().map(Course::getCategoryId).toList());
        categoryNames.putAll(knownCategoryNames);

        return candidates.stream()
                .map(course -> {
                    boolean sameCategory = course.getCategoryId() != null && enrolledCategoryIds.contains(course.getCategoryId());
                    boolean weakRelated = course.getCategoryId() != null && weakCategoryIds.contains(course.getCategoryId());
                    double score = 52 + popularityScore(course);
                    if (sameCategory) score += 18;
                    if (weakRelated) score += 14;
                    score += difficultyScore(course.getDifficulty());

                    String categoryName = categoryNames.get(course.getCategoryId());
                    String reason = weakRelated
                            ? "你在同类课程的成绩或进度还有提升空间，适合用这门课补强。"
                            : sameCategory
                            ? "与你已学习的课程方向接近，可以作为后续拓展。"
                            : "平台热度和评分较高，适合作为新的学习方向。";
                    RecommendationItemDTO item = item(TYPE_COURSE, course.getId(), course.getTitle(), categoryName,
                            trim(course.getDescription(), 96), reason, "/courses/" + course.getId(), "查看课程", score);
                    item.getMeta().put("difficulty", course.getDifficulty());
                    item.getMeta().put("rating", course.getRating());
                    item.getMeta().put("studentCount", defaultInt(course.getStudentCount()));
                    item.getMeta().put("coverUrl", firstNonBlank(course.getCoverUrl(), course.getCover()));
                    return item;
                })
                .sorted(Comparator.comparingDouble(RecommendationItemDTO::getScore).reversed())
                .limit(Math.min(maxItems, 8))
                .toList();
    }

    private List<RecommendationItemDTO> recommendLessons(Map<Integer, Course> enrolledCourseMap,
                                                         Map<Integer, Integer> courseClassMap,
                                                         Map<Integer, LearningProgress> progressByLesson,
                                                         Set<Integer> weakCourseIds,
                                                         int maxItems) {
        if (enrolledCourseMap.isEmpty()) {
            return List.of();
        }
        List<Chapter> chapters = selectChapters(enrolledCourseMap.keySet());
        if (chapters.isEmpty()) {
            return List.of();
        }
        Map<Integer, Integer> chapterCourseMap = chapters.stream()
                .collect(Collectors.toMap(Chapter::getId, Chapter::getCourseId, (a, b) -> a));
        List<Lesson> lessons = selectLessons(chapterCourseMap.keySet());

        return lessons.stream()
                .map(lesson -> {
                    Integer courseId = chapterCourseMap.get(lesson.getChapterId());
                    Course course = enrolledCourseMap.get(courseId);
                    if (course == null) {
                        return null;
                    }
                    LearningProgress progress = progressByLesson.get(lesson.getId());
                    int progressValue = progress == null || progress.getProgress() == null ? 0 : progress.getProgress();
                    if (progressValue >= 100) {
                        return null;
                    }

                    double score = 92 - progressValue * 0.35;
                    if (weakCourseIds.contains(courseId)) score += 12;
                    score += recentProgressBoost(progress);

                    Integer classId = courseClassMap.get(courseId);
                    String target = "/course-learn/" + courseId + (classId == null ? "" : "?classId=" + classId + "&lessonId=" + lesson.getId());
                    String reason = progressValue > 0
                            ? "你已经学到 " + progressValue + "%，建议继续完成这个课时。"
                            : weakCourseIds.contains(courseId)
                            ? "该课程近期表现偏弱，建议从这个课时开始补齐基础。"
                            : "这是已加入课程中尚未完成的课时，适合继续学习。";
                    RecommendationItemDTO item = item(TYPE_LESSON, lesson.getId(), lesson.getTitle(), course.getTitle(),
                            trim(lesson.getContent(), 96), reason, target, "继续学习", score);
                    item.getMeta().put("courseId", courseId);
                    item.getMeta().put("classId", classId);
                    item.getMeta().put("progress", progressValue);
                    item.getMeta().put("duration", defaultInt(lesson.getDuration()));
                    return item;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(RecommendationItemDTO::getScore).reversed())
                .limit(Math.min(maxItems, 8))
                .toList();
    }

    private List<RecommendationItemDTO> recommendExercises(Map<Integer, Course> enrolledCourseMap,
                                                           Set<Integer> weakCourseIds,
                                                           List<GradeRecord> grades,
                                                           int maxItems) {
        if (enrolledCourseMap.isEmpty()) {
            return List.of();
        }
        Set<Integer> candidateCourseIds = weakCourseIds.isEmpty()
                ? enrolledCourseMap.keySet()
                : weakCourseIds;
        QueryWrapper<Question> query = new QueryWrapper<>();
        query.in("course_id", candidateCourseIds)
                .eq("status", 1)
                .orderByDesc("difficulty")
                .orderByDesc("id")
                .last("LIMIT 40");
        List<Question> questions = questionMapper.selectList(query);
        Set<String> weakTerms = weakTerms(grades, enrolledCourseMap);

        return questions.stream()
                .map(question -> {
                    Course course = enrolledCourseMap.get(question.getCourseId());
                    if (course == null) {
                        return null;
                    }
                    boolean weakCourse = weakCourseIds.contains(question.getCourseId());
                    boolean weakTerm = containsAny(question.getKnowledgePoint(), weakTerms) || containsAny(question.getTitle(), weakTerms);
                    double score = 58 + defaultInt(question.getDifficulty()) * 8;
                    if (weakCourse) score += 18;
                    if (weakTerm) score += 10;
                    String point = firstNonBlank(question.getKnowledgePoint(), "核心知识点");
                    String prompt = "请围绕" + course.getTitle() + "的" + point + "生成5道练习题，并给出答案解析";
                    RecommendationItemDTO item = item(TYPE_EXERCISE, question.getId(), point + "巩固练习", course.getTitle(),
                            trim(question.getTitle(), 96), weakCourse ? "这门课的近期成绩偏低，建议针对该知识点做练习。" : "根据你的课程进度推荐的巩固练习方向。",
                            "/agent-chat?prompt=" + encode(prompt), "生成练习", score);
                    item.getMeta().put("courseId", question.getCourseId());
                    item.getMeta().put("questionId", question.getId());
                    item.getMeta().put("difficulty", question.getDifficulty());
                    return item;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(RecommendationItemDTO::getScore).reversed())
                .limit(Math.min(maxItems, 6))
                .toList();
    }

    private List<RecommendationItemDTO> recommendPapers(List<Course> enrolledCourses,
                                                        List<GradeRecord> grades,
                                                        int maxItems) {
        List<String> keywords = recommendationKeywords(enrolledCourses, grades);
        QueryWrapper<Paper> query = new QueryWrapper<>();
        if (!keywords.isEmpty()) {
            query.and(w -> {
                for (int i = 0; i < keywords.size() && i < 8; i++) {
                    String term = keywords.get(i);
                    if (i == 0) {
                        w.like("title", term).or().like("abstract_text", term);
                    } else {
                        w.or().like("title", term).or().like("abstract_text", term);
                    }
                }
            });
        }
        query.orderByDesc("citation_count").orderByDesc("`year`").last("LIMIT 20");
        List<Paper> papers = paperMapper.selectList(query);
        String firstKeyword = keywords.isEmpty() ? "" : keywords.get(0);

        return papers.stream()
                .map(paper -> {
                    double score = 54 + Math.min(28, Math.log10(Math.max(1, defaultInt(paper.getCitationCount()))) * 8);
                    if (paper.getYear() != null && paper.getYear() >= 2022) score += 6;
                    RecommendationItemDTO item = item(TYPE_PAPER, paper.getId(), paper.getTitle(), paper.getVenue(),
                            trim(paper.getAbstractText(), 120), firstKeyword.isBlank()
                                    ? "本地论文库中引用较高的资料，适合拓展阅读。"
                                    : "与你近期学习的「" + firstKeyword + "」方向相关，适合拓展阅读。",
                            "/paper-search", "查看论文", score);
                    item.getMeta().put("paperId", paper.getArxivId());
                    item.getMeta().put("year", paper.getYear());
                    item.getMeta().put("citationCount", defaultInt(paper.getCitationCount()));
                    item.getMeta().put("url", paper.getUrl());
                    item.getMeta().put("pdfUrl", paper.getPdfUrl());
                    return item;
                })
                .sorted(Comparator.comparingDouble(RecommendationItemDTO::getScore).reversed())
                .limit(Math.min(maxItems, 6))
                .toList();
    }

    private Map<Integer, Integer> mapCourseToClass(List<Integer> classIds) {
        if (classIds.isEmpty()) {
            return Map.of();
        }
        return teachingClassMapper.selectBatchIds(classIds).stream()
                .filter(item -> item.getCourseId() != null && item.getId() != null)
                .collect(Collectors.toMap(TeachingClass::getCourseId, TeachingClass::getId, (a, b) -> a, LinkedHashMap::new));
    }

    private List<Course> selectCourses(List<Integer> courseIds) {
        if (courseIds.isEmpty()) {
            return List.of();
        }
        return courseMapper.selectBatchIds(courseIds).stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private List<Chapter> selectChapters(Collection<Integer> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper<Chapter> query = new QueryWrapper<>();
        query.in("course_id", courseIds)
                .eq("status", Chapter.STATUS_NORMAL)
                .orderByAsc("course_id")
                .orderByAsc("sort_order")
                .orderByAsc("id");
        return chapterMapper.selectList(query);
    }

    private List<Lesson> selectLessons(Collection<Integer> chapterIds) {
        if (chapterIds == null || chapterIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper<Lesson> query = new QueryWrapper<>();
        query.in("chapter_id", chapterIds)
                .eq("status", 1)
                .orderByAsc("chapter_id")
                .orderByAsc("sort_order")
                .orderByAsc("id");
        return lessonMapper.selectList(query);
    }

    private List<LearningProgress> listStudentProgress(Integer studentId, List<Integer> classIds) {
        QueryWrapper<LearningProgress> query = new QueryWrapper<>();
        query.eq("student_id", studentId);
        if (!classIds.isEmpty()) {
            query.in("class_id", classIds);
        }
        query.orderByDesc("last_learn_time").orderByDesc("id").last("LIMIT 200");
        return progressMapper.selectList(query);
    }

    private Map<Integer, LearningProgress> latestProgressByLesson(List<LearningProgress> progressList) {
        Map<Integer, LearningProgress> map = new HashMap<>();
        for (LearningProgress progress : progressList) {
            if (progress.getLessonId() == null) {
                continue;
            }
            map.putIfAbsent(progress.getLessonId(), progress);
        }
        return map;
    }

    private List<GradeRecord> listStudentGrades(Integer studentId) {
        QueryWrapper<GradeRecord> query = new QueryWrapper<>();
        query.eq("student_id", studentId)
                .orderByDesc("create_time")
                .orderByDesc("id")
                .last("LIMIT 80");
        return gradeRecordMapper.selectList(query);
    }

    private Set<Integer> weakCourseIds(List<GradeRecord> grades) {
        return grades.stream()
                .filter(grade -> grade.getCourseId() != null && gradeRatio(grade) < 0.75)
                .map(GradeRecord::getCourseId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> weakTerms(List<GradeRecord> grades, Map<Integer, Course> courseMap) {
        Set<String> terms = new LinkedHashSet<>();
        for (GradeRecord grade : grades) {
            if (gradeRatio(grade) >= 0.75) {
                continue;
            }
            Course course = courseMap.get(grade.getCourseId());
            if (course != null) {
                terms.addAll(splitTerms(course.getTitle()));
            }
            terms.addAll(splitTerms(grade.getGradeName()));
            terms.addAll(splitTerms(grade.getComment()));
        }
        return terms;
    }

    private List<String> recommendationKeywords(List<Course> courses, List<GradeRecord> grades) {
        Set<String> terms = new LinkedHashSet<>();
        for (Course course : courses) {
            terms.addAll(courseTerms(course));
        }
        for (GradeRecord grade : grades) {
            if (gradeRatio(grade) < 0.8) {
                terms.addAll(splitTerms(grade.getGradeName()));
                terms.addAll(splitTerms(grade.getComment()));
            }
        }
        return terms.stream().filter(term -> term.length() >= 2).limit(12).toList();
    }

    private List<String> courseTerms(Course course) {
        if (course == null) {
            return List.of();
        }
        String text = (nullToEmpty(course.getTitle()) + " " + nullToEmpty(course.getDescription())).toLowerCase(Locale.ROOT);
        List<String> terms = new ArrayList<>();
        addTerm(terms, text.contains("java"), "Java", "Spring", "JVM");
        addTerm(terms, text.contains("python"), "Python", "machine learning");
        addTerm(terms, text.contains("vue") || text.contains("前端"), "frontend", "Vue", "component");
        addTerm(terms, text.contains("mysql") || text.contains("数据库"), "MySQL", "database", "SQL");
        addTerm(terms, text.contains("算法") || text.contains("数据结构"), "algorithm", "graph", "data structure");
        addTerm(terms, text.contains("人工智能") || text.contains("ai"), "machine learning", "deep learning");
        if (terms.isEmpty()) {
            terms.addAll(splitTerms(course.getTitle()));
        }
        return terms;
    }

    private void addTerm(List<String> terms, boolean match, String... values) {
        if (match) {
            terms.addAll(List.of(values));
        }
    }

    private List<String> splitTerms(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String[] parts = value.split("[\\s，,、：:；;（）()\\[\\]【】/\\\\-]+");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.length() >= 2 && trimmed.length() <= 30) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private Map<Integer, String> categoryNames(Collection<Integer> categoryIds) {
        Set<Integer> ids = categoryIds == null
                ? Set.of()
                : categoryIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return categoryMapper.selectBatchIds(ids).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(CourseCategory::getId, CourseCategory::getName, (a, b) -> a, HashMap::new));
    }

    private List<String> buildProfileTags(List<Course> courses, Set<Integer> weakCourseIds, Map<Integer, String> categoryNames) {
        List<String> tags = new ArrayList<>();
        courses.stream()
                .map(course -> categoryNames.get(course.getCategoryId()))
                .filter(Objects::nonNull)
                .distinct()
                .limit(3)
                .forEach(tags::add);
        if (!weakCourseIds.isEmpty()) {
            tags.add("薄弱课程 " + weakCourseIds.size() + " 门");
        }
        if (tags.isEmpty()) {
            tags.add("新学习画像");
        }
        return tags;
    }

    private String buildStudentSummary(List<Course> courses, List<LearningProgress> progressList, List<GradeRecord> grades) {
        long unfinished = progressList.stream().filter(progress -> defaultInt(progress.getProgress()) < 100).count();
        long weak = grades.stream().filter(grade -> gradeRatio(grade) < 0.75).count();
        if (courses.isEmpty()) {
            return "还没有加入课程，先推荐平台热门课程作为起点。";
        }
        return "基于你已加入的 " + courses.size() + " 门课程、" + unfinished + " 条未完成学习进度和 " + weak + " 条待提升成绩生成。";
    }

    private RecommendationItemDTO workspaceItem(String type,
                                                String title,
                                                String subtitle,
                                                String description,
                                                String reason,
                                                String targetUrl,
                                                double score) {
        return item(type, null, title, subtitle, description, reason, targetUrl, "进入", score);
    }

    private RecommendationItemDTO item(String type,
                                       Integer itemId,
                                       String title,
                                       String subtitle,
                                       String description,
                                       String reason,
                                       String targetUrl,
                                       String actionText,
                                       double score) {
        RecommendationItemDTO item = new RecommendationItemDTO();
        item.setType(type);
        item.setItemId(itemId);
        item.setId(type + "-" + (itemId == null ? Math.abs(Objects.hash(title, targetUrl)) : itemId));
        item.setTitle(firstNonBlank(title, "未命名推荐"));
        item.setSubtitle(subtitle);
        item.setDescription(description);
        item.setReason(reason);
        item.setTargetUrl(targetUrl);
        item.setActionText(actionText);
        item.setScore(Math.round(score * 10.0) / 10.0);
        return item;
    }

    private List<RecommendationItemDTO> filterByType(List<RecommendationItemDTO> items, String type) {
        if (TYPE_ALL.equals(type)) {
            return new ArrayList<>(items);
        }
        return items.stream().filter(item -> type.equals(item.getType())).collect(Collectors.toCollection(ArrayList::new));
    }

    private Map<String, Long> countByType(List<RecommendationItemDTO> items) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(TYPE_COURSE, 0L);
        counts.put(TYPE_LESSON, 0L);
        counts.put(TYPE_EXERCISE, 0L);
        counts.put(TYPE_PAPER, 0L);
        items.stream()
                .collect(Collectors.groupingBy(RecommendationItemDTO::getType, LinkedHashMap::new, Collectors.counting()))
                .forEach(counts::put);
        return counts;
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return TYPE_ALL;
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case TYPE_ALL, TYPE_COURSE, TYPE_LESSON, TYPE_EXERCISE, TYPE_PAPER -> normalized;
            default -> throw new BusinessException("推荐类型不支持: " + type);
        };
    }

    private boolean matches(String requestedType, String itemType) {
        return TYPE_ALL.equals(requestedType) || itemType.equals(requestedType);
    }

    private int clamp(Integer value, int min, int max, int fallback) {
        int fixed = value == null ? fallback : value;
        return Math.max(min, Math.min(max, fixed));
    }

    private double popularityScore(Course course) {
        double ratingScore = Optional.ofNullable(course.getRating()).map(BigDecimal::doubleValue).orElse(0.0) * 2.0;
        double studentScore = Math.min(7, Math.log10(Math.max(1, defaultInt(course.getStudentCount()))) * 3);
        double viewScore = Math.min(6, Math.log10(Math.max(1, defaultInt(course.getViewCount()))) * 2);
        return ratingScore + studentScore + viewScore;
    }

    private double difficultyScore(String difficulty) {
        if (difficulty == null) {
            return 0;
        }
        String normalized = difficulty.toLowerCase(Locale.ROOT);
        if (normalized.contains("intermediate")) return 6;
        if (normalized.contains("advanced")) return 4;
        if (normalized.contains("beginner")) return 3;
        return 0;
    }

    private double recentProgressBoost(LearningProgress progress) {
        if (progress == null || progress.getLastLearnTime() == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(progress.getLastLearnTime(), LocalDateTime.now());
        if (days <= 2) return 8;
        if (days <= 7) return 4;
        return 0;
    }

    private double gradeRatio(GradeRecord grade) {
        if (grade == null || grade.getScore() == null || grade.getFullScore() == null || BigDecimal.ZERO.compareTo(grade.getFullScore()) >= 0) {
            return 1.0;
        }
        return grade.getScore().doubleValue() / grade.getFullScore().doubleValue();
    }

    private boolean containsAny(String value, Set<String> terms) {
        if (value == null || value.isBlank() || terms == null || terms.isEmpty()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return terms.stream().anyMatch(term -> normalized.contains(term.toLowerCase(Locale.ROOT)));
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 1)) + "...";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
