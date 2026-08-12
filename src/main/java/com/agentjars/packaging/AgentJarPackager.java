package com.agentjars.packaging;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.agentjars.config.AgentJarsProperties;
import com.agentjars.model.Coordinates;
import com.agentjars.model.DiscoveredAgent;
import com.agentjars.model.GitHubRepoRef;
import org.springframework.stereotype.Component;

/**
 * Turns a discovered agent into the artifacts Maven Central expects: the agent jar itself plus
 * the empty sources and javadoc jars and the pom that Central validates.
 *
 * <p>The agent files land under {@code META-INF/agents/<slug>/} so that a consumer can extract
 * every agent on the classpath into one directory without collisions.
 */
@Component
public class AgentJarPackager {

    private final AgentJarsProperties properties;
    private final LicenseResolver licenseResolver;

    public AgentJarPackager(AgentJarsProperties properties, LicenseResolver licenseResolver) {
        this.properties = properties;
        this.licenseResolver = licenseResolver;
    }

    /**
     * Writes the jar, sources jar, javadoc jar and pom for one agent into {@code outputDirectory}.
     *
     * @return the files that were written, in upload order
     */
    public List<Path> packageAgent(
            DiscoveredAgent agent,
            Coordinates coordinates,
            GitHubRepoRef repo,
            String license,
            Path outputDirectory) {
        try {
            Files.createDirectories(outputDirectory);
            String base = coordinates.artifactId() + "-" + coordinates.version();

            Path jar = outputDirectory.resolve(base + ".jar");
            writeAgentJar(agent, coordinates, repo, jar);

            Path sources = outputDirectory.resolve(base + "-sources.jar");
            writePlaceholderJar(coordinates, sources,
                    "AgentJars package the agent definition itself; there are no Java sources.");

            Path javadoc = outputDirectory.resolve(base + "-javadoc.jar");
            writePlaceholderJar(coordinates, javadoc,
                    "AgentJars package the agent definition itself; there is no javadoc.");

            Path pom = outputDirectory.resolve(base + ".pom");
            Files.writeString(pom, pom(agent, coordinates, repo, license), StandardCharsets.UTF_8);

            return List.of(pom, jar, sources, javadoc);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not package " + coordinates, e);
        }
    }

    /**
     * Bundles previously packaged files into the single zip the Central Portal upload API takes.
     * Files are laid out at their repository path, which is what the portal validates against.
     */
    public Path bundle(Coordinates coordinates, List<Path> files, Path outputDirectory) {
        Path bundle = outputDirectory.resolve(
                coordinates.artifactId() + "-" + coordinates.version() + "-bundle.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(bundle))) {
            for (Path file : files) {
                String entryName = coordinates.repositoryPath() + "/" + file.getFileName();
                zip.putNextEntry(new ZipEntry(entryName));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not build bundle for " + coordinates, e);
        }
        return bundle;
    }

    private void writeAgentJar(
            DiscoveredAgent agent, Coordinates coordinates, GitHubRepoRef repo, Path target)
            throws IOException {
        String prefix = properties.agentsRoot() + "/" + agent.slug() + "/";
        try (OutputStream out = Files.newOutputStream(target);
             JarOutputStream jar = new JarOutputStream(out, manifest(coordinates, repo, agent))) {
            for (Path file : filesUnder(agent.directory())) {
                String relative = agent.directory().relativize(file).toString().replace('\\', '/');
                jar.putNextEntry(new ZipEntry(prefix + relative));
                Files.copy(file, jar);
                jar.closeEntry();
            }
            jar.putNextEntry(new ZipEntry(properties.agentsRoot() + "/agentjars.index"));
            jar.write((agent.slug() + "=" + prefix + AgentScanner.MANIFEST_FILE + "\n")
                    .getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
    }

    private void writePlaceholderJar(Coordinates coordinates, Path target, String note)
            throws IOException {
        try (OutputStream out = Files.newOutputStream(target);
             JarOutputStream jar = new JarOutputStream(out, minimalManifest(coordinates))) {
            jar.putNextEntry(new ZipEntry("README.txt"));
            jar.write(note.getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
    }

    private Manifest manifest(Coordinates coordinates, GitHubRepoRef repo, DiscoveredAgent agent) {
        Manifest manifest = minimalManifest(coordinates);
        Attributes attributes = manifest.getMainAttributes();
        attributes.putValue("Agent-Name", agent.manifest().name());
        attributes.putValue("Agent-Root", properties.agentsRoot() + "/" + agent.slug());
        attributes.putValue("Agent-Source-Repository", repo.browseUrl());
        if (!agent.relativePath().isBlank()) {
            attributes.putValue("Agent-Source-Path", agent.relativePath());
        }
        if (agent.manifest().model() != null) {
            attributes.putValue("Agent-Model", agent.manifest().model());
        }
        return manifest;
    }

    private Manifest minimalManifest(Coordinates coordinates) {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Created-By", "AgentJars");
        attributes.putValue("Implementation-Title", coordinates.artifactId());
        attributes.putValue("Implementation-Version", coordinates.version());
        return manifest;
    }

    private String pom(
            DiscoveredAgent agent, Coordinates coordinates, GitHubRepoRef repo, String license) {
        String description = agent.manifest().hasDescription()
                ? escape(agent.manifest().description())
                : "Agent " + escape(agent.manifest().name()) + " packaged from " + repo.slug();
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                  <packaging>jar</packaging>
                  <name>%s</name>
                  <description>%s</description>
                  <url>%s</url>
                  <licenses>
                    <license>
                      <name>%s</name>
                      <url>%s</url>
                    </license>
                  </licenses>
                  <developers>
                    <developer>
                      <name>%s</name>
                      <url>https://github.com/%s</url>
                    </developer>
                  </developers>
                  <scm>
                    <connection>scm:git:%s</connection>
                    <developerConnection>scm:git:%s</developerConnection>
                    <url>%s</url>
                  </scm>
                </project>
                """.formatted(
                coordinates.groupId(),
                coordinates.artifactId(),
                coordinates.version(),
                escape(agent.manifest().name()),
                description,
                repo.browseUrl(),
                escape(license),
                licenseResolver.urlFor(license),
                escape(repo.org()),
                escape(repo.org()),
                repo.cloneUrl(),
                repo.cloneUrl(),
                repo.browseUrl());
    }

    private static List<Path> filesUnder(Path directory) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(files::add);
        }
        return files;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
