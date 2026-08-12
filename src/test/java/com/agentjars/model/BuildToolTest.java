package com.agentjars.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BuildToolTest {

    private static final Coordinates COORDINATES =
            Coordinates.of("com.agentjars", "myorg__myrepo__reviewer", "2026_07_28-9c1f4ab");

    @Test
    @DisplayName("renders a Maven dependency block")
    void maven() {
        assertThat(BuildTool.MAVEN.snippet(COORDINATES))
                .contains("<groupId>com.agentjars</groupId>")
                .contains("<artifactId>myorg__myrepo__reviewer</artifactId>")
                .contains("<version>2026_07_28-9c1f4ab</version>");
    }

    @Test
    @DisplayName("renders Gradle and sbt one-liners")
    void gradleAndSbt() {
        assertThat(BuildTool.GRADLE.snippet(COORDINATES))
                .isEqualTo("implementation(\"com.agentjars:myorg__myrepo__reviewer:2026_07_28-9c1f4ab\")");
        assertThat(BuildTool.GRADLE_GROOVY.snippet(COORDINATES))
                .isEqualTo("implementation 'com.agentjars:myorg__myrepo__reviewer:2026_07_28-9c1f4ab'");
        assertThat(BuildTool.SBT.snippet(COORDINATES))
                .isEqualTo("libraryDependencies += \"com.agentjars\" % \"myorg__myrepo__reviewer\" % \"2026_07_28-9c1f4ab\"");
    }

    @Test
    @DisplayName("falls back to Maven for an unknown or missing build tool id")
    void unknownIdFallsBackToMaven() {
        assertThat(BuildTool.fromId(null)).isEqualTo(BuildTool.MAVEN);
        assertThat(BuildTool.fromId("")).isEqualTo(BuildTool.MAVEN);
        assertThat(BuildTool.fromId("bazel")).isEqualTo(BuildTool.MAVEN);
        assertThat(BuildTool.fromId("GRADLE")).isEqualTo(BuildTool.GRADLE);
        assertThat(BuildTool.fromId("gradle-groovy")).isEqualTo(BuildTool.GRADLE_GROOVY);
    }

    @Test
    @DisplayName("renders LATEST when the coordinate carries no version")
    void missingVersion() {
        Coordinates unversioned = Coordinates.of("com.agentjars", "myorg__myrepo", null);
        assertThat(BuildTool.GRADLE.snippet(unversioned)).endsWith(":LATEST\")");
    }
}
