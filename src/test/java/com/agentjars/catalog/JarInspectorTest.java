package com.agentjars.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import com.agentjars.config.AgentJarsProperties;
import com.agentjars.model.JarFileEntry;
import com.agentjars.packaging.AgentManifestParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JarInspectorTest {

    private final JarInspector inspector = new JarInspector(
            new AgentJarsProperties(null, null, null, null, null), new AgentManifestParser());

    @Test
    @DisplayName("lists the files in a jar with their sizes")
    void listsEntries() throws IOException {
        byte[] jar = jar(Map.of(
                "META-INF/agents/reviewer/AGENT.md", "---\nname: Reviewer\n---\nBody.",
                "META-INF/agents/reviewer/checklist.md", "- one"));

        var entries = inspector.listEntries(jar);

        assertThat(entries).extracting(JarFileEntry::path)
                .contains("META-INF/agents/reviewer/AGENT.md", "META-INF/agents/reviewer/checklist.md");
        assertThat(entries).filteredOn(entry -> entry.path().endsWith("checklist.md"))
                .singleElement()
                .satisfies(entry -> assertThat(entry.size()).isEqualTo(5));
    }

    @Test
    @DisplayName("parses the packaged AGENT.md wherever it sits under the agents root")
    void readsManifest() throws IOException {
        byte[] jar = jar(Map.of("META-INF/agents/reviewer/AGENT.md", """
                ---
                name: Code Reviewer
                description: Reviews a diff.
                tags: [review]
                ---
                """));

        assertThat(inspector.readManifest(jar, "fallback")).hasValueSatisfying(manifest -> {
            assertThat(manifest.name()).isEqualTo("Code Reviewer");
            assertThat(manifest.tags()).containsExactly("review");
        });
    }

    @Test
    @DisplayName("reads one file out of a jar by path")
    void readsSingleFile() throws IOException {
        byte[] jar = jar(Map.of("META-INF/agents/reviewer/checklist.md", "- read the diff"));

        assertThat(inspector.readText(jar, "META-INF/agents/reviewer/checklist.md"))
                .contains("- read the diff");
        assertThat(inspector.readText(jar, "META-INF/agents/reviewer/missing.md")).isEmpty();
    }

    @Test
    @DisplayName("returns nothing for an empty or unreadable jar")
    void emptyJar() {
        assertThat(inspector.listEntries(new byte[0])).isEmpty();
        assertThat(inspector.listEntries(null)).isEmpty();
        assertThat(inspector.readManifest(new byte[0], "fallback")).isEmpty();
        assertThat(inspector.readText(null, "anything")).isEmpty();
    }

    @Test
    @DisplayName("finds no manifest in a jar that carries none")
    void jarWithoutManifest() throws IOException {
        byte[] jar = jar(Map.of("META-INF/agents/reviewer/notes.md", "no manifest here"));

        assertThat(inspector.readManifest(jar, "fallback")).isEmpty();
    }

    private static byte[] jar(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(buffer)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                jar.putNextEntry(new ZipEntry(entry.getKey()));
                jar.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
        }
        return buffer.toByteArray();
    }
}
