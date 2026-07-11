package com.cupk.aiacademy.service.imports;

import com.cupk.aiacademy.common.BusinessException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddressList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public final class ImportTemplateSupport {
    public static final String QUESTION_SHEET = "题目列表";
    private static final int EXAMPLE_ROW = 1;
    private static final int VALIDATION_LAST_ROW = 500;

    private ImportTemplateSupport() {
    }

    public static byte[] questionTemplate() {
        return createTemplate("题库导入说明", questionHeaders(), questionExample(), questionInstructions());
    }

    public static byte[] paperTemplate() {
        return createTemplate("试卷导入说明", paperHeaders(), paperExample(), paperInstructions());
    }

    private static byte[] createTemplate(String guideTitle,
                                         List<String> headers,
                                         List<String> example,
                                         List<String> instructions) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet guide = workbook.createSheet("填写说明");
            writeGuide(guide, guideTitle, instructions);
            Sheet questions = workbook.createSheet(QUESTION_SHEET);
            writeQuestionSheet(workbook, questions, headers, example);
            workbook.setActiveSheet(0);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("生成导入模板失败");
        }
    }

    private static void writeGuide(Sheet guide, String title, List<String> instructions) {
        CellStyle titleStyle = guide.getWorkbook().createCellStyle();
        Font titleFont = guide.getWorkbook().createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);

        Row titleRow = guide.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(titleStyle);
        for (int i = 0; i < instructions.size(); i++) {
            guide.createRow(i + 2).createCell(0).setCellValue(instructions.get(i));
        }
        guide.setColumnWidth(0, 15000);
    }

    private static void writeQuestionSheet(Workbook workbook, Sheet sheet, List<String> headers, List<String> example) {
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, columnWidth(headers.get(i)));
        }
        Row exampleRow = sheet.createRow(EXAMPLE_ROW);
        for (int i = 0; i < example.size(); i++) {
            exampleRow.createCell(i).setCellValue(example.get(i));
        }
        sheet.createFreezePane(0, 1);
        addListValidation(sheet, 1, new String[]{"单选题", "多选题", "判断题", "填空题", "简答题", "文件上传题"});
        addListValidation(sheet, headers.size() - 3, new String[]{"简单", "中等", "困难"});
    }

    private static void addListValidation(Sheet sheet, int columnIndex, String[] values) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(values);
        CellRangeAddressList range = new CellRangeAddressList(1, VALIDATION_LAST_ROW, columnIndex, columnIndex);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        sheet.addValidationData(validation);
    }

    private static int columnWidth(String header) {
        return switch (header) {
            case "题干", "解析" -> 10000;
            case "选项A", "选项B", "选项C", "选项D", "选项E", "选项F" -> 7000;
            default -> 4200;
        };
    }

    private static List<String> questionHeaders() {
        return List.of("课程ID", "题型", "题干", "选项A", "选项B", "选项C", "选项D", "选项E", "选项F", "正确答案", "难度", "知识点", "解析");
    }

    private static List<String> paperHeaders() {
        return List.of("题号", "题型", "题干", "选项A", "选项B", "选项C", "选项D", "选项E", "选项F", "正确答案", "分值", "难度", "知识点", "解析");
    }

    private static List<String> questionExample() {
        return List.of("1", "单选题", "Java 的关键字 public 表示什么？", "公共访问", "私有访问", "", "", "", "", "A", "中等", "Java基础", "public 允许其他类访问。");
    }

    private static List<String> paperExample() {
        return List.of("1", "单选题", "Java 的关键字 public 表示什么？", "公共访问", "私有访问", "", "", "", "", "A", "5", "中等", "Java基础", "public 允许其他类访问。");
    }

    private static List<String> questionInstructions() {
        return List.of(
                "1. 只填写“题目列表”工作表；每行是一道题，示例行可直接覆盖或删除。",
                "2. 课程ID、题型、题干为必填项；除文件上传题外，正确答案也必填。课程ID请从课程管理列表获取。",
                "3. 单选题填写一个答案键（如 A）；多选题填写至少两个答案键（如 A;C）；判断题填写“正确”或“错误”。",
                "4. 选择题至少填写两个选项；填空、简答和文件上传题不得填写选项。",
                "5. 难度可选：简单、中等、困难；留空时默认中等。"
        );
    }

    private static List<String> paperInstructions() {
        return List.of(
                "1. 一个 Excel 文件只创建一张试卷；试卷名称、课程、时长和状态在导入弹窗填写。",
                "2. 只填写“题目列表”工作表；每行是一道题，题号和分值为必填项。",
                "3. 单选题填写一个答案键（如 A）；多选题填写至少两个答案键（如 A;C）；判断题填写“正确”或“错误”。",
                "4. 选择题至少填写两个选项；填空、简答和文件上传题不得填写选项。",
                "5. 预览出现任何错误时必须先修正，再确认导入。"
        );
    }
}
