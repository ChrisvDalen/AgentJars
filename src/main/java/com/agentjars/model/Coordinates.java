package com.agentjars.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Maven coordinates of a single AgentJar.
 *
 * <p>AgentJars use a derived artifactId so that a coordinate can always be traced back to the
 * GitHub repository and agent directory it was built from:
 * {@code <org>__<repo>__<agent-path>}. Each segment is lowercased and stripped of everything
 * that is not alphanumeric, a hyphen or an underscore; path separators inside the agent path
 * become single hyphens.
 */
public record Coordinates(String groupId, String artifactId, String version) {

    private static final String SEPARATOR = "__";

    public Coordinates {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(artifactId, "artifactId");
    }

    public static Coordinates of(String groupId, String artifactId, String version) {
        return new Coordinates(groupId, artifactId, version);
    }

    /**
     * Builds the artifactId for an agent discovered at {@code agentPath} in {@code org/repo}.
     *
     * @param org       GitHub organisation or user
     * @param repo      GitHub repository name
     * @param agentPath path of the agent directory relative to the repository root, or the empty
     *                  string when the agent lives at the repository root
     */
    public static String artifactId(String org, String repo, String agentPath) {
        String normalisedOrg = normalise(org);
        String normalisedRepo = normalise(repo);
        String normalisedAgent = normalise(stripAgentPathPrefix(agentPath));
        if (normalisedOrg.isEmpty() || normalisedRepo.isEmpty()) {
            throw new IllegalArgumentException("org and repo are required to derive an artifactId");
        }
        if (normalisedAgent.isEmpty()) {
            return normalisedOrg + SEPARATOR + normalisedRepo;
        }
        return normalisedOrg + SEPARATOR + normalisedRepo + SEPARATOR + normalisedAgent;
    }

    /**
     * Drops a leading {@code agents/} segment so that {@code agents/reviewer} and {@code reviewer}
     * produce the same artifactId.
     */
    private static String stripAgentPathPrefix(String agentPath) {
        if (agentPath == null) {
            return "";
        }
        String trimmed = agentPath.replaceAll("^/+", "").replaceAll("/+$", "");
        if (trimmed.equalsIgnoreCase("agents")) {
            return "";
        }
        if (trimmed.regionMatches(true, 0, "agents/", 0, "agents/".length())) {
            return trimmed.substring("agents/".length());
        }
        return trimmed;
    }

    private static String normalise(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace('/', '-')
                .replaceAll("[^a-z0-9\\-_]", "")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
    }

    /** The GitHub organisation this artifactId was derived from. */
    public String org() {
        return artifactId.split(SEPARATOR, -1)[0];
    }

    /** The GitHub repository this artifactId was derived from. */
    public String repo() {
        String[] parts = artifactId.split(SEPARATOR, -1);
        return parts.length > 1 ? parts[1] : "";
    }

    /** The agent segment of the artifactId, empty for repository-root agents. */
    public String agent() {
        String[] parts = artifactId.split(SEPARATOR, -1);
        return parts.length > 2 ? parts[2] : "";
    }

    /** Path of this artifact inside a Maven repository, without a trailing slash. */
    public String repositoryPath() {
        String base = groupId.replace('.', '/') + "/" + artifactId;
        return version == null || version.isBlank() ? base : base + "/" + version;
    }

    /** File name of the main jar for this coordinate. */
    public String jarFileName() {
        return artifactId + "-" + version + ".jar";
    }

    /** File name of the pom for this coordinate. */
    public String pomFileName() {
        return artifactId + "-" + version + ".pom";
    }

    public Coordinates withVersion(String newVersion) {
        return new Coordinates(groupId, artifactId, newVersion);
    }

    @Override
    public String toString() {
        return version == null || version.isBlank()
                ? groupId + ":" + artifactId
                : groupId + ":" + artifactId + ":" + version;
    }
}
