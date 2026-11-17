package com.cupk.aiacademy.analysis;

import com.cupk.aiacademy.common.BusinessException;
import com.cupk.aiacademy.service.PermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class LearningAnalysisTemplateService {
    public static final Map<String,String> SECTIONS;
    private static final Pattern MARKER = Pattern.compile("\\{\\{([^{}]+)}}");
    private static final Set<String> METADATA = Set.of("course_name", "semester", "generated_at");
    static {
        var sections = new LinkedHashMap<String,String>();
        sections.put("course_overview", "课程与教学班概况");
        sections.put("homework_analysis", "作业完成与学习表现");
        sections.put("exam_analysis", "考试成绩与阶段变化");
        sections.put("class_comparison", "教学班差异分析");
        sections.put("main_findings", "主要问题");
        sections.put("teaching_suggestions", "教学改进建议");
        SECTIONS = Collections.unmodifiableMap(sections);
    }
    public record TemplateInfo(String id, String name, List<String> fields) {}
    private final JdbcTemplate db;
    private final PermissionService permissions;
    private final ObjectMapper json;

    public LearningAnalysisTemplateService(JdbcTemplate db, PermissionService permissions, ObjectMapper json,
                                           LearningAnalysisSchema schema) {
        this.db = db; this.permissions = permissions; this.json = json;
    }

    public List<TemplateInfo> list() {
        permissions.requireTeacherOrAdmin();
        List<TemplateInfo> templates = new ArrayList<>();
        templates.add(new TemplateInfo("default", "课程学情分析报告默认模板", new ArrayList<>(SECTIONS.keySet())));
        templates.addAll(db.query("SELECT id,name,fields_json FROM learning_analysis_template WHERE owner_id=? ORDER BY created_at DESC",
                (row, index) -> new TemplateInfo(row.getString("id"), row.getString("name"), readFields(row.getString("fields_json"))),
                permissions.currentUser().getUserId()));
        return templates;
    }

    public TemplateInfo upload(String filename, byte[] bytes) {
        permissions.requireTeacherOrAdmin();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".docx")) throw new BusinessException("请上传.docx格式的Word模板");
        if (bytes.length > 5 * 1024 * 1024) throw new BusinessException("模板不能超过5MB");
        List<String> fields = fields(bytes);
        String id = UUID.randomUUID().toString();
        String name = filename.replaceAll("[\\\\/\\r\\n]", "_");
        if (name.length() > 200) name = name.substring(name.length() - 200);
        db.update("INSERT INTO learning_analysis_template(id,owner_id,name,template_bytes,fields_json) VALUES(?,?,?,?,?)",
                id, permissions.currentUser().getUserId(), name, bytes, writeJson(fields));
        return new TemplateInfo(id, name, fields);
    }

    public byte[] bytes(String id) {
        permissions.requireTeacherOrAdmin();
        if ("default".equals(id)) return defaultTemplate();
        var rows = db.queryForList("SELECT template_bytes FROM learning_analysis_template WHERE id=? AND owner_id=?", id, permissions.currentUser().getUserId());
        if (rows.isEmpty()) throw new BusinessException("模板不存在或无权访问");
        return (byte[]) rows.getFirst().get("template_bytes");
    }

    public List<String> fields(byte[] bytes) {
        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            Set<String> fields = new LinkedHashSet<>();
            for (var paragraph : paragraphs(document)) {
                var matcher = MARKER.matcher(paragraph.getText());
                while (matcher.find()) {
                    String field = matcher.group(1);
                    if (!SECTIONS.containsKey(field) && !METADATA.contains(field)) throw new BusinessException("模板包含未知占位符 " + field);
                    fields.add(field);
                }
            }
            if (fields.stream().noneMatch(SECTIONS::containsKey)) throw new BusinessException("模板需要至少一个正文占位符，请下载默认模板后修改");
            return List.copyOf(fields);
        } catch (BusinessException error) { throw error; }
        catch (Exception error) { throw new BusinessException("Word模板无法读取，请检查文件格式或重新保存为.docx"); }
    }

    public byte[] render(byte[] template, Map<String,String> values) {
        try (var document = new XWPFDocument(new ByteArrayInputStream(template)); var output = new ByteArrayOutputStream()) {
            for (var paragraph : paragraphs(document)) replace(paragraph, values);
            document.write(output);
            return output.toByteArray();
        } catch (Exception error) { throw new BusinessException("Word报告导出失败"); }
    }

    public String outline(byte[] template) {
        try (var document = new XWPFDocument(new ByteArrayInputStream(template))) {
            return paragraphs(document).stream().map(XWPFParagraph::getText).reduce((a,b) -> a + "\n" + b).orElse("");
        } catch (IOException error) { throw new BusinessException("模板结构读取失败"); }
    }

    private void replace(XWPFParagraph paragraph, Map<String,String> values) {
        List<XWPFRun> runs = paragraph.getRuns();
        String text = runs.stream().map(XWPFRun::text).reduce("", String::concat);
        var matches = MARKER.matcher(text).results().toList();
        for (int markerIndex = matches.size() - 1; markerIndex >= 0; markerIndex--) {
            var marker = matches.get(markerIndex);
            String replacement = values.getOrDefault(marker.group(1), "");
            int offset = 0;
            for (var run : runs) {
                String original = run.text();
                int start = Math.max(0, marker.start() - offset);
                int end = Math.min(original.length(), marker.end() - offset);
                if (start < end) {
                    String inserted = marker.start() >= offset ? replacement : "";
                    setRunText(run, original.substring(0, start) + inserted + original.substring(end));
                }
                offset += original.length();
            }
        }
    }

    private void setRunText(XWPFRun run, String value) {
        run.getCTR().getTList().clear();
        run.getCTR().getBrList().clear();
        String[] lines = value.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) run.addBreak();
            run.setText(lines[i]);
        }
    }

    private List<XWPFParagraph> paragraphs(XWPFDocument document) {
        List<XWPFParagraph> result = new ArrayList<>();
        collect(document, result);
        document.getHeaderList().forEach(header -> collect(header, result));
        document.getFooterList().forEach(footer -> collect(footer, result));
        return result;
    }

    private void collect(IBody body, List<XWPFParagraph> paragraphs) {
        for (IBodyElement element : body.getBodyElements()) {
            if (element instanceof XWPFParagraph paragraph) paragraphs.add(paragraph);
            if (element instanceof XWPFTable table)
                for (var row : table.getRows()) for (var cell : row.getTableCells()) collect(cell, paragraphs);
        }
    }

    public byte[] defaultTemplate() {
        try (var document = new XWPFDocument(); var output = new ByteArrayOutputStream()) {
            var section = document.getDocument().getBody().addNewSectPr();
            var size = section.addNewPgSz(); size.setW(BigInteger.valueOf(11906)); size.setH(BigInteger.valueOf(16838));
            var margins = section.addNewPgMar();
            margins.setLeft(BigInteger.valueOf(1440)); margins.setRight(BigInteger.valueOf(1440));
            margins.setTop(BigInteger.valueOf(1440)); margins.setBottom(BigInteger.valueOf(1440));
            var title = document.createParagraph(); title.setStyle("Title"); title.setAlignment(ParagraphAlignment.CENTER);
            var titleRun = title.createRun(); titleRun.setText("课程学情分析报告"); titleRun.setFontFamily("黑体"); titleRun.setFontSize(20); titleRun.setColor("000000");
            addParagraph(document, "课程  {{course_name}}", false);
            addParagraph(document, "学期  {{semester}}", false);
            addParagraph(document, "数据截止时间  {{generated_at}}", false);
            for (var entry : SECTIONS.entrySet()) {
                addParagraph(document, entry.getValue(), true);
                addParagraph(document, "{{" + entry.getKey() + "}}", false);
            }
            document.write(output);
            return output.toByteArray();
        } catch (IOException error) { throw new BusinessException("默认模板生成失败"); }
    }

    private void addParagraph(XWPFDocument document, String text, boolean heading) {
        var paragraph = document.createParagraph();
        paragraph.setSpacingAfter(120); paragraph.setSpacingBetween(1.5);
        if (heading) { paragraph.setStyle("Heading1"); paragraph.setKeepNext(true); }
        var run = paragraph.createRun(); run.setText(text); run.setFontFamily(heading ? "黑体" : "宋体");
        run.setFontSize(heading ? 14 : 12); run.setColor("000000"); run.setBold(heading);
    }

    private List<String> readFields(String value) {
        try { return json.readValue(value, json.getTypeFactory().constructCollectionType(List.class, String.class)); }
        catch (Exception error) { throw new BusinessException("模板元数据读取失败"); }
    }
    private String writeJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception error) { throw new BusinessException("模板元数据保存失败"); }
    }
}
