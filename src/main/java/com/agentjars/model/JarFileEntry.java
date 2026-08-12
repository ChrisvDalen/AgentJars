package com.agentjars.model;

/**
 * One entry in the file listing of a packaged AgentJar.
 *
 * @param path      full path inside the jar
 * @param size      uncompressed size in bytes, {@code -1} for directories
 * @param directory whether the entry is a directory
 */
public record JarFileEntry(String path, long size, boolean directory) {

    public static JarFileEntry file(String path, long size) {
        return new JarFileEntry(path, size, false);
    }

    public static JarFileEntry directory(String path) {
        return new JarFileEntry(path, -1, true);
    }

    /** The last path segment, used as the label in the file tree. */
    public String name() {
        String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int slash = trimmed.lastIndexOf('/');
        return slash < 0 ? trimmed : trimmed.substring(slash + 1);
    }

    /** Nesting level, used to indent the file tree. */
    public int depth() {
        String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        return (int) trimmed.chars().filter(c -> c == '/').count();
    }

    /** Size rendered for humans; directories render as an empty cell. */
    public String humanSize() {
        if (directory || size < 0) {
            return "";
        }
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return "%.1f KB".formatted(size / 1024.0);
        }
        return "%.1f MB".formatted(size / (1024.0 * 1024.0));
    }
}
