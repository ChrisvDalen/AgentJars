package com.agentjars.packaging;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import com.agentjars.model.AgentManifest;
import com.agentjars.model.DiscoveredAgent;
import org.springframework.stereotype.Component;

/**
 * Finds the agents in a checked-out repository.
 *
 * <p>An agent is any directory containing an {@code AGENT.md}. Repositories may keep agents under
 * an {@code agents/} directory, nest them further, or define a single agent at the repository
 * root. Agent directories may not contain each other: a nested agent would be packaged twice, so
 * an overlap is rejected rather than silently resolved.
 */
@Component
public class AgentScanner {

    public static final String MANIFEST_FILE = "AGENT.md";

    private static final int MAX_DEPTH = 8;
    private static final List<String> IGNORED_DIRECTORIES =
            List.of(".git", ".github", "node_modules", "target", "build", "out", ".idea", "dist");

    private final AgentManifestParser parser;

    public AgentScanner(AgentManifestParser parser) {
        this.parser = parser;
    }

    /**
     * Scans {@code repositoryRoot} for agents.
     *
     * @throws IllegalStateException when two discovered agents overlap
     */
    public List<DiscoveredAgent> scan(Path repositoryRoot) {
        List<DiscoveredAgent> agents = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(repositoryRoot, MAX_DEPTH)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(MANIFEST_FILE))
                    .filter(path -> !isIgnored(repositoryRoot, path))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> agents.add(read(repositoryRoot, path)));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not scan " + repositoryRoot, e);
        }
        verifyNoOverlap(agents);
        return List.copyOf(agents);
    }

    private DiscoveredAgent read(Path repositoryRoot, Path manifestPath) {
        Path directory = manifestPath.getParent();
        String relativePath = normalise(repositoryRoot.relativize(directory).toString());
        String fallbackName = relativePath.isEmpty()
                ? repositoryRoot.getFileName().toString()
                : relativePath.substring(relativePath.lastIndexOf('/') + 1);
        try {
            String content = Files.readString(manifestPath, StandardCharsets.UTF_8);
            AgentManifest manifest = parser.parse(content, fallbackName);
            return new DiscoveredAgent(directory, relativePath, manifest);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + manifestPath, e);
        }
    }

    /**
     * Rejects agents whose directories nest, because the outer jar would contain the inner agent
     * and both would be published under different coordinates.
     */
    private void verifyNoOverlap(List<DiscoveredAgent> agents) {
        for (DiscoveredAgent outer : agents) {
            for (DiscoveredAgent inner : agents) {
                if (outer == inner) {
                    continue;
                }
                if (isAncestor(outer.relativePath(), inner.relativePath())) {
                    throw new IllegalStateException(
                            "Overlapping agents: '%s' contains '%s'. Agent directories may not nest."
                                    .formatted(displayPath(outer.relativePath()), displayPath(inner.relativePath())));
                }
            }
        }
    }

    private static boolean isAncestor(String candidate, String descendant) {
        if (candidate.isEmpty()) {
            return !descendant.isEmpty();
        }
        return descendant.startsWith(candidate + "/");
    }

    private static String displayPath(String relativePath) {
        return relativePath.isEmpty() ? "<repository root>" : relativePath;
    }

    private static boolean isIgnored(Path repositoryRoot, Path manifestPath) {
        Path relative = repositoryRoot.relativize(manifestPath);
        for (Path segment : relative) {
            if (IGNORED_DIRECTORIES.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private static String normalise(String path) {
        return path.replace('\\', '/');
    }
}
