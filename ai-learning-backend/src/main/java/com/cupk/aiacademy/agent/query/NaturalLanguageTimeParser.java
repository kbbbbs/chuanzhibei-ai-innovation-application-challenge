package com.cupk.aiacademy.agent.query;


import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Deprecated(forRemoval = true)
public class NaturalLanguageTimeParser {
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern DAYS_PATTERN = Pattern.compile("(?:最近|近|过去)(\\d{1,3})天");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})");
    private static final Pattern CN_DATE_PATTERN = Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日?");
    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("(\\d{4})[-/年](\\d{1,2})月?");

    public NaturalLanguageTimeRange parse(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return empty();
        }

        LocalDate today = LocalDate.now(DEFAULT_ZONE);
        NaturalLanguageTimeRange relative = parseRelative(value, today);
        if (relative.present()) {
            return relative;
        }

        NaturalLanguageTimeRange explicit = parseExplicitDateRange(value);
        if (explicit.present()) {
            return explicit;
        }

        return empty();
    }

    private NaturalLanguageTimeRange parseRelative(String value, LocalDate today) {
        Matcher daysMatcher = DAYS_PATTERN.matcher(value);
        if (daysMatcher.find()) {
            int days = clampDays(daysMatcher.group(1));
            LocalDate start = today.minusDays(days - 1L);
            return wholeDays(start, today, daysMatcher.group());
        }

        if (containsAny(value, "今天", "今日")) {
            return wholeDay(today, "今天");
        }
        if (containsAny(value, "昨天", "昨日")) {
            return wholeDay(today.minusDays(1), "昨天");
        }
        if (containsAny(value, "明天", "明日")) {
            return wholeDay(today.plusDays(1), "明天");
        }
        if (value.contains("本周") || value.contains("这周")) {
            LocalDate start = today.with(DayOfWeek.MONDAY);
            return wholeDays(start, start.plusDays(6), "本周");
        }
        if (value.contains("上周")) {
            LocalDate start = today.with(DayOfWeek.MONDAY).minusWeeks(1);
            return wholeDays(start, start.plusDays(6), "上周");
        }
        if (value.contains("下周")) {
            LocalDate start = today.with(DayOfWeek.MONDAY).plusWeeks(1);
            if (containsAny(value, "周一", "星期一", "礼拜一")) {
                return wholeDay(start, "下周一");
            }
            if (containsAny(value, "周二", "星期二", "礼拜二")) {
                return wholeDay(start.plusDays(1), "下周二");
            }
            if (containsAny(value, "周三", "星期三", "礼拜三")) {
                return wholeDay(start.plusDays(2), "下周三");
            }
            if (containsAny(value, "周四", "星期四", "礼拜四")) {
                return wholeDay(start.plusDays(3), "下周四");
            }
            if (containsAny(value, "周五", "星期五", "礼拜五")) {
                return wholeDay(start.plusDays(4), "下周五");
            }
            if (containsAny(value, "周六", "星期六", "礼拜六")) {
                return wholeDay(start.plusDays(5), "下周六");
            }
            if (containsAny(value, "周日", "周天", "星期日", "星期天", "礼拜日", "礼拜天")) {
                return wholeDay(start.plusDays(6), "下周日");
            }
            return wholeDays(start, start.plusDays(6), "下周");
        }
        if (value.contains("本月") || value.contains("这个月")) {
            YearMonth month = YearMonth.from(today);
            return wholeDays(month.atDay(1), month.atEndOfMonth(), "本月");
        }
        if (value.contains("上月") || value.contains("上个月")) {
            YearMonth month = YearMonth.from(today).minusMonths(1);
            return wholeDays(month.atDay(1), month.atEndOfMonth(), "上月");
        }
        if (value.contains("下月") || value.contains("下个月")) {
            YearMonth month = YearMonth.from(today).plusMonths(1);
            return wholeDays(month.atDay(1), month.atEndOfMonth(), "下月");
        }
        if (value.contains("今年")) {
            return wholeDays(LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 12, 31), "今年");
        }
        if (value.contains("去年")) {
            int year = today.getYear() - 1;
            return wholeDays(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), "去年");
        }
        if (value.contains("下个工作日")) {
            LocalDate next = today.plusDays(1);
            while (next.getDayOfWeek() == DayOfWeek.SATURDAY || next.getDayOfWeek() == DayOfWeek.SUNDAY) {
                next = next.plusDays(1);
            }
            return wholeDay(next, "下个工作日");
        }
        if (value.contains("周末")) {
            LocalDate saturday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
            return wholeDays(saturday, saturday.plusDays(1), "周末");
        }
        return empty();
    }

    private NaturalLanguageTimeRange parseExplicitDateRange(String value) {
        Matcher isoMatcher = ISO_DATE_PATTERN.matcher(value);
        LocalDate first = null;
        LocalDate second = null;
        while (isoMatcher.find()) {
            LocalDate date = safeDate(isoMatcher.group(1), isoMatcher.group(2), isoMatcher.group(3));
            if (date == null) {
                continue;
            }
            if (first == null) {
                first = date;
            } else {
                second = date;
                break;
            }
        }
        if (first != null && second != null) {
            return orderedDays(first, second, "显式日期范围");
        }
        if (first != null) {
            return wholeDay(first, "显式日期");
        }

        Matcher cnMatcher = CN_DATE_PATTERN.matcher(value);
        while (cnMatcher.find()) {
            LocalDate date = safeDate(cnMatcher.group(1), cnMatcher.group(2), cnMatcher.group(3));
            if (date == null) {
                continue;
            }
            if (first == null) {
                first = date;
            } else {
                second = date;
                break;
            }
        }
        if (first != null && second != null) {
            return orderedDays(first, second, "中文日期范围");
        }
        if (first != null) {
            return wholeDay(first, "中文日期");
        }

        Matcher monthMatcher = YEAR_MONTH_PATTERN.matcher(value);
        if (monthMatcher.find()) {
            try {
                YearMonth month = YearMonth.of(Integer.parseInt(monthMatcher.group(1)), Integer.parseInt(monthMatcher.group(2)));
                return wholeDays(month.atDay(1), month.atEndOfMonth(), monthMatcher.group());
            } catch (RuntimeException ignored) {
                return empty();
            }
        }
        return empty();
    }

    private NaturalLanguageTimeRange orderedDays(LocalDate first, LocalDate second, String source) {
        if (first.isAfter(second)) {
            return wholeDays(second, first, source);
        }
        return wholeDays(first, second, source);
    }

    private NaturalLanguageTimeRange wholeDay(LocalDate date, String source) {
        return wholeDays(date, date, source);
    }

    private NaturalLanguageTimeRange wholeDays(LocalDate start, LocalDate end, String source) {
        return new NaturalLanguageTimeRange(start.atStartOfDay(), end.atTime(LocalTime.MAX), source);
    }

    private NaturalLanguageTimeRange empty() {
        return new NaturalLanguageTimeRange(null, null, "");
    }

    private LocalDate safeDate(String year, String month, String day) {
        try {
            return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private int clampDays(String value) {
        try {
            return Math.max(1, Math.min(180, Integer.parseInt(value)));
        } catch (NumberFormatException ignored) {
            return 7;
        }
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
