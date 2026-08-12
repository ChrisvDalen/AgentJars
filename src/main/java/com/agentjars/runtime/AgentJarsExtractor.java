package com.agentjars.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Extracts every AgentJar on the classpath into a directory an agent runtime can read.
 *
 * <p>This is the consumer half of the registry: an application declares AgentJars as ordinary
 * dependencies and calls this once at startup, so agent definitions are versioned and resolved by
 * the build tool instead of copied between repositories.
 *
 * <pre>{@code
 * Path agents = AgentJarsExtractor.create().extractTo(Path.of("build/agents"));
 * }</pre>
 *
 * <p>The class deliberately has no dependencies beyond the JDK so it can be lifted into a
 * standalone library without dragging the web application along.
 */
public final class AgentJarsExtractor {

    private static final String DEFAULT_ROOT = "META-INF/agents";
    private static final String INDEX_FILE = "agentjars.index";

    private final ClassLoader classLoader;
    private final String agentsRoot;

    private AgentJarsExtractor(ClassLoader classLoader, String agentsRoot) {
        this.classLoader = classLoader;
        this.agentsRoot = agentsRoot;
    }

    /** An extractor reading {@code META-INF/agents} from the current thread's classloader. */
    public static AgentJarsExtractor create() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return new AgentJarsExtractor(
                loader == null ? AgentJarsExtractor.class.getClassLoader() : loader, DEFAULT_ROOT);
    }

    public static AgentJarsExtractor create(ClassLoader classLoader, String agentsRoot) {
        return new AgentJarsExtractor(classLoader, agentsRoot);
    }

    /**
     * Copies every packaged agent into {@code targetDirectory}, preserving the
     * {@code <agent-slug>/...} layout so that agents from different jars stay separate.
     *
     * @return the directory that was written to
     */
    public Path extractTo(Path targetDirectory) {
        try {
            Files.createDirectories(targetDirectory);
            for (Path jar : agentJarsOnClasspath()) {
                extractJar(jar, targetDirectory);
            }
            return targetDirectory;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not extract agents to " + targetDirectory, e);
        }
    }

    /** The slugs of every agent found on the classpath, without extracting anything. */
    public List<String> listAgents() {
        List<String> slugs = new ArrayList<>();
        try {
            for (Path jar : agentJarsOnClasspath()) {
                try (JarFile jarFile = new JarFile(jar.toFile())) {
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String slug = slugOf(entry.getName());
                        if (slug != null && !slugs.contains(slug)) {
                            slugs.add(slug);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read agents from the classpath", e);
        }
        return List.copyOf(slugs);
    }

    /**
     * Locates the jars that carry an agents root. The index file written at packaging time is the
     * marker: it exists in every AgentJar and nowhere else.
     */
    private List<Path> agentJarsOnClasspath() throws IOException {
        List<Path> jars = new ArrayList<>();
        Enumeration<URL> resources = classLoader.getResources(agentsRoot + "/" + INDEX_FILE);
        while (resources.hasMoreElements()) {
            jarPathOf(resources.nextElement()).ifPresent(jars::add);
        }
        return jars;
    }

    private static java.util.Optional<Path> jarPathOf(URL resource) {
        String url = resource.toString();
        if (!url.startsWith("jar:file:")) {
            return java.util.Optional.empty();
        }
        int separator = url.indexOf("!/");
        if (separator < 0) {
            return java.util.Optional.empty();
        }
        String file = url.substring("jar:file:".length(), separator);
        return java.util.Optional.of(Path.of(java.net.URLDecoder.decode(file,
                java.nio.charset.StandardCharsets.UTF_8)));
    }

    private void extractJar(Path jar, Path targetDirectory) throws IOException {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String relative = relativeAgentPath(entry.getName());
                if (relative == null || entry.isDirectory()) {
                    continue;
                }
                Path target = resolveSafely(targetDirectory, relative);
                Files.createDirectories(target.getParent());
                try (InputStream in = jarFile.getInputStream(entry)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** The path under the agents root, or null for entries outside it and for the index file. */
    private String relativeAgentPath(String entryName) {
        String prefix = agentsRoot + "/";
        if (!entryName.startsWith(prefix)) {
            return null;
        }
        String relative = entryName.substring(prefix.length());
        return relative.isEmpty() || relative.equals(INDEX_FILE) ? null : relative;
    }

    private String slugOf(String entryName) {
        String relative = relativeAgentPath(entryName);
        if (relative == null) {
            return null;
        }
        int slash = relative.indexOf('/');
        return slash <= 0 ? null : relative.substring(0, slash);
    }

    /** Refuses entry names that would escape the target directory. */
    private static Path resolveSafely(Path targetDirectory, String relative) {
        Path normalisedTarget = targetDirectory.toAbsolutePath().normalize();
        Path resolved = normalisedTarget.resolve(relative).normalize();
        if (!resolved.startsWith(normalisedTarget)) {
            throw new IllegalStateException("Refusing to write outside the target directory: " + relative);
        }
        return resolved;
    }
}
