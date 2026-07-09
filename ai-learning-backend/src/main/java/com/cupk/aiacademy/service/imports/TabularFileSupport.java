package com.cupk.aiacademy.service.imports;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelProperty;
import com.cupk.aiacademy.common.BusinessException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TabularFileSupport {
    public static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String CSV_CONTENT_TYPE = "text/csv;charset=UTF-8";

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private TabularFileSupport() {
    }

    public record TabularRow<T>(Integer rowNumber, T data) {
    }

    public record ExportFile(byte[] bytes, String fileName, String contentType) {
    }

    public enum Format {
        XLSX("xlsx", XLSX_CONTENT_TYPE),
        XLS("xls", "application/vnd.ms-excel"),
        CSV("csv", CSV_CONTENT_TYPE);

        private final String extension;
        private final String contentType;

        Format(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        public String extension() {
            return extension;
        }

        public String contentType() {
            return contentType;
        }
    }

    public static Format importFormat(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new BusinessException("无法识别上传文件名");
        }
        String lower = originalName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xlsx")) {
            return Format.XLSX;
        }
        if (lower.endsWith(".xls")) {
            return Format.XLS;
        }
        if (lower.endsWith(".csv")) {
            return Format.CSV;
        }
        throw new BusinessException("仅支持.xlsx、.xls、.csv文件");
    }

    public static Format exportFormat(String raw) {
        String value = raw == null || raw.isBlank() ? "xlsx" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "xlsx", "excel" -> Format.XLSX;
            case "csv" -> Format.CSV;
            default -> throw new BusinessException("不支持的导出格式: " + raw);
        };
    }

    public static <T> List<TabularRow<T>> read(MultipartFile file, Class<T> rowType) {
        Format format = importFormat(file);
        return format == Format.CSV ? readCsv(file, rowType) : readExcel(file, rowType);
    }

    public static <T> byte[] write(String format, Class<T> rowType, List<T> rows) {
        Format resolvedFormat = exportFormat(format);
        List<T> safeRows = rows == null ? List.of() : rows;
        return resolvedFormat == Format.CSV ? writeCsv(rowType, safeRows) : writeExcel(rowType, safeRows);
    }

    public static <T> ExportFile exportFile(String baseName, String format, Class<T> rowType, List<T> rows) {
        Format resolvedFormat = exportFormat(format);
        byte[] bytes = write(resolvedFormat.extension(), rowType, rows);
        return new ExportFile(bytes, baseName + "." + resolvedFormat.extension(), resolvedFormat.contentType());
    }

    private static <T> List<TabularRow<T>> readExcel(MultipartFile file, Class<T> rowType) {
        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BusinessException("Excel内容不能为空");
            }
            Sheet sheet = workbook.getSheetAt(0);
            int headerIndex = firstNonEmptyRow(sheet);
            if (headerIndex < 0) {
                throw new BusinessException("Excel内容不能为空");
            }
            Row headerRow = sheet.getRow(headerIndex);
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Map<Integer, Field> fieldByColumn = mapHeaderFields(headerValues(headerRow, formatter, evaluator), rowType);
            List<TabularRow<T>> rows = new ArrayList<>();
            for (int i = headerIndex + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                T data = newInstance(rowType);
                boolean hasValue = false;
                for (Map.Entry<Integer, Field> entry : fieldByColumn.entrySet()) {
                    String value = cellValue(row.getCell(entry.getKey()), formatter, evaluator);
                    if (!value.isBlank()) {
                        hasValue = true;
                    }
                    setField(data, entry.getValue(), value);
                }
                if (hasValue) {
                    rows.add(new TabularRow<>(i + 1, data));
                }
            }
            return requireRows(rows, "Excel内容不能为空");
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException("读取Excel失败: " + e.getMessage());
        } catch (RuntimeException e) {
            throw new BusinessException("解析Excel失败，请确认文件模板正确");
        }
    }

    private static <T> List<TabularRow<T>> readCsv(MultipartFile file, Class<T> rowType) {
        try {
            List<List<String>> csvRows = parseCsv(decodeCsv(file.getBytes()));
            int headerIndex = -1;
            for (int i = 0; i < csvRows.size(); i++) {
                if (!isBlankRow(csvRows.get(i))) {
                    headerIndex = i;
                    break;
                }
            }
            if (headerIndex < 0) {
                throw new BusinessException("CSV内容不能为空");
            }
            Map<Integer, Field> fieldByColumn = mapHeaderFields(csvRows.get(headerIndex), rowType);
            List<TabularRow<T>> rows = new ArrayList<>();
            for (int i = headerIndex + 1; i < csvRows.size(); i++) {
                List<String> row = csvRows.get(i);
                if (isBlankRow(row)) {
                    continue;
                }
                T data = newInstance(rowType);
                for (Map.Entry<Integer, Field> entry : fieldByColumn.entrySet()) {
                    String value = entry.getKey() < row.size() ? row.get(entry.getKey()).trim() : "";
                    setField(data, entry.getValue(), value);
                }
                rows.add(new TabularRow<>(i + 1, data));
            }
            return requireRows(rows, "CSV内容不能为空");
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException("读取CSV失败: " + e.getMessage());
        } catch (RuntimeException e) {
            throw new BusinessException("解析CSV失败，请确认文件格式正确");
        }
    }

    private static <T> byte[] writeExcel(Class<T> rowType, List<T> rows) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            EasyExcel.write(output, rowType)
                    .sheet("导出数据")
                    .doWrite(rows);
            return output.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("生成Excel失败");
        } catch (RuntimeException e) {
            throw new BusinessException("生成Excel失败: " + e.getMessage());
        }
    }

    private static <T> byte[] writeCsv(Class<T> rowType, List<T> rows) {
        List<Column> columns = columns(rowType);
        StringBuilder builder = new StringBuilder();
        builder.append(toCsvLine(columns.stream().map(Column::header).toList()));
        for (T row : rows) {
            List<String> values = new ArrayList<>();
            for (Column column : columns) {
                values.add(formatValue(getFieldValue(row, column.field())));
            }
            builder.append("\r\n").append(toCsvLine(values));
        }
        byte[] body = builder.toString().getBytes(StandardCharsets.UTF_8);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            output.write(UTF8_BOM);
            output.write(body);
            return output.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("生成CSV失败");
        }
    }

    private static List<String> headerValues(Row headerRow, DataFormatter formatter, FormulaEvaluator evaluator) {
        List<String> headers = new ArrayList<>();
        short last = headerRow.getLastCellNum();
        for (int i = 0; i < last; i++) {
            headers.add(cellValue(headerRow.getCell(i), formatter, evaluator));
        }
        return headers;
    }

    private static Map<Integer, Field> mapHeaderFields(List<String> headers, Class<?> rowType) {
        Map<String, Field> fieldByAlias = new LinkedHashMap<>();
        for (Column column : columns(rowType)) {
            for (String alias : column.aliases()) {
                fieldByAlias.putIfAbsent(normalize(alias), column.field());
            }
        }
        Map<Integer, Field> fieldByColumn = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            Field field = fieldByAlias.get(normalize(headers.get(i)));
            if (field != null) {
                fieldByColumn.put(i, field);
            }
        }
        if (fieldByColumn.isEmpty()) {
            throw new BusinessException("文件表头与导入模板不匹配");
        }
        return fieldByColumn;
    }

    private static List<Column> columns(Class<?> rowType) {
        List<Column> columns = new ArrayList<>();
        for (Field field : rowType.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            ExcelProperty property = field.getAnnotation(ExcelProperty.class);
            String header = field.getName();
            Set<String> aliases = new LinkedHashSet<>();
            aliases.add(field.getName());
            aliases.add(toSnakeCase(field.getName()));
            aliases.add(toKebabCase(field.getName()));
            if (property != null && property.value().length > 0) {
                for (String item : property.value()) {
                    if (item != null && !item.isBlank()) {
                        header = item;
                        aliases.add(item);
                    }
                }
            }
            field.setAccessible(true);
            columns.add(new Column(field, header, aliases.stream().toList()));
        }
        columns.sort(Comparator.comparingInt(column -> column.field().getAnnotation(ExcelProperty.class) == null
                ? Integer.MAX_VALUE
                : column.field().getAnnotation(ExcelProperty.class).index()));
        return columns;
    }

    private static String cellValue(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        if (DateUtil.isCellDateFormatted(cell)) {
            try {
                return cell.getLocalDateTimeCellValue().format(DATE_TIME_FORMATTER);
            } catch (RuntimeException ignored) {
            }
        }
        return formatter.formatCellValue(cell, evaluator).trim();
    }

    private static int firstNonEmptyRow(Sheet sheet) {
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            short first = row.getFirstCellNum();
            short last = row.getLastCellNum();
            if (first < 0 || last < 0) {
                continue;
            }
            for (int j = first; j < last; j++) {
                if (!cellValue(row.getCell(j), new DataFormatter(Locale.CHINA), null).isBlank()) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static <T> List<TabularRow<T>> requireRows(List<TabularRow<T>> rows, String message) {
        if (rows.isEmpty()) {
            throw new BusinessException(message);
        }
        return rows;
    }

    private static <T> T newInstance(Class<T> rowType) {
        try {
            return rowType.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new BusinessException("导入行类型缺少无参构造方法: " + rowType.getSimpleName());
        }
    }

    private static void setField(Object target, Field field, String rawValue) {
        try {
            Class<?> type = field.getType();
            String value = rawValue == null ? "" : rawValue.trim();
            if (type == String.class) {
                field.set(target, value.isBlank() ? null : value);
            } else if (type == Integer.class) {
                field.set(target, value.isBlank() ? null : new BigDecimal(value).intValueExact());
            } else if (type == Long.class) {
                field.set(target, value.isBlank() ? null : new BigDecimal(value).longValueExact());
            } else if (type == BigDecimal.class) {
                field.set(target, value.isBlank() ? null : new BigDecimal(value));
            } else {
                field.set(target, value.isBlank() ? null : value);
            }
        } catch (ReflectiveOperationException | ArithmeticException | NumberFormatException e) {
            throw new BusinessException("字段" + field.getName() + "类型转换失败");
        }
    }

    private static Object getFieldValue(Object target, Field field) {
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(DATE_TIME_FORMATTER);
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }

    private static String decodeCsv(byte[] bytes) {
        if (bytes.length >= 3 && bytes[0] == UTF8_BOM[0] && bytes[1] == UTF8_BOM[1] && bytes[2] == UTF8_BOM[2]) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ignored) {
            return Charset.forName("GBK").decode(ByteBuffer.wrap(bytes)).toString();
        }
    }

    private static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        boolean hasAny = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                hasAny = true;
            } else if (ch == ',' && !inQuotes) {
                row.add(cell.toString());
                cell.setLength(0);
                hasAny = true;
            } else if ((ch == '\r' || ch == '\n') && !inQuotes) {
                row.add(cell.toString());
                cell.setLength(0);
                if (hasAny || !isBlankRow(row)) {
                    rows.add(row);
                }
                row = new ArrayList<>();
                hasAny = false;
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                cell.append(ch);
                hasAny = true;
            }
        }
        row.add(cell.toString());
        if (hasAny || !isBlankRow(row)) {
            rows.add(row);
        }
        return rows;
    }

    private static boolean isBlankRow(List<String> row) {
        if (row == null || row.isEmpty()) {
            return true;
        }
        for (String value : row) {
            if (value != null && !value.trim().isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String toCsvLine(List<String> values) {
        return values.stream().map(TabularFileSupport::csvEscape).reduce((left, right) -> left + "," + right).orElse("");
    }

    private static String csvEscape(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains("\"") || safe.contains(",") || safe.contains("\r") || safe.contains("\n")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_\\-()（）\\[\\]【】:：.。/\\\\]+", "");
    }

    private static String toSnakeCase(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private static String toKebabCase(String value) {
        return toSnakeCase(value).replace('_', '-');
    }

    private record Column(Field field, String header, List<String> aliases) {
    }
}
