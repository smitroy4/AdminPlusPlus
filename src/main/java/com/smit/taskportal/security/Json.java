package com.smit.taskportal.security;

/**
 * Tiny hand-rolled JSON writer for the security filter chain.
 *
 * <p>Deliberately avoids an {@code ObjectMapper} here: these two responses are
 * written on the container's error path where no user message from us should
 * ever contain raw user input beyond a fixed string.
 */
final class Json {

    private Json() {
    }

    static String error(String message) {
        return "{\"success\":false,\"message\":" + quote(message) + ",\"data\":null}";
    }

    static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
