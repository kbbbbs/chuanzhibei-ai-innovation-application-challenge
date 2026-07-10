package com.cupk.aiacademy.service;

import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.common.Difficulty;
import com.cupk.aiacademy.common.ExamStatus;
import com.cupk.aiacademy.common.PaperStatus;
import com.cupk.aiacademy.common.QuestionType;
import com.cupk.aiacademy.common.RecordStatus;
import com.cupk.aiacademy.common.Result;
import com.cupk.aiacademy.dto.ExamResponseDTO;
import com.cupk.aiacademy.dto.HomeworkDto;
import com.cupk.aiacademy.dto.PaperRequestDTO;
import com.cupk.aiacademy.dto.PaperResponseDTO;
import com.cupk.aiacademy.dto.QuestionRequestDTO;
import com.cupk.aiacademy.dto.QuestionResponseDTO;
import com.cupk.aiacademy.dto.imports.ExamExportRow;
import com.cupk.aiacademy.dto.imports.HomeworkImportRow;
import com.cupk.aiacademy.dto.imports.ImportConfirmItem;
import com.cupk.aiacademy.dto.imports.ImportConfirmRequest;
import com.cupk.aiacademy.dto.imports.ImportConfirmResponse;
import com.cupk.aiacademy.dto.imports.ImportPreviewItem;
import com.cupk.aiacademy.dto.imports.ImportPreviewResponse;
import com.cupk.aiacademy.dto.imports.PaperExportRow;
import com.cupk.aiacademy.dto.imports.PaperImportConfirmRequest;
import com.cupk.aiacademy.dto.imports.PaperImportRow;
import com.cupk.aiacademy.dto.imports.QuestionImportRow;
import com.cupk.aiacademy.pojo.Homework;
import com.cupk.aiacademy.pojo.Question;
import com.cupk.aiacademy.pojo.TeachingClass;
import com.cupk.aiacademy.service.imports.TabularFileSupport;
import com.cupk.aiacademy.service.imports.TabularFileSupport.ExportFile;
import com.cupk.aiacademy.service.imports.TabularFileSupport.TabularRow;
import com.cupk.aiacademy.service.imports.ImportTemplateSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ImportService {
    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    );
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ISO_LOCAL_DATE
    );

    private final QuestionService questionService;
    private final PaperService paperService;
    private final ExamService examService;
    private final HomeworkService homeworkService;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;

    public ImportService(QuestionService questionService,
                         PaperService paperService,
                         ExamService examService,
                         HomeworkService homeworkService,
                         PermissionService permissionService,
                         ObjectMapper objectMapper) {
        this.questionService = questionService;
        this.paperService = paperService;
        this.examService = examService;
        this.homeworkService = homeworkService;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
    }

    public ImportPreviewResponse preview(String type, MultipartFile file) {
        permissionService.requireTeacherOrAdmin();
        return switch (normalType(type)) {
            case "questions" -> new ImportPreviewResponse(TabularFileSupport.read(file, QuestionImportRow.class, ImportTemplateSupport.QUESTION_SHEET).stream()
                    .map(this::previewQuestion)
                    .toList());
            case "homeworks" -> new ImportPreviewResponse(TabularFileSupport.read(file, HomeworkImportRow.class).stream()
                    .map(this::previewHomework)
                    .toList());
            default -> throw new BusinessException("不支持的导入类型: " + type);
        };
    }

    public ImportPreviewResponse previewPapers(MultipartFile file, Long courseId) {
        permissionService.requireTeacherOrAdmin();
        if (courseId == null || courseId <= 0) {
            throw new BusinessException("课程ID不能为空");
        }
        return new ImportPreviewResponse(TabularFileSupport.read(file, PaperImportRow.class, ImportTemplateSupport.QUESTION_SHEET).stream()
                .map(row -> previewPaper(row, courseId))
                .toList());
    }

    public byte[] template(String type) {
        permissionService.requireTeacherOrAdmin();
        return switch (normalType(type)) {
            case "questions" -> ImportTemplateSupport.questionTemplate();
            case "papers" -> ImportTemplateSupport.paperTemplate();
            case "homeworks" -> TabularFileSupport.write("xlsx", HomeworkImportRow.class, List.of());
            default -> throw new BusinessException("不支持的导入类型: " + type);
        };
    }

    public ImportConfirmResponse confirmQuestions(ImportConfirmRequest request) {
        permissionService.requireTeacherOrAdmin();
        return confirmQuestions(validPreviewItems(request));
    }

    public ImportConfirmResponse confirmPapers(PaperImportConfirmRequest request) {
        permissionService.requireTeacherOrAdmin();
        if (request == null) {
            throw new BusinessException("试卷信息不能为空");
        }
        List<ImportPreviewItem> items = validPreviewItems(request.getItems());
        if (request.getCourseId() == null || request.getCourseId() <= 0) {
            throw new BusinessException("课程ID不能为空");
        }
        if (value(request.getName()).isBlank()) {
            throw new BusinessException("试卷名称不能为空");
        }
        if (request.getDurationMinutes() == null || request.getDurationMinutes() <= 0) {
            throw new BusinessException("时长必须大于0");
        }
        if (request.getStatus() == null) {
            throw new BusinessException("试卷状态不能为空");
        }
        var course = permissionService.getCourse(request.getCourseId().intValue());
        permissionService.requireCourseTeacher(course);
        PaperRequestDTO paper = new PaperRequestDTO();
        paper.setName(value(request.getName()));
        paper.setCourseId(request.getCourseId());
        paper.setTeacherId(course.getTeacherId() == null ? null : course.getTeacherId().longValue());
        paper.setDurationMinutes(request.getDurationMinutes());
        paper.setStatus(request.getStatus());
        paper.setIgnoreWarnings(true);
        paper.setQuestions(items.stream().map(item -> paperQuestion(item, request.getCourseId())).toList());
        try {
            PaperResponseDTO response = paperService.create(paper);
            return new ImportConfirmResponse(List.of(ImportConfirmItem.success(items.getFirst().getRowNumber(), response)));
        } catch (Exception e) {
            return new ImportConfirmResponse(List.of(ImportConfirmItem.failure(items.getFirst().getRowNumber(), e.getMessage(), paper)));
        }
    }

    public ExportFile exportData(String type, String format, Map<String, String> params) {
        permissionService.requireTeacherOrAdmin();
        Map<String, String> safeParams = params == null ? Map.of() : params;
        return switch (normalType(type)) {
            case "questions" -> TabularFileSupport.exportFile("questions-export", format, QuestionImportRow.class, exportQuestions(safeParams));
            case "papers" -> TabularFileSupport.exportFile("papers-export", format, PaperExportRow.class, exportPapers(safeParams));
            case "exams" -> TabularFileSupport.exportFile("exams-export", format, ExamExportRow.class, exportExams(safeParams));
            case "homeworks" -> TabularFileSupport.exportFile("homeworks-export", format, HomeworkImportRow.class, exportHomeworks(safeParams));
            default -> throw new BusinessException("不支持的导出类型: " + type);
        };
    }

    private ImportConfirmResponse confirmQuestions(List<ImportPreviewItem> items) {
        List<ImportConfirmItem> results = new ArrayList<>();
        for (ImportPreviewItem item : items) {
            QuestionRequestDTO request = convert(item.getNormalizedData(), QuestionRequestDTO.class);
            try {
                QuestionResponseDTO response = questionService.create(request);
                results.add(ImportConfirmItem.success(item.getRowNumber(), response));
            } catch (Exception e) {
                results.add(ImportConfirmItem.failure(item.getRowNumber(), e.getMessage(), request));
            }
        }
        return new ImportConfirmResponse(results);
    }

    private ImportConfirmResponse confirmHomeworks(List<ImportPreviewItem> items) {
        List<ImportConfirmItem> results = new ArrayList<>();
        Map<String, HomeworkGroup> groups = new LinkedHashMap<>();
        for (ImportPreviewItem item : items) {
            HomeworkRowData row = convert(item.getNormalizedData(), HomeworkRowData.class);
            String key = row.getClassId() + "|" + row.getTitle();
            groups.computeIfAbsent(key, ignored -> new HomeworkGroup(item.getRowNumber(), row)).add(row);
        }
        for (HomeworkGroup group : groups.values()) {
            try {
                HomeworkDto request = group.toRequest(this::ensureQuestionId);
                Result result = homeworkService.create(request);
                if ("200".equals(String.valueOf(result.getCode()))) {
                    results.add(ImportConfirmItem.success(group.rowNumber(), result.getData()));
                } else {
                    results.add(ImportConfirmItem.failure(group.rowNumber(), result.getMsg(), request));
                }
            } catch (Exception e) {
                results.add(ImportConfirmItem.failure(group.rowNumber(), e.getMessage(), group.rows()));
            }
        }
        return new ImportConfirmResponse(results);
    }

    private List<ImportPreviewItem> validPreviewItems(ImportConfirmRequest request) {
        if (request == null) {
            throw new BusinessException("导入数据不能为空");
        }
        return validPreviewItems(request.getItems());
    }

    private List<ImportPreviewItem> validPreviewItems(List<ImportPreviewItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("导入数据不能为空");
        }
        if (items.stream().anyMatch(item -> !Boolean.TRUE.equals(item.getValid()))) {
            throw new BusinessException("存在校验未通过的导入行，请先修正后再导入");
        }
        return items;
    }

    private PaperRequestDTO.PaperQuestionRequest paperQuestion(ImportPreviewItem item, Long courseId) {
        PaperRowData row = convert(item.getNormalizedData(), PaperRowData.class);
        if (!Objects.equals(courseId, row.getCourseId()) || row.getQuestion() == null) {
            throw new BusinessException("预览数据与当前试卷课程不一致，请重新预览");
        }
        PaperRequestDTO.PaperQuestionRequest question = new PaperRequestDTO.PaperQuestionRequest();
        question.setInlineQuestion(row.getQuestion());
        question.setSortOrder(row.getSortOrder());
        question.setScore(row.getScore());
        question.setRequiredAnswer(true);
        return question;
    }

    private ImportPreviewItem previewQuestion(TabularRow<QuestionImportRow> row) {
        List<String> errors = new ArrayList<>();
        QuestionImportRow source = row.data();
        QuestionRequestDTO request = new QuestionRequestDTO();
        Long courseId = parseLong(source.getCourseId(), "课程ID", errors);
        fillQuestionRequest(request, questionColumns(source), courseId, errors);
        return item(row.rowNumber(), errors, request);
    }

    private ImportPreviewItem previewPaper(TabularRow<PaperImportRow> row, Long courseId) {
        List<String> errors = new ArrayList<>();
        PaperImportRow source = row.data();
        PaperRowData data = new PaperRowData();
        data.setCourseId(courseId);
        data.setQuestionNumber(requiredInteger(source.getQuestionNumber(), "题号", errors));
        data.setSortOrder(data.getQuestionNumber());
        data.setScore(requiredDecimal(source.getScore(), "分值", errors));
        QuestionRequestDTO question = new QuestionRequestDTO();
        fillQuestionRequest(question, questionColumns(source), courseId, errors);
        data.setQuestion(question);
        return item(row.rowNumber(), errors, data);
    }

    private ImportPreviewItem previewHomework(TabularRow<HomeworkImportRow> row) {
        List<String> errors = new ArrayList<>();
        HomeworkImportRow source = row.data();
        HomeworkRowData request = new HomeworkRowData();
        Long classId = parseLong(source.getClassId(), "教学班ID", errors);
        request.setClassId(toInteger(classId, "教学班ID", errors));
        request.setTitle(requiredText(source.getTitle(), "作业标题", errors));
        request.setContent(blankToNull(source.getContent()));
        request.setAttachmentUrl(blankToNull(source.getAttachmentUrl()));
        request.setStartTime(parseOptionalDateTime(source.getStartTime(), "开始时间", errors));
        request.setDeadline(parseDateTime(source.getDeadline(), "截止时间", errors));
        request.setFullScore(parseDecimal(source.getFullScore(), "总分", errors, BigDecimal.valueOf(100)));
        request.setStatus(parseHomeworkStatus(source.getStatus(), errors));
        request.setQuestionId(parseOptionalLong(source.getQuestionId(), "题目ID", errors));
        request.setQuestionNumber(parseInteger(source.getQuestionNumber(), "题号", errors, null));
        request.setScore(parseDecimal(source.getScore(), "题目分值", errors, BigDecimal.valueOf(5)));
        request.setSortOrder(parseInteger(source.getSortOrder(), "排序", errors, request.getQuestionNumber() == null ? 1 : request.getQuestionNumber()));
        applyHomeworkTeacherAndQuestion(source, classId, request, errors);
        return item(row.rowNumber(), errors, request);
    }

    private void fillQuestionRequest(QuestionRequestDTO request, QuestionColumns source, Long courseId, List<String> errors) {
        request.setCourseId(courseId);
        request.setType(parseQuestionType(source.type(), errors));
        request.setContent(requiredText(firstNonBlank(source.content(), source.stem()), "题面", errors));
        request.setDifficulty(parseDifficulty(source.difficulty(), errors));
        request.setKnowledgePoint(blankToNull(source.knowledgePoint()));
        request.setAnalysis(blankToNull(source.analysis()));
        request.setStatus(RecordStatus.ACTIVE);
        applyQuestionTeacher(courseId, request, errors);
        applyQuestionOptionsAndAnswers(source, request, errors);
    }

    private void applyQuestionOptionsAndAnswers(QuestionColumns source, QuestionRequestDTO request, List<String> errors) {
        boolean choiceType = isChoiceType(request.getType());
        List<QuestionRequestDTO.QuestionOptionRequest> options = parseOptions(source);
        if (request.getType() == QuestionType.TRUE_FALSE) {
            if (!options.isEmpty()) {
                errors.add("判断题不需要填写选项，系统会自动生成“正确”和“错误”");
            }
            options = trueFalseOptions();
        }
        if (choiceType && options.isEmpty()) {
            errors.add("选择题必须填写选项");
        }
        if ((request.getType() == QuestionType.SINGLE_CHOICE || request.getType() == QuestionType.MULTIPLE_CHOICE)
                && options.size() < 2) {
            errors.add("单选题和多选题至少需要两个选项");
        }
        if (!choiceType && !options.isEmpty()) {
            errors.add("非选择题不能填写选项");
        }
        request.setOptions(choiceType ? options : null);

        String rawAnswers = requiredText(firstNonBlank(source.answers(), source.correctAnswers(), source.singleChoiceAnswer()), "正确答案", errors);
        List<QuestionRequestDTO.QuestionAnswerRequest> answers = parseAnswers(rawAnswers, request.getType());
        request.setAnswers(answers);
        if (request.getType() == QuestionType.MULTIPLE_CHOICE && answers.size() < 2) {
            errors.add("多选题至少需要两个答案");
        }
        if ((request.getType() == QuestionType.SINGLE_CHOICE || request.getType() == QuestionType.TRUE_FALSE) && answers.size() != 1) {
            errors.add("单选题和判断题必须有且仅有一个答案");
        }
        validateChoiceAnswers(options, answers, request.getType(), errors);
    }

    private List<QuestionRequestDTO.QuestionOptionRequest> parseOptions(QuestionColumns source) {
        List<String> optionValues = List.of(
                value(source.optionA()),
                value(source.optionB()),
                value(source.optionC()),
                value(source.optionD()),
                value(source.optionE()),
                value(source.optionF())
        );
        List<QuestionRequestDTO.QuestionOptionRequest> options = new ArrayList<>();
        for (int i = 0; i < optionValues.size(); i++) {
            String optionValue = optionValues.get(i);
            if (!optionValue.isBlank()) {
                options.add(option(String.valueOf((char) ('A' + i)), optionValue, options.size() + 1));
            }
        }
        return options;
    }

    private QuestionRequestDTO.QuestionOptionRequest option(String key, String content, int sortOrder) {
        QuestionRequestDTO.QuestionOptionRequest option = new QuestionRequestDTO.QuestionOptionRequest();
        option.setOptionKey(key);
        option.setOptionContent(value(content));
        option.setSortOrder(sortOrder);
        return option;
    }

    private List<QuestionRequestDTO.QuestionOptionRequest> trueFalseOptions() {
        return List.of(option("A", "正确", 1), option("B", "错误", 2));
    }

    private List<QuestionRequestDTO.QuestionAnswerRequest> parseAnswers(String rawAnswers, QuestionType type) {
        if (rawAnswers == null || rawAnswers.isBlank()) {
            return List.of();
        }
        String[] parts = isChoiceType(type)
                ? rawAnswers.split("[,，;；、\\s]+")
                : new String[]{rawAnswers};
        List<QuestionRequestDTO.QuestionAnswerRequest> answers = new ArrayList<>();
        for (String part : parts) {
            String answer = part.trim();
            if (answer.isEmpty()) {
                continue;
            }
            if (type == QuestionType.TRUE_FALSE) {
                answer = switch (answer) {
                    case "正确" -> "A";
                    case "错误" -> "B";
                    default -> answer;
                };
            }
            QuestionRequestDTO.QuestionAnswerRequest item = new QuestionRequestDTO.QuestionAnswerRequest();
            item.setAnswerContent(answer);
            item.setSortOrder(answers.size() + 1);
            answers.add(item);
        }
        return answers;
    }

    private void validateChoiceAnswers(List<QuestionRequestDTO.QuestionOptionRequest> options,
                                       List<QuestionRequestDTO.QuestionAnswerRequest> answers,
                                       QuestionType type,
                                       List<String> errors) {
        if (!isChoiceType(type) || options == null || options.isEmpty() || answers == null || answers.isEmpty()) {
            return;
        }
        Set<String> optionKeys = new LinkedHashSet<>();
        for (QuestionRequestDTO.QuestionOptionRequest option : options) {
            optionKeys.add(value(option.getOptionKey()).toUpperCase(Locale.ROOT));
        }
        for (QuestionRequestDTO.QuestionAnswerRequest answer : answers) {
            String normalized = value(answer.getAnswerContent()).toUpperCase(Locale.ROOT);
            if (!optionKeys.contains(normalized)) {
                errors.add("答案必须对应已有选项: " + answer.getAnswerContent());
            }
        }
    }

    private void applyQuestionTeacher(Long courseId, QuestionRequestDTO request, List<String> errors) {
        if (courseId == null) {
            return;
        }
        try {
            var course = permissionService.getCourse(courseId.intValue());
            permissionService.requireCourseTeacher(course);
            request.setTeacherId(course.getTeacherId() == null ? null : course.getTeacherId().longValue());
        } catch (Exception e) {
            errors.add(e.getMessage());
        }
    }

    private void applyHomeworkTeacherAndQuestion(HomeworkImportRow source, Long classId, HomeworkRowData data, List<String> errors) {
        if (classId == null) {
            return;
        }
        try {
            TeachingClass teachingClass = permissionService.getTeachingClass(classId.intValue());
            permissionService.requireClassTeacher(teachingClass);
            data.setTeacherId(teachingClass.getTeacherId());
            Long courseId = teachingClass.getCourseId() == null ? null : teachingClass.getCourseId().longValue();
            QuestionColumns questionColumns = questionColumns(source);
            if (data.getQuestionId() != null || hasEmbeddedQuestion(questionColumns)) {
                applyQuestionReference(data, questionColumns, courseId, data.getScore(), errors);
            } else if (data.getContent() == null || data.getContent().isBlank()) {
                errors.add("作业内容或题面不能为空");
            }
        } catch (Exception e) {
            errors.add(e.getMessage());
        }
    }

    private void applyQuestionReference(QuestionCarrier data,
                                        QuestionColumns source,
                                        Long courseId,
                                        BigDecimal score,
                                        List<String> errors) {
        if (data.getQuestionId() != null) {
            validateReferencedQuestion(data, courseId, errors);
            return;
        }
        if (!hasEmbeddedQuestion(source)) {
            errors.add("题目ID或题面不能为空");
            return;
        }
        QuestionRequestDTO request = new QuestionRequestDTO();
        fillQuestionRequest(request, source, courseId, errors);
        data.setQuestion(request);
        if (errors.isEmpty()) {
            data.setExistingQuestionId(findSameQuestionId(request));
        }
    }

    private void validateReferencedQuestion(QuestionCarrier data, Long courseId, List<String> errors) {
        try {
            Question question = questionService.getEntityById(data.getQuestionId());
            if (!Objects.equals(question.getCourseId(), toInteger(courseId, "课程ID", errors))) {
                errors.add("题目不属于当前课程");
            }
            Long teacherId = data.getQuestionTeacherId();
            if (teacherId != null && !Objects.equals(question.getTeacherId(), teacherId.intValue())) {
                errors.add("题目不属于当前教师");
            }
        } catch (Exception e) {
            errors.add(e.getMessage());
        }
    }

    private ImportPreviewItem item(Integer rowNumber, List<String> errors, Object normalizedData) {
        return errors.isEmpty()
                ? ImportPreviewItem.valid(rowNumber, normalizedData)
                : ImportPreviewItem.invalid(rowNumber, errors, normalizedData);
    }

    private List<QuestionImportRow> exportQuestions(Map<String, String> params) {
        Page<QuestionResponseDTO> page = questionService.list(
                parseQuestionTypeFilter(firstParam(params, "type", "questionType")),
                parseDifficultyFilter(params.get("difficulty")),
                parseRecordStatusFilter(params.get("status")),
                parseOptionalLongParam(params.get("courseId"), "课程ID"),
                parseOptionalLongParam(params.get("teacherId"), "教师ID"),
                blankToNull(params.get("keyword")),
                0,
                exportSize(params)
        );
        return page.getContent().stream().map(this::toQuestionImportRow).toList();
    }

    private List<PaperExportRow> exportPapers(Map<String, String> params) {
        Long teacherId = parseOptionalLongParam(params.get("teacherId"), "教师ID");
        Long courseId = parseOptionalLongParam(params.get("courseId"), "课程ID");
        String keyword = blankToNull(params.get("keyword"));
        PaperStatus status = parsePaperStatusFilter(params.get("status"));
        return paperService.list(teacherId, keyword, courseId).stream()
                .filter(paper -> courseId == null || Objects.equals(paper.getCourseId(), courseId))
                .filter(paper -> status == null || paper.getStatus() == status)
                .flatMap(paper -> toPaperImportRows(paper).stream())
                .toList();
    }

    private List<ExamExportRow> exportExams(Map<String, String> params) {
        Long teacherId = parseOptionalLongParam(params.get("teacherId"), "教师ID");
        Long classId = parseOptionalLongParam(params.get("classId"), "教学班ID");
        Long paperId = parseOptionalLongParam(params.get("paperId"), "试卷ID");
        String keyword = blankToNull(params.get("keyword"));
        ExamStatus status = parseExamStatusFilter(params.get("status"));
        return examService.list(teacherId, keyword, classId).stream()
                .filter(exam -> classId == null || Objects.equals(exam.getClassId(), classId))
                .filter(exam -> paperId == null || Objects.equals(exam.getPaperId(), paperId))
                .filter(exam -> status == null || exam.getStatus() == status)
                .map(this::toExamImportRow)
                .toList();
    }

    private List<HomeworkImportRow> exportHomeworks(Map<String, String> params) {
        Integer classId = parseOptionalIntegerParam(params.get("classId"), "教学班ID");
        Integer teacherId = parseOptionalIntegerParam(params.get("teacherId"), "教师ID");
        String title = blankToNull(params.get("title"));
        Integer status = parseHomeworkStatusFilter(params.get("status"));
        return homeworkService.listForExport(classId, teacherId, title, status).stream()
                .map(this::toHomeworkImportRow)
                .toList();
    }

    private QuestionImportRow toQuestionImportRow(QuestionResponseDTO question) {
        QuestionImportRow row = new QuestionImportRow();
        row.setCourseId(toText(question.getCourseId()));
        row.setType(questionTypeLabel(question.getType()));
        row.setContent(question.getContent());
        row.setDifficulty(difficultyLabel(question.getDifficulty()));
        row.setKnowledgePoint(question.getKnowledgePoint());
        row.setAnalysis(question.getAnalysis());
        fillOptionColumns(row, question.getOptions());
        row.setAnswers(joinAnswers(question.getAnswers()));
        return row;
    }

    private List<PaperExportRow> toPaperImportRows(PaperResponseDTO paper) {
        List<PaperResponseDTO.PaperQuestionResponse> paperQuestions = paper.getPaperQuestions() == null
                ? List.of()
                : paper.getPaperQuestions();
        if (paperQuestions.isEmpty()) {
            PaperExportRow row = new PaperExportRow();
            fillPaperBaseColumns(row, paper);
            return List.of(row);
        }
        List<PaperExportRow> rows = new ArrayList<>();
        for (PaperResponseDTO.PaperQuestionResponse question : paperQuestions) {
            PaperExportRow row = new PaperExportRow();
            fillPaperBaseColumns(row, paper);
            row.setQuestionId(toText(question.getQuestionId()));
            row.setQuestionNumber(toText(question.getSortOrder()));
            row.setContent(question.getQuestionContent());
            row.setScore(toText(question.getScore()));
            row.setSortOrder(toText(question.getSortOrder()));
            rows.add(row);
        }
        return rows;
    }

    private void fillPaperBaseColumns(PaperExportRow row, PaperResponseDTO paper) {
        row.setName(paper.getName());
        row.setCourseId(toText(paper.getCourseId()));
        row.setDurationMinutes(toText(paper.getDurationMinutes()));
        row.setStatus(paperStatusLabel(paper.getStatus()));
    }

    private ExamExportRow toExamImportRow(ExamResponseDTO exam) {
        ExamExportRow row = new ExamExportRow();
        row.setName(exam.getName());
        row.setClassId(toText(exam.getClassId()));
        row.setPaperId(toText(exam.getPaperId()));
        row.setPaperName(exam.getPaperName());
        row.setStartTime(formatDateTime(exam.getStartTime()));
        row.setEndTime(formatDateTime(exam.getEndTime()));
        row.setDurationMinutes(toText(exam.getDurationMinutes()));
        row.setStatus(examStatusLabel(exam.getStatus()));
        row.setDescription(exam.getDescription());
        return row;
    }

    private HomeworkImportRow toHomeworkImportRow(Homework homework) {
        HomeworkImportRow row = new HomeworkImportRow();
        row.setClassId(toText(homework.getClassId()));
        row.setTitle(homework.getTitle());
        row.setContent(homework.getContent());
        row.setAttachmentUrl(homework.getAttachmentUrl());
        row.setStartTime(formatDateTime(homework.getStartTime()));
        row.setDeadline(formatDateTime(homework.getDeadline()));
        row.setFullScore(toText(homework.getFullScore()));
        row.setStatus(homeworkStatusLabel(homework.getStatus()));
        return row;
    }

    private void fillOptionColumns(QuestionImportRow row, List<QuestionResponseDTO.OptionResponse> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        List<QuestionResponseDTO.OptionResponse> sorted = options.stream()
                .sorted(Comparator.comparing(option -> option.getSortOrder() == null ? Integer.MAX_VALUE : option.getSortOrder()))
                .toList();
        for (QuestionResponseDTO.OptionResponse option : sorted) {
            String key = value(option.getOptionKey()).toUpperCase(Locale.ROOT);
            switch (key) {
                case "A" -> row.setOptionA(option.getOptionContent());
                case "B" -> row.setOptionB(option.getOptionContent());
                case "C" -> row.setOptionC(option.getOptionContent());
                case "D" -> row.setOptionD(option.getOptionContent());
                case "E" -> row.setOptionE(option.getOptionContent());
                case "F" -> row.setOptionF(option.getOptionContent());
                default -> {
                }
            }
        }
    }

    private String joinAnswers(List<QuestionResponseDTO.AnswerResponse> answers) {
        if (answers == null || answers.isEmpty()) {
            return null;
        }
        return answers.stream()
                .sorted(Comparator.comparing(answer -> answer.getSortOrder() == null ? Integer.MAX_VALUE : answer.getSortOrder()))
                .map(QuestionResponseDTO.AnswerResponse::getAnswerContent)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("; "));
    }

    private String normalType(String type) {
        return value(type).toLowerCase(Locale.ROOT);
    }

    private int exportSize(Map<String, String> params) {
        Integer size = parseOptionalIntegerParam(params.get("size"), "导出条数");
        if (size == null || size <= 0) {
            return 10000;
        }
        return Math.min(size, 50000);
    }

    private String firstParam(Map<String, String> params, String... names) {
        for (String name : names) {
            String value = params.get(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Long parseOptionalLongParam(String raw, String fieldName) {
        String value = value(raw);
        if (value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new BusinessException(fieldName + "必须是整数");
        }
    }

    private Integer parseOptionalIntegerParam(String raw, String fieldName) {
        String value = value(raw);
        if (value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new BusinessException(fieldName + "必须是整数");
        }
    }

    private QuestionType parseQuestionTypeFilter(String raw) {
        String value = value(raw);
        if (value.isBlank()) {
            return null;
        }
        List<String> errors = new ArrayList<>();
        QuestionType type = parseQuestionType(value, errors);
        if (!errors.isEmpty()) {
            throw new BusinessException(errors.get(0));
        }
        return type;
    }

    private Difficulty parseDifficultyFilter(String raw) {
        String value = value(raw);
        if (value.isBlank()) {
            return null;
        }
        List<String> errors = new ArrayList<>();
        Difficulty difficulty = parseDifficulty(value, errors);
        if (!errors.isEmpty()) {
            throw new BusinessException(errors.get(0));
        }
        return difficulty;
    }

    private RecordStatus parseRecordStatusFilter(String raw) {
        String value = value(raw);
        if (value.isBlank()) {
            return null;
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "DRAFT", "草稿", "0" -> RecordStatus.DRAFT;
            case "ACTIVE", "启用", "正常", "1" -> RecordStatus.ACTIVE;
            case "DISABLED", "停用", "禁用", "2" -> RecordStatus.DISABLED;
            default -> throw new BusinessException("题目状态不合法: " + raw);
        };
    }

    private PaperStatus parsePaperStatusFilter(String raw) {
        String value = value(raw);
        if (value.isBlank()) {
            return null;
        }
        List<String> errors = new ArrayList<>();
        PaperStatus status = parsePaperStatus(value, errors);
        if (!errors.isEmpty()) {
            throw new BusinessException(errors.get(0));
        }
        return status;
    }

    private ExamStatus parseExamStatusFilter(String raw) {
        String value = value(raw);
        if (value.isBlank()) {
            return null;
        }
        List<String> errors = new ArrayList<>();
        ExamStatus status = parseExamStatus(value, errors);
        if (!errors.isEmpty()) {
            throw new BusinessException(errors.get(0));
        }
        return status;
    }

    private Integer parseHomeworkStatusFilter(String raw) {
        String value = value(raw);
        if (value.isBlank()) {
            return null;
        }
        List<String> errors = new ArrayList<>();
        Integer status = parseHomeworkStatus(value, errors);
        if (!errors.isEmpty()) {
            throw new BusinessException(errors.get(0));
        }
        return status;
    }

    private QuestionType parseQuestionType(String raw, List<String> errors) {
        String value = value(raw);
        if (value.isBlank()) {
            errors.add("题型不能为空");
            return QuestionType.SHORT_ANSWER;
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "SINGLE_CHOICE", "SINGLE", "单选", "单选题" -> QuestionType.SINGLE_CHOICE;
            case "MULTIPLE_CHOICE", "MULTIPLE", "多选", "多选题" -> QuestionType.MULTIPLE_CHOICE;
            case "TRUE_FALSE", "JUDGE", "判断", "判断题" -> QuestionType.TRUE_FALSE;
            case "FILL_BLANK", "BLANK", "填空", "填空题" -> QuestionType.FILL_BLANK;
            case "SHORT_ANSWER", "SHORT", "简答", "简答题" -> QuestionType.SHORT_ANSWER;
            case "FILE_UPLOAD", "FILE", "文件上传", "文件上传题" -> QuestionType.FILE_UPLOAD;
            default -> {
                errors.add("题型不合法: " + raw);
                yield QuestionType.SHORT_ANSWER;
            }
        };
    }

    private Difficulty parseDifficulty(String raw, List<String> errors) {
        String value = value(raw);
        if (value.isBlank()) {
            return Difficulty.MEDIUM;
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "EASY", "简单", "易", "1" -> Difficulty.EASY;
            case "MEDIUM", "中等", "中", "2" -> Difficulty.MEDIUM;
            case "HARD", "困难", "难", "3" -> Difficulty.HARD;
            default -> {
                errors.add("难度不合法: " + raw);
                yield Difficulty.MEDIUM;
            }
        };
    }

    private PaperStatus parsePaperStatus(String raw, List<String> errors) {
        String value = value(raw);
        if (value.isBlank()) {
            return PaperStatus.DRAFT;
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "DRAFT", "草稿", "0" -> PaperStatus.DRAFT;
            case "PUBLISHED", "启用", "已发布", "发布", "1" -> PaperStatus.PUBLISHED;
            case "ARCHIVED", "归档", "2" -> PaperStatus.ARCHIVED;
            default -> {
                errors.add("试卷状态不合法: " + raw);
                yield PaperStatus.DRAFT;
            }
        };
    }

    private ExamStatus parseExamStatus(String raw, List<String> errors) {
        String value = value(raw);
        if (value.isBlank()) {
            return ExamStatus.DRAFT;
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "DRAFT", "未开始", "草稿", "0" -> ExamStatus.DRAFT;
            case "PUBLISHED", "进行中", "发布", "已发布", "1" -> ExamStatus.PUBLISHED;
            case "CLOSED", "已结束", "关闭", "3" -> ExamStatus.CLOSED;
            default -> {
                errors.add("考试状态不合法: " + raw);
                yield ExamStatus.DRAFT;
            }
        };
    }

    private Integer parseHomeworkStatus(String raw, List<String> errors) {
        String value = value(raw);
        if (value.isBlank()) {
            return 1;
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "1", "进行中", "启用", "发布" -> 1;
            case "0", "已结束", "结束", "关闭", "停用" -> 0;
            default -> {
                errors.add("作业状态不合法: " + raw);
                yield 1;
            }
        };
    }

    private String questionTypeLabel(QuestionType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case SINGLE_CHOICE -> "单选题";
            case MULTIPLE_CHOICE -> "多选题";
            case TRUE_FALSE -> "判断题";
            case FILL_BLANK -> "填空题";
            case SHORT_ANSWER -> "简答题";
            case FILE_UPLOAD -> "文件上传题";
        };
    }

    private String difficultyLabel(Difficulty difficulty) {
        if (difficulty == null) {
            return null;
        }
        return switch (difficulty) {
            case EASY -> "简单";
            case MEDIUM -> "中等";
            case HARD -> "困难";
        };
    }

    private String paperStatusLabel(PaperStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case DRAFT -> "草稿";
            case PUBLISHED -> "已发布";
            case ARCHIVED -> "归档";
        };
    }

    private String examStatusLabel(ExamStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case DRAFT -> "未开始";
            case PUBLISHED -> "进行中";
            case CLOSED -> "已结束";
            case RESULTS_SUBMITTED -> "成绩已提交";
        };
    }

    private String homeworkStatusLabel(Integer status) {
        return status != null && status == 0 ? "已结束" : "进行中";
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTERS.get(0));
    }

    private String toText(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(raw);
    }

    private String requiredText(String raw, String fieldName, List<String> errors) {
        String value = value(raw);
        if (value.isBlank()) {
            errors.add(fieldName + "不能为空");
        }
        return value;
    }

    private Long parseLong(String raw, String fieldName, List<String> errors) {
        String value = requiredText(raw, fieldName, errors);
        if (value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            errors.add(fieldName + "必须是整数");
            return null;
        }
    }

    private Long parseOptionalLong(String raw, String fieldName, List<String> errors) {
        String value = value(raw);
        if (value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            errors.add(fieldName + "必须是整数");
            return null;
        }
    }

    private Integer parseInteger(String raw, String fieldName, List<String> errors, Integer defaultValue) {
        String value = value(raw);
        if (value.isBlank()) {
            return defaultValue;
        }
        try {
            return new BigDecimal(value).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            errors.add(fieldName + "必须是整数");
            return defaultValue;
        }
    }

    private Integer requiredInteger(String raw, String fieldName, List<String> errors) {
        String value = requiredText(raw, fieldName, errors);
        if (value.isBlank()) {
            return null;
        }
        return parseInteger(value, fieldName, errors, null);
    }

    private BigDecimal parseDecimal(String raw, String fieldName, List<String> errors, BigDecimal defaultValue) {
        String value = value(raw);
        if (value.isBlank()) {
            return defaultValue;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            errors.add(fieldName + "必须是数字");
            return defaultValue;
        }
    }

    private BigDecimal requiredDecimal(String raw, String fieldName, List<String> errors) {
        String value = requiredText(raw, fieldName, errors);
        if (value.isBlank()) {
            return null;
        }
        BigDecimal parsed = parseDecimal(value, fieldName, errors, null);
        if (parsed != null && parsed.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(fieldName + "必须大于0");
        }
        return parsed;
    }

    private LocalDateTime parseDateTime(String raw, String fieldName, List<String> errors) {
        String value = requiredText(raw, fieldName, errors);
        if (value.isBlank()) {
            return null;
        }
        LocalDateTime parsed = tryParseDateTime(value);
        if (parsed == null) {
            errors.add(fieldName + "格式应为yyyy-MM-dd HH:mm");
        }
        return parsed;
    }

    private LocalDateTime parseOptionalDateTime(String raw, String fieldName, List<String> errors) {
        String value = value(raw);
        if (value.isBlank()) {
            return null;
        }
        LocalDateTime parsed = tryParseDateTime(value);
        if (parsed == null) {
            errors.add(fieldName + "格式应为yyyy-MM-dd HH:mm");
        }
        return parsed;
    }

    private LocalDateTime tryParseDateTime(String value) {
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(value, formatter).atTime(LocalTime.MIN);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private Integer toInteger(Long value, String fieldName, List<String> errors) {
        if (value == null) {
            return null;
        }
        if (value > Integer.MAX_VALUE) {
            errors.add(fieldName + "超出范围");
            return null;
        }
        return value.intValue();
    }

    private String blankToNull(String raw) {
        String value = value(raw);
        return value.isBlank() ? null : value;
    }

    private String value(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String item : values) {
            String value = value(item);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private boolean isChoiceType(QuestionType type) {
        return type == QuestionType.SINGLE_CHOICE
                || type == QuestionType.MULTIPLE_CHOICE
                || type == QuestionType.TRUE_FALSE;
    }

    private boolean hasEmbeddedQuestion(QuestionColumns source) {
        return !firstNonBlank(source.type(), source.content(), source.stem()).isBlank();
    }

    private Long findSameQuestionId(QuestionRequestDTO request) {
        Question question = questionService.findSameQuestion(request);
        return question == null || question.getId() == null ? null : question.getId().longValue();
    }

    private Long ensureQuestionId(QuestionCarrier row) {
        if (row.getQuestionId() != null) {
            return row.getQuestionId();
        }
        if (row.getExistingQuestionId() != null) {
            return row.getExistingQuestionId();
        }
        if (row.getQuestion() == null) {
            throw new BusinessException("题目数据不能为空");
        }
        Long existingQuestionId = findSameQuestionId(row.getQuestion());
        if (existingQuestionId != null) {
            row.setExistingQuestionId(existingQuestionId);
            return existingQuestionId;
        }
        QuestionResponseDTO response = questionService.create(row.getQuestion());
        row.setExistingQuestionId(response.getId());
        return response.getId();
    }

    private QuestionColumns questionColumns(QuestionImportRow source) {
        return new QuestionColumns(
                source.getType(),
                source.getContent(),
                null,
                source.getDifficulty(),
                source.getKnowledgePoint(),
                source.getAnalysis(),
                null,
                null,
                source.getOptionA(),
                source.getOptionB(),
                source.getOptionC(),
                source.getOptionD(),
                source.getOptionE(),
                source.getOptionF(),
                source.getAnswers(),
                null,
                null
        );
    }

    private QuestionColumns questionColumns(PaperImportRow source) {
        return new QuestionColumns(
                source.getType(),
                source.getContent(),
                null,
                source.getDifficulty(),
                source.getKnowledgePoint(),
                source.getAnalysis(),
                null,
                null,
                source.getOptionA(),
                source.getOptionB(),
                source.getOptionC(),
                source.getOptionD(),
                source.getOptionE(),
                source.getOptionF(),
                source.getAnswers(),
                null,
                null
        );
    }

    private QuestionColumns questionColumns(HomeworkImportRow source) {
        return new QuestionColumns(
                source.getType(),
                source.getQuestionContent(),
                source.getStem(),
                source.getDifficulty(),
                source.getKnowledgePoint(),
                source.getAnalysis(),
                source.getOptions(),
                source.getSingleChoiceOptions(),
                source.getOptionA(),
                source.getOptionB(),
                source.getOptionC(),
                source.getOptionD(),
                source.getOptionE(),
                source.getOptionF(),
                source.getAnswers(),
                source.getCorrectAnswers(),
                source.getSingleChoiceAnswer()
        );
    }

    private <T> T convert(Object source, Class<T> targetType) {
        return objectMapper.convertValue(source, targetType);
    }

    private record QuestionColumns(
            String type,
            String content,
            String stem,
            String difficulty,
            String knowledgePoint,
            String analysis,
            String options,
            String singleChoiceOptions,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            String optionE,
            String optionF,
            String answers,
            String correctAnswers,
            String singleChoiceAnswer
    ) {
    }

    private interface QuestionCarrier {
        Long getQuestionId();

        QuestionRequestDTO getQuestion();

        void setQuestion(QuestionRequestDTO question);

        Long getExistingQuestionId();

        void setExistingQuestionId(Long existingQuestionId);

        Long getQuestionTeacherId();
    }

    @FunctionalInterface
    private interface QuestionIdResolver {
        Long resolve(QuestionCarrier row);
    }

    @lombok.Data
    public static class PaperRowData {
        private Long courseId;
        private Integer questionNumber;
        private QuestionRequestDTO question;
        private BigDecimal score;
        private Integer sortOrder;
    }

    @lombok.Data
    public static class HomeworkRowData implements QuestionCarrier {
        private Integer classId;
        private Integer teacherId;
        private String title;
        private String content;
        private String attachmentUrl;
        private LocalDateTime startTime;
        private LocalDateTime deadline;
        private BigDecimal fullScore;
        private Integer status;
        private Long questionId;
        private Integer questionNumber;
        private QuestionRequestDTO question;
        private Long existingQuestionId;
        private BigDecimal score;
        private Integer sortOrder;

        @Override
        public Long getQuestionTeacherId() {
            return teacherId == null ? null : teacherId.longValue();
        }
    }

    private record HomeworkGroup(Integer rowNumber, HomeworkRowData firstRow, List<HomeworkRowData> rows) {
        private HomeworkGroup(Integer rowNumber, HomeworkRowData firstRow) {
            this(rowNumber, firstRow, new ArrayList<>());
        }

        private void add(HomeworkRowData row) {
            rows.add(row);
        }

        private HomeworkDto toRequest(QuestionIdResolver questionIdResolver) {
            validateConsistentRows();
            for (HomeworkRowData row : rows) {
                if (hasQuestion(row)) {
                    questionIdResolver.resolve(row);
                }
            }
            HomeworkDto request = new HomeworkDto();
            request.setClassId(firstRow.getClassId());
            request.setTeacherId(firstRow.getTeacherId());
            request.setTitle(firstRow.getTitle());
            request.setContent(renderContent());
            request.setAttachmentUrl(firstRow.getAttachmentUrl());
            request.setStartTime(firstRow.getStartTime());
            request.setDeadline(firstRow.getDeadline());
            request.setFullScore(firstRow.getFullScore());
            request.setStatus(firstRow.getStatus());
            return request;
        }

        private String renderContent() {
            StringBuilder content = new StringBuilder();
            if (firstRow.getContent() != null && !firstRow.getContent().isBlank()) {
                content.append(firstRow.getContent().trim());
            }
            List<HomeworkRowData> questionRows = rows.stream()
                    .filter(this::hasQuestion)
                    .sorted(Comparator.comparing(row -> row.getSortOrder() == null ? Integer.MAX_VALUE : row.getSortOrder()))
                    .toList();
            if (!questionRows.isEmpty()) {
                if (content.length() > 0) {
                    content.append("\n\n");
                }
                content.append("题目清单：");
                for (HomeworkRowData row : questionRows) {
                    content.append("\n").append(row.getQuestionNumber() == null ? row.getSortOrder() : row.getQuestionNumber()).append(". ");
                    if (row.getQuestion() != null) {
                        content.append(row.getQuestion().getContent());
                        appendOptions(content, row.getQuestion());
                    } else {
                        content.append("题目ID：").append(row.getQuestionId());
                    }
                    if (row.getScore() != null) {
                        content.append("（").append(row.getScore().stripTrailingZeros().toPlainString()).append("分）");
                    }
                }
            }
            return content.toString();
        }

        private void appendOptions(StringBuilder content, QuestionRequestDTO question) {
            if (question.getOptions() == null || question.getOptions().isEmpty()) {
                return;
            }
            for (QuestionRequestDTO.QuestionOptionRequest option : question.getOptions()) {
                content.append("\n   ")
                        .append(option.getOptionKey())
                        .append(". ")
                        .append(option.getOptionContent());
            }
        }

        private boolean hasQuestion(HomeworkRowData row) {
            return row.getQuestionId() != null || row.getQuestion() != null;
        }

        private void validateConsistentRows() {
            for (HomeworkRowData row : rows) {
                if (!Objects.equals(firstRow.getClassId(), row.getClassId())
                        || !Objects.equals(firstRow.getTitle(), row.getTitle())
                        || !Objects.equals(firstRow.getAttachmentUrl(), row.getAttachmentUrl())
                        || !Objects.equals(firstRow.getStartTime(), row.getStartTime())
                        || !Objects.equals(firstRow.getDeadline(), row.getDeadline())
                        || !Objects.equals(firstRow.getFullScore(), row.getFullScore())
                        || !Objects.equals(firstRow.getStatus(), row.getStatus())) {
                    throw new BusinessException("同一作业标题下教学班、时间、总分、状态必须一致");
                }
            }
            if ((firstRow.getContent() == null || firstRow.getContent().isBlank())
                    && rows.stream().noneMatch(this::hasQuestion)) {
                throw new BusinessException("作业内容或题目不能为空");
            }
        }
    }
}
