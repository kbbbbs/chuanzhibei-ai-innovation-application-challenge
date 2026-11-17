package com.cupk.aiacademy.learning;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cupk.aiacademy.mapper.ChapterMapper;
import com.cupk.aiacademy.mapper.CourseMapper;
import com.cupk.aiacademy.mapper.LearningProgressMapper;
import com.cupk.aiacademy.mapper.LessonMapper;
import com.cupk.aiacademy.pojo.Chapter;
import com.cupk.aiacademy.pojo.Course;
import com.cupk.aiacademy.pojo.LearningProgress;
import com.cupk.aiacademy.pojo.Lesson;
import com.cupk.aiacademy.service.PermissionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LearningExperienceService {
    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final LessonMapper lessonMapper;
    private final LearningProgressMapper progressMapper;
    private final PermissionService permissionService;
    private final LearningContentRepository contentRepository;

    public LearningExperienceService(CourseMapper courseMapper,
                                     ChapterMapper chapterMapper,
                                     LessonMapper lessonMapper,
                                     LearningProgressMapper progressMapper,
                                     PermissionService permissionService,
                                     LearningContentRepository contentRepository) {
        this.courseMapper = courseMapper;
        this.chapterMapper = chapterMapper;
        this.lessonMapper = lessonMapper;
        this.progressMapper = progressMapper;
        this.permissionService = permissionService;
        this.contentRepository = contentRepository;
    }

    public LearningExperienceDTO courseExperience(Integer courseId, Integer classId) {
        var user = permissionService.currentUser();
        permissionService.requireCourseContentRead(courseId);
        if (permissionService.isStudent(user) && classId != null) {
            permissionService.requireStudentLearningClass(classId, courseId, user.getUserId());
        }
        Course course = courseMapper.selectById(courseId);
        LearningContentRepository.LearningProfile profile = contentRepository.profileFor(course);
        Map<Integer, LearningProgress> progress = progressByLesson(classId, user.getUserId());
        List<Chapter> courseChapters = chapters(courseId);
        List<LearningExperienceDTO.PathNode> path = new ArrayList<>();
        List<LearningExperienceDTO.TextbookChapter> textbook = new ArrayList<>();
        List<LearningExperienceDTO.Experiment> experiments = new ArrayList<>();
        List<LearningExperienceDTO.Challenge> challenges = new ArrayList<>();
        List<LearningExperienceDTO.CodeLab> codeLabs = new ArrayList<>();
        boolean previousDone = true;
        int totalMinutes = 0;

        for (Chapter chapter : courseChapters) {
            List<Lesson> chapterLessons = lessons(chapter.getId());
            LearningExperienceDTO.TextbookChapter textChapter = textbookChapter(chapter, profile);
            List<LearningExperienceDTO.TextbookLesson> textLessons = new ArrayList<>();

            for (Lesson lesson : chapterLessons) {
                LearningProgress item = progress.get(lesson.getId());
                int percent = item == null || item.getProgress() == null ? 0 : item.getProgress();
                boolean unlocked = previousDone || percent > 0;
                totalMinutes += safeDurationMinutes(lesson.getDuration());
                path.add(pathNode(chapter, lesson, percent, unlocked));
                LearningExperienceDTO.TextbookLesson textLesson = textbookLesson(chapter, lesson, percent, unlocked);
                textLessons.add(textLesson);
                if (unlocked) {
                    experiments.addAll(contentRepository.experimentsFromPage(textLesson.getPage(), lesson.getId()));
                    challenges.addAll(contentRepository.challengesFromPage(textLesson.getPage(), lesson.getId()));
                    codeLabs.addAll(contentRepository.codeLabsFromPage(textLesson.getPage(), lesson.getId()));
                }
                previousDone = percent >= 100;
            }
            textChapter.setLessons(textLessons);
            textbook.add(textChapter);
        }

        LearningExperienceDTO dto = new LearningExperienceDTO();
        dto.setOverview(overview(course, profile, path.size(), totalMinutes));
        dto.setPath(path);
        dto.setTextbook(textbook);
        dto.setExperiments(experiments);
        dto.setChallenges(challenges);
        dto.setCodeLabs(codeLabs);
        dto.setCodeLab(dto.getCodeLabs().isEmpty() ? null : dto.getCodeLabs().getFirst());
        return dto;
    }

    private LearningExperienceDTO.CourseOverview overview(Course course, LearningContentRepository.LearningProfile profile,
                                                          int totalLessons, int totalMinutes) {
        LearningExperienceDTO.CourseOverview overview = new LearningExperienceDTO.CourseOverview();
        overview.setTitle(course == null ? "课程学习" : course.getTitle());
        overview.setDomain(profile.getLabel());
        overview.setTagline(profile.getTagline());
        overview.setAudience(profile.getAudience());
        overview.setFinalProject(profile.getFinalProject());
        overview.setOutcomes(profile.getOutcomes());
        overview.setTotalLessons(totalLessons);
        overview.setEstimatedMinutes(totalMinutes);
        return overview;
    }

    private LearningExperienceDTO.TextbookChapter textbookChapter(Chapter chapter, LearningContentRepository.LearningProfile profile) {
        LearningExperienceDTO.TextbookChapter textChapter = new LearningExperienceDTO.TextbookChapter();
        textChapter.setId(chapter.getId());
        textChapter.setTitle(chapter.getTitle());
        textChapter.setDescription(nonBlank(chapter.getDescription(), contentRepository.chapterDescription(profile, chapter.getTitle())));
        return textChapter;
    }

    private LearningExperienceDTO.PathNode pathNode(Chapter chapter, Lesson lesson, int percent, boolean unlocked) {
        LearningExperienceDTO.PathNode node = new LearningExperienceDTO.PathNode();
        node.setId(lesson.getId());
        node.setTitle(lesson.getTitle());
        node.setChapterTitle(chapter.getTitle());
        node.setType(lesson.getResourceType());
        node.setProgress(percent);
        node.setUnlocked(unlocked);
        node.setDuration(lesson.getDuration());
        node.setSummary(unlocked ? contentRepository.lessonSummary(lesson) : "完成前置节点后解锁本节内容与练习");
        return node;
    }

    private LearningExperienceDTO.TextbookLesson textbookLesson(Chapter chapter, Lesson lesson, int percent, boolean unlocked) {
        LearningExperienceDTO.TextbookLesson item = new LearningExperienceDTO.TextbookLesson();
        item.setId(lesson.getId());
        item.setTitle(lesson.getTitle());
        item.setResourceType(lesson.getResourceType());
        item.setResourceUrl(lesson.getResourceUrl());
        item.setDuration(lesson.getDuration());
        item.setProgress(percent);
        item.setUnlocked(unlocked);
        Map<String, Object> page = contentRepository.lessonPage(chapter.getTitle(), lesson, unlocked);
        item.setPage(page);
        item.setBlocks(contentRepository.blocksFromPage(page));
        item.setContent(contentRepository.markdownFromPage(page));
        item.setCheckpoints(contentRepository.checkpointsFromPage(page));
        return item;
    }

    private Map<Integer, LearningProgress> progressByLesson(Integer classId, Integer studentId) {
        if (classId == null || classId <= 0 || studentId == null) return Map.of();
        QueryWrapper<LearningProgress> query = new QueryWrapper<>();
        query.eq("class_id", classId).eq("student_id", studentId);
        return progressMapper.selectList(query).stream()
                .filter(p -> p.getLessonId() != null)
                .collect(Collectors.toMap(LearningProgress::getLessonId, p -> p, (a, b) -> a));
    }

    private List<Chapter> chapters(Integer courseId) {
        return chapterMapper.selectList(new QueryWrapper<Chapter>()
                .eq("course_id", courseId)
                .eq("status", 1)
                .orderByAsc("sort_order").orderByAsc("id"));
    }

    private List<Lesson> lessons(Integer chapterId) {
        return lessonMapper.selectList(new QueryWrapper<Lesson>()
                .eq("chapter_id", chapterId)
                .eq("status", 1)
                .orderByAsc("sort_order").orderByAsc("id"));
    }

    private int safeDurationMinutes(Integer seconds) {
        if (seconds == null || seconds <= 0) return 0;
        return Math.max(1, Math.round(seconds / 60f));
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
