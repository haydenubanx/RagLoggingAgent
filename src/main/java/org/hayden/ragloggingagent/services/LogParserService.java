package org.hayden.ragloggingagent.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LogParserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogParserService.class);


    // Regex for Apache/Nginx combined log format with response time at the end
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(?<ip>\\S+) \\S+ \\S+ \\[(?<timestamp>[^\\]]+)] " +
                    "\"(?<method>\\S+) (?<endpoint>\\S+) \\S+\" " +
                    "(?<status>\\d{3}) (?<size>\\d+) " +
                    "\"(?<referer>[^\"]*)\" " +
                    "\"(?<userAgent>[^\"]*)\" " +
                    "(?<responseTime>\\d+)"
    );

    public Map<String, Object> parseLogLine(String logLine) {
        Matcher matcher = LOG_PATTERN.matcher(logLine);
        if (matcher.find()) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("ip", matcher.group("ip"));
            metadata.put("timestamp", matcher.group("timestamp"));
            metadata.put("request_type", matcher.group("method"));
            metadata.put("endpoint", matcher.group("endpoint"));
            metadata.put("status", matcher.group("status"));
            metadata.put("size", matcher.group("size"));
            metadata.put("referer", matcher.group("referer"));
            metadata.put("user_agent", matcher.group("userAgent"));
            metadata.put("response_time", matcher.group("responseTime"));
            metadata.put("raw", logLine);
            return metadata;
        }
        return null;
    }

    public List<String> readAllLogLines() {
        List<String> lines = new ArrayList<>();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("logfiles.log");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error reading log file: {}", e.getMessage());
        }
        return lines;
    }

}