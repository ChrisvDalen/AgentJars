package com.agentjars.packaging;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.agentjars.model.AgentManifest;
import org.springframework.stereotype.Component;

/**
 * Reads the YAML front matter at the top of an {@code AGENT.md} file.
 *
 * <p>Only the small subset of YAML that agent front matter actually uses is supported: scalar
 * values, inline lists ({@code [a, b]}) and block lists. That keeps the registry free of a YAML
 * dependency and makes malformed input fail predictably rather than in surprising ways.
 */
@Component
public class AgentManifestParser {

    private static final String DELIMITER = "---";

    /**
     * Parses an {@code AGENT.md} document.
     *
     * @param content    the full file content
     * @param fallbackName name to use when the front matter carries no {@code name}
     */
    public AgentManifest parse(String content, String fallbackName) {
        Map<String, Object> frontMatter = frontMatter(content);
        String name = string(frontMatter, "name");
        return new AgentManifest(
                name == null || name.isBlank() ? fallbackName : name,
                firstNonBlank(string(frontMatter, "description"), firstParagraph(content)),
                string(frontMatter, "model"),
                list(frontMatter, "tools"),
                list(frontMatter, "tags"),
                firstNonBlank(string(frontMatter, "license"), string(frontMatter, "spdx")),
                firstNonBlank(string(frontMatter, "homepage"), string(frontMatter, "url")));
    }

    /** Extracts the front matter block as a map, empty when the document has none. */
    public Map<String, Object> frontMatter(String content) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (content == null) {
            return values;
        }
        List<String> lines = content.lines().toList();
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty()) {
                continue;
            }
            start = line.equals(DELIMITER) ? i : -1;
            break;
        }
        if (start < 0) {
            return values;
        }

        String pendingListKey = null;
        List<String> pendingList = new ArrayList<>();
        for (int i = start + 1; i < lines.size(); i++) {
            String raw = lines.get(i);
            String line = raw.strip();
            if (line.equals(DELIMITER)) {
                flush(values, pendingListKey, pendingList);
                return values;
            }
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("- ") || line.equals("-")) {
                if (pendingListKey != null) {
                    pendingList.add(unquote(line.length() > 1 ? line.substring(1).strip() : ""));
                }
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            flush(values, pendingListKey, pendingList);
            pendingListKey = null;
            pendingList = new ArrayList<>();

            String key = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).strip();
            if (value.isEmpty()) {
                pendingListKey = key;
            } else if (value.startsWith("[") && value.endsWith("]")) {
                values.put(key, inlineList(value));
            } else {
                values.put(key, unquote(value));
            }
        }
        flush(values, pendingListKey, pendingList);
        return values;
    }

    private void flush(Map<String, Object> values, String key, List<String> pending) {
        if (key != null && !pending.isEmpty()) {
            values.put(key, List.copyOf(pending));
        }
    }

    private List<String> inlineList(String value) {
        String inner = value.substring(1, value.length() - 1).strip();
        if (inner.isEmpty()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String part : inner.split(",")) {
            String item = unquote(part.strip());
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return List.copyOf(items);
    }

    private static String unquote(String value) {
        String trimmed = value.strip();
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'")))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String string(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof List<?> list && !list.isEmpty()) {
            return String.valueOf(list.getFirst());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value instanceof List<?> list) {
            return ((List<Object>) list).stream().map(String::valueOf).toList();
        }
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text.split("\\s*,\\s*"));
        }
        return List.of();
    }

    /** Falls back to the first prose paragraph when the front matter has no description. */
    private static String firstParagraph(String content) {
        if (content == null) {
            return null;
        }
        String body = stripFrontMatter(content);
        StringBuilder paragraph = new StringBuilder();
        for (String line : body.lines().toList()) {
            String trimmed = line.strip();
            if (trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.isEmpty()) {
                if (!paragraph.isEmpty()) {
                    break;
                }
                continue;
            }
            if (!paragraph.isEmpty()) {
                paragraph.append(' ');
            }
            paragraph.append(trimmed);
        }
        String result = paragraph.toString();
        return result.isBlank() ? null : result;
    }

    private static String stripFrontMatter(String content) {
        List<String> lines = new ArrayList<>(content.lines().toList());
        if (lines.isEmpty() || !lines.getFirst().strip().equals(DELIMITER)) {
            return content;
        }
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).strip().equals(DELIMITER)) {
                return String.join("\n", lines.subList(i + 1, lines.size()));
            }
        }
        return content;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
