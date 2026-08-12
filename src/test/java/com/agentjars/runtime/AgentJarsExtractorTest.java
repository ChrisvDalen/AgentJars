package com.agentjars.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentJarsExtractorTest {

    private static final String ROOT = "META-INF/agents";

    @Test
    @DisplayName("extracts agents from every AgentJar on the classpath")
    void extractsFromClasspath(@TempDir Path work) throws IOException {
        Path first = agentJar(work.resolve("first.jar"), Map.of(
                ROOT + "/reviewer/AGENT.md", "reviewer instructions",
                ROOT + "/reviewer/checklist.md", "checklist"));
        Path second = agentJar(work.resolve("second.jar"), Map.of(
                ROOT + "/planner/AGENT.md", "planner instructions"));
        Path target = work.resolve("out");

        try (URLClassLoader loader = classLoaderFor(first, second)) {
            AgentJarsExtractor.create(loader, ROOT).extractTo(target);
        }

        assertThat(target.resolve("reviewer/AGENT.md")).hasContent("reviewer instructions");
        assertThat(target.resolve("reviewer/checklist.md")).hasContent("checklist");
        assertThat(target.resolve("planner/AGENT.md")).hasContent("planner instructions");
    }

    @Test
    @DisplayName("does not extract the index marker itself")
    void skipsIndexFile(@TempDir Path work) throws IOException {
        Path jar = agentJar(work.resolve("agents.jar"), Map.of(ROOT + "/reviewer/AGENT.md", "x"));
        Path target = work.resolve("out");

        try (URLClassLoader loader = classLoaderFor(jar)) {
            AgentJarsExtractor.create(loader, ROOT).extractTo(target);
        }

        assertThat(target.resolve("agentjars.index")).doesNotExist();
    }

    @Test
    @DisplayName("ignores jars on the classpath that carry no agents")
    void ignoresUnrelatedJars(@TempDir Path work) throws IOException {
        Path unrelated = work.resolve("library.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(unrelated))) {
            jar.putNextEntry(new ZipEntry("com/example/Thing.class"));
            jar.write("not an agent".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        Path target = work.resolve("out");

        try (URLClassLoader loader = classLoaderFor(unrelated)) {
            AgentJarsExtractor.create(loader, ROOT).extractTo(target);
        }

        assertThat(target).isEmptyDirectory();
    }

    @Test
    @DisplayName("lists agent slugs without writing anything")
    void listsAgents(@TempDir Path work) throws IOException {
        Path jar = agentJar(work.resolve("agents.jar"), Map.of(
                ROOT + "/reviewer/AGENT.md", "a",
                ROOT + "/planner/AGENT.md", "b"));

        try (URLClassLoader loader = classLoaderFor(jar)) {
            assertThat(AgentJarsExtractor.create(loader, ROOT).listAgents())
                    .containsExactlyInAnyOrder("reviewer", "planner");
        }
    }

    @Test
    @DisplayName("refuses entries that would escape the target directory")
    void refusesPathTraversal(@TempDir Path work) throws IOException {
        Path jar = agentJar(work.resolve("evil.jar"), Map.of(
                ROOT + "/../../escaped.md", "should not be written"));
        Path target = work.resolve("out");

        try (URLClassLoader loader = classLoaderFor(jar)) {
            AgentJarsExtractor extractor = AgentJarsExtractor.create(loader, ROOT);
            assertThatThrownBy(() -> extractor.extractTo(target))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("outside the target directory");
        }
        assertThat(work.resolve("escaped.md")).doesNotExist();
    }

    /** Writes a jar with the index marker plus the given entries. */
    private static Path agentJar(Path target, Map<String, String> entries) throws IOException {
        try (OutputStream out = Files.newOutputStream(target);
             JarOutputStream jar = new JarOutputStream(out)) {
            jar.putNextEntry(new ZipEntry(ROOT + "/agentjars.index"));
            jar.write("index".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                jar.putNextEntry(new ZipEntry(entry.getKey()));
                jar.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
        }
        return target;
    }

    private static URLClassLoader classLoaderFor(Path... jars) throws IOException {
        URL[] urls = new URL[jars.length];
        for (int i = 0; i < jars.length; i++) {
            urls[i] = jars[i].toUri().toURL();
        }
        return new URLClassLoader(urls, null);
    }
}
