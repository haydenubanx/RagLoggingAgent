package org.hayden.ragloggingagent.utils;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@Service
public class DateFormatUtil {
    private static final DateTimeFormatter LOG_FORMAT = DateTimeFormatter.ofPattern("[dd/MMM/yyyy:HH:mm:ss Z]", Locale.ENGLISH);
    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Tool(
            name = "toIso8601",
            description = "Converts a date string to ISO 8601 format. Supports both ISO and custom log formats."
    )
    public static String toIso8601(String input) {
        if (input == null) return null;
        try {
            // Try ISO first
            OffsetDateTime odt = OffsetDateTime.parse(input, ISO_FORMAT);
            return odt.toString();
        } catch (DateTimeParseException ignored) {}
        try {
            if (input.startsWith("[") && input.endsWith("]")) {
                input = input.substring(1, input.length() - 1);
            }
            ZonedDateTime zdt = ZonedDateTime.parse(input, LOG_FORMAT);
            return zdt.toOffsetDateTime().toString();
        } catch (DateTimeParseException ignored) {}
        // Fallback: return as-is
        return input;
    }

    @Tool(
            name = "toLogFormat",
            description = "Converts a date string to the log format (dd/MMM/yyyy:HH:mm:ss Z)."
    )
    public static String toLogFormat(String input) {
        String iso = toIso8601(input);
        OffsetDateTime odt = OffsetDateTime.parse(iso);
        return LOG_FORMAT.format(odt);
    }
}