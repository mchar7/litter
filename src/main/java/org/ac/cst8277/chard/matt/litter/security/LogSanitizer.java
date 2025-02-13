package org.ac.cst8277.chard.matt.litter.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * Utility class to sanitize user input before logging to prevent log injection.
 */
public enum LogSanitizer {
    ;
    private static final Logger LOGGER = LoggerFactory.getLogger(LogSanitizer.class);
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
            LOGGER.debug("sanitize() received null input, returning empty string.");
            return "";
        }
        String sanitized = sanitizePattern.matcher(input).replaceAll(SANITIZED_REPLACEMENT);
        LOGGER.debug("Input was sanitized to prevent possible log injection.");
        return sanitized;
    }
}
