package com.vspicy.content.security;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class HtmlSanitizer {
    private static final Pattern SCRIPT_STYLE = Pattern.compile("(?is)<(script|style|iframe|object|embed)[^>]*>.*?</\\1>");
    private static final Pattern DANGEROUS_TAG_SELF_CLOSE = Pattern.compile("(?is)<(script|style|iframe|object|embed)[^>]*?/?>");
    private static final Pattern EVENT_ATTR_DOUBLE = Pattern.compile("(?i)\\s+on[a-z]+\\s*=\\s*\"[^\"]*\"");
    private static final Pattern EVENT_ATTR_SINGLE = Pattern.compile("(?i)\\s+on[a-z]+\\s*=\\s*'[^']*'");
    private static final Pattern EVENT_ATTR_UNQUOTED = Pattern.compile("(?i)\\s+on[a-z]+\\s*=\\s*[^\\s>]+");
    private static final Pattern JAVASCRIPT_URL = Pattern.compile("(?i)javascript\\s*:");
    private static final Pattern TAGS = Pattern.compile("(?is)<[^>]+>");

    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        String cleaned = html;
        cleaned = SCRIPT_STYLE.matcher(cleaned).replaceAll("");
        cleaned = DANGEROUS_TAG_SELF_CLOSE.matcher(cleaned).replaceAll("");
        cleaned = EVENT_ATTR_DOUBLE.matcher(cleaned).replaceAll("");
        cleaned = EVENT_ATTR_SINGLE.matcher(cleaned).replaceAll("");
        cleaned = EVENT_ATTR_UNQUOTED.matcher(cleaned).replaceAll("");
        cleaned = JAVASCRIPT_URL.matcher(cleaned).replaceAll("");
        return cleaned;
    }

    public String plainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return TAGS.matcher(sanitize(html)).replaceAll(" ");
    }
}
