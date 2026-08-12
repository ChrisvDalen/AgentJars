package com.agentjars.model;

import java.nio.file.Path;

/**
 * An agent found by scanning a checked-out repository, before it is packaged into a jar.
 *
 * @param directory  absolute path of the directory holding {@code AGENT.md}
 * @param relativePath path of that directory relative to the repository root ("" at the root)
 * @param manifest   metadata parsed from the {@code AGENT.md} front matter
 */
public record DiscoveredAgent(Path directory, String relativePath, AgentManifest manifest) {

    /** The name used in the artifactId and in the jar layout. */
    public String slug() {
        if (relativePath == null || relativePath.isBlank()) {
            return manifest.name();
        }
        int slash = relativePath.lastIndexOf('/');
        return slash < 0 ? relativePath : relativePath.substring(slash + 1);
    }
}
