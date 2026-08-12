package com.agentjars.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentjars.model.AgentManifest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AgentManifestParserTest {

    private final AgentManifestParser parser = new AgentManifestParser();

    @Test
    @DisplayName("reads scalars and inline lists out of the front matter")
    void readsFrontMatter() {
        AgentManifest manifest = parser.parse("""
                ---
                name: Code Reviewer
                description: Reviews a diff for correctness bugs.
                model: reasoning
                tools: [read_file, grep, run_tests]
                tags: [review, quality]
                license: Apache-2.0
                ---

                # Code Reviewer

                Read the diff before the surrounding code.
                """, "fallback");

        assertThat(manifest.name()).isEqualTo("Code Reviewer");
        assertThat(manifest.description()).isEqualTo("Reviews a diff for correctness bugs.");
        assertThat(manifest.model()).isEqualTo("reasoning");
        assertThat(manifest.tools()).containsExactly("read_file", "grep", "run_tests");
        assertThat(manifest.tags()).containsExactly("review", "quality");
        assertThat(manifest.license()).isEqualTo("Apache-2.0");
    }

    @Test
    @DisplayName("reads block lists as well as inline lists")
    void readsBlockLists() {
        AgentManifest manifest = parser.parse("""
                ---
                name: Release Notes
                tools:
                  - git_log
                  - read_file
                tags:
                  - release
                ---
                Body.
                """, "fallback");

        assertThat(manifest.tools()).containsExactly("git_log", "read_file");
        assertThat(manifest.tags()).containsExactly("release");
    }

    @Test
    @DisplayName("strips the quotes people wrap descriptions in")
    void stripsQuotes() {
        AgentManifest manifest = parser.parse("""
                ---
                name: "Quoted Agent"
                description: 'Single quoted, with a colon: like this.'
                ---
                """, "fallback");

        assertThat(manifest.name()).isEqualTo("Quoted Agent");
        assertThat(manifest.description()).isEqualTo("Single quoted, with a colon: like this.");
    }

    @Test
    @DisplayName("falls back to the first prose paragraph when there is no description")
    void fallsBackToFirstParagraph() {
        AgentManifest manifest = parser.parse("""
                ---
                name: Migration Planner
                ---

                # Migration Planner

                Plans a dependency upgrade so the build stays
                green between steps.

                A second paragraph that should not be used.
                """, "fallback");

        assertThat(manifest.description())
                .isEqualTo("Plans a dependency upgrade so the build stays green between steps.");
    }

    @Test
    @DisplayName("falls back to the supplied name when the front matter has none")
    void fallsBackToDirectoryName() {
        AgentManifest manifest = parser.parse("Just a body, no front matter.", "code-reviewer");

        assertThat(manifest.name()).isEqualTo("code-reviewer");
        assertThat(manifest.description()).isEqualTo("Just a body, no front matter.");
        assertThat(manifest.tools()).isEmpty();
    }

    @Test
    @DisplayName("ignores a leading '---' that is not front matter")
    void ignoresHorizontalRule() {
        AgentManifest manifest = parser.parse("""
                # Heading first

                ---

                name: not front matter
                """, "fallback");

        assertThat(manifest.name()).isEqualTo("fallback");
    }

    @Test
    @DisplayName("survives an unterminated front matter block")
    void unterminatedFrontMatter() {
        AgentManifest manifest = parser.parse("""
                ---
                name: Half Written
                tags: [a, b]
                """, "fallback");

        assertThat(manifest.name()).isEqualTo("Half Written");
        assertThat(manifest.tags()).containsExactly("a", "b");
    }

    @Test
    @DisplayName("treats a comma separated string as a list")
    void commaSeparatedString() {
        AgentManifest manifest = parser.parse("""
                ---
                name: Agent
                tags: review, quality, testing
                ---
                """, "fallback");

        assertThat(manifest.tags()).containsExactly("review", "quality", "testing");
    }
}
