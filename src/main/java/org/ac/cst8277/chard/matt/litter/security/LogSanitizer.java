package org.ac.cst8277.chard.matt.litter.security;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * Utility class to sanitize user input before logging to prevent log injection.
 */
@Slf4j
public enum LogSanitizer {
    ;
    private static final String SANITIZED_REPLACEMENT = "REDACTED_BY_SANITIZER";
    private static final Pattern sanitizePattern = Pattern.compile("[^a-zA-Z0-9]");

    /**
     * Sanitizes the input string to allow only alphanumeric characters.
     *
     * @param input the input string to sanitize
     * @return the sanitized string; returns an empty string if input is null.
     */
    public static String sanitize(CharSequence input) {
        if (null == input) {
            log.debug("Input was null; returning empty string.");
            return "";
        }
        log.debug("Input was sanitized to prevent possible log injection.");
        return sanitizePattern.matcher(input).replaceAll(SANITIZED_REPLACEMENT);
    }
}
