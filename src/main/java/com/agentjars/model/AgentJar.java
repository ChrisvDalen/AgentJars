package com.agentjars.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One agent in the registry: its coordinates, the metadata read from {@code AGENT.md} and every
 * version published to Maven Central.
 *
 * @param coordinates  groupId and artifactId; the version field holds the latest version
 * @param manifest     metadata parsed from the packaged {@code AGENT.md}
 * @param versions     all known versions, newest first
 * @param sourceRepo   {@code org/repo} the agent was packaged from
 * @param sourcePath   path of the agent directory inside that repository
 * @param lastUpdated  publication timestamp of the newest version
 */
public record AgentJar(
        Coordinates coordinates,
        AgentManifest manifest,
        List<AgentJarVersion> versions,
        String sourceRepo,
        String sourcePath,
        Instant lastUpdated) {

    public AgentJar {
        versions = versions == null ? List.of() : versions.stream()
                .sorted(Comparator.comparing(AgentJarVersion::published,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public String artifactId() {
        return coordinates.artifactId();
    }

    public String groupId() {
        return coordinates.groupId();
    }

    public String displayName() {
        if (manifest != null && manifest.name() != null && !manifest.name().isBlank()) {
            return manifest.name();
        }
        String agent = coordinates.agent();
        return agent.isEmpty() ? coordinates.repo() : agent;
    }

    public String description() {
        return manifest == null || !manifest.hasDescription()
                ? "No description provided."
                : manifest.description();
    }

    public List<String> tags() {
        return manifest == null ? List.of() : manifest.tags();
    }

    public String license() {
        return manifest == null || manifest.license() == null ? "Unknown" : manifest.license();
    }

    public Optional<AgentJarVersion> latest() {
        return versions.isEmpty() ? Optional.empty() : Optional.of(versions.getFirst());
    }

    public String latestVersion() {
        return latest().map(AgentJarVersion::version).orElseGet(coordinates::version);
    }

    /** Coordinates pinned to the newest published version. */
    public Coordinates latestCoordinates() {
        return coordinates.withVersion(latestVersion());
    }

    public String sourceUrl() {
        if (sourceRepo == null || sourceRepo.isBlank()) {
            return null;
        }
        String base = "https://github.com/" + sourceRepo;
        return sourcePath == null || sourcePath.isBlank() ? base : base + "/tree/HEAD/" + sourcePath;
    }

    /** Case-insensitive match over name, artifactId, description and tags. */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return contains(displayName(), needle)
                || contains(artifactId(), needle)
                || contains(description(), needle)
                || contains(sourceRepo, needle)
                || tags().stream().anyMatch(tag -> contains(tag, needle));
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }
}
