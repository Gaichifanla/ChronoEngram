package com.jinjin.memory.infrastructure.temporal;

import com.jinjin.memory.domain.TemporalPrecision;
import com.jinjin.memory.domain.TemporalRange;

import java.time.DayOfWeek;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EventTemporalParser {
    private static final Pattern ISO_DATE = Pattern.compile("(?<!\\d)(\\d{4})[-/年](\\d{1,2})[-/月](\\d{1,2})(?:日)?");
    private static final Pattern LAST_WEEK = Pattern.compile("上周([一二三四五六日天])");
    private static final Pattern KNOWLEDGE_TIME = Pattern.compile("(前天|昨天|今天|刚刚|现在).{0,4}(?:才?知道|得知|听说|获悉)");
    private static final Map<String, DayOfWeek> WEEKDAYS = Map.of(
            "一", DayOfWeek.MONDAY, "二", DayOfWeek.TUESDAY, "三", DayOfWeek.WEDNESDAY,
            "四", DayOfWeek.THURSDAY, "五", DayOfWeek.FRIDAY, "六", DayOfWeek.SATURDAY,
            "日", DayOfWeek.SUNDAY, "天", DayOfWeek.SUNDAY
    );

    private final ZoneId zoneId;

    public EventTemporalParser() {
        this(ZoneId.systemDefault());
    }

    public EventTemporalParser(ZoneId zoneId) {
        this.zoneId = zoneId;
    }

    public TemporalRange parse(String content, Instant learnedAt) {
        LocalDate learnedDate = learnedAt.atZone(zoneId).toLocalDate();
        String eventText = KNOWLEDGE_TIME.matcher(content).replaceAll("");
        Matcher absolute = ISO_DATE.matcher(eventText);
        if (absolute.find()) {
            try {
                LocalDate date = LocalDate.of(
                        Integer.parseInt(absolute.group(1)),
                        Integer.parseInt(absolute.group(2)),
                        Integer.parseInt(absolute.group(3))
                );
                return day(date, absolute.group(), 1.0);
            } catch (DateTimeException ignored) {
                // Invalid user-provided dates remain inferred instead of aborting the write.
            }
        }

        Matcher lastWeek = LAST_WEEK.matcher(eventText);
        if (lastWeek.find()) {
            LocalDate monday = learnedDate.minusWeeks(1).with(DayOfWeek.MONDAY);
            LocalDate date = monday.with(WEEKDAYS.get(lastWeek.group(1)));
            return day(date, lastWeek.group(), 0.95);
        }
        if (eventText.contains("前天")) {
            return day(learnedDate.minusDays(2), "前天", 0.9);
        }
        if (eventText.contains("昨天")) {
            return day(learnedDate.minusDays(1), "昨天", 0.9);
        }
        if (eventText.contains("今天") || eventText.contains("刚刚") || eventText.contains("现在")) {
            return TemporalRange.point(learnedAt, TemporalPrecision.EXACT, firstMatch(eventText, "今天", "刚刚", "现在"), 0.85);
        }
        return TemporalRange.point(learnedAt, TemporalPrecision.INFERRED, "", 0.55);
    }

    private TemporalRange day(LocalDate date, String expression, double confidence) {
        ZonedDateTime start = date.atStartOfDay(zoneId);
        return new TemporalRange(
                start.toInstant(),
                start.plusDays(1).minusNanos(1).toInstant(),
                TemporalPrecision.DAY,
                expression,
                confidence
        );
    }

    private String firstMatch(String content, String... values) {
        for (String value : values) {
            if (content.contains(value)) {
                return value;
            }
        }
        return "";
    }
}
