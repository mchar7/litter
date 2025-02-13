package org.ac.cst8277.chard.matt.litter.security;

/**
 * Utility class to sanitize user input before logging to prevent log injection.
 */
public enum LogSanitizer {
    ;

    /**
     * Sanitizes the input string to allow only alphanumeric characters.
     *
     * @param input the input string to sanitize
     * @return the sanitized string; returns an empty string if input is null.
     */
    public static String sanitize(String input) {
        if (null == input) {
            return "";
        }
        return input.replaceAll("[^a-zA-Z0-9]", "");
    }
}
