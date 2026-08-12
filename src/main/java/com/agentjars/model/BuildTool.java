package com.agentjars.model;

import java.util.Arrays;
import java.util.Locale;

/** Build tools the registry can emit a dependency snippet for. */
public enum BuildTool {

    MAVEN("maven", "Maven", "xml"),
    GRADLE("gradle", "Gradle", "kotlin"),
    GRADLE_GROOVY("gradle-groovy", "Gradle (Groovy)", "groovy"),
    SBT("sbt", "sbt", "scala");

    private final String id;
    private final String label;
    private final String highlightLanguage;

    BuildTool(String id, String label, String highlightLanguage) {
        this.id = id;
        this.label = label;
        this.highlightLanguage = highlightLanguage;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String highlightLanguage() {
        return highlightLanguage;
    }

    /** Resolves an id coming from a query parameter, falling back to Maven. */
    public static BuildTool fromId(String value) {
        if (value == null || value.isBlank()) {
            return MAVEN;
        }
        String needle = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(tool -> tool.id.equals(needle) || tool.name().toLowerCase(Locale.ROOT).equals(needle))
                .findFirst()
                .orElse(MAVEN);
    }

    /** Renders the dependency declaration a consumer copies into their build. */
    public String snippet(Coordinates coordinates) {
        String group = coordinates.groupId();
        String artifact = coordinates.artifactId();
        String version = coordinates.version() == null ? "LATEST" : coordinates.version();
        return switch (this) {
            case MAVEN -> """
                    <dependency>
                        <groupId>%s</groupId>
                        <artifactId>%s</artifactId>
                        <version>%s</version>
                    </dependency>""".formatted(group, artifact, version);
            case GRADLE -> "implementation(\"%s:%s:%s\")".formatted(group, artifact, version);
            case GRADLE_GROOVY -> "implementation '%s:%s:%s'".formatted(group, artifact, version);
            case SBT -> "libraryDependencies += \"%s\" %% \"%s\" %% \"%s\"".formatted(group, artifact, version);
        };
    }
}
