package com.agentjars.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CoordinatesTest {

    @ParameterizedTest(name = "{0}/{1} + {2} -> {3}")
    @CsvSource({
            "myorg,       myrepo,     agents/myskill, myorg__myrepo__myskill",
            "myorg,       myrepo,     myskill,        myorg__myrepo__myskill",
            "MyOrg,       My-Repo,    Agents/Review,  myorg__my-repo__review",
            "my.org,      my repo,    agents/a b,     myorg__myrepo__ab",
            "myorg,       myrepo,     agents/a/b,     myorg__myrepo__a-b",
    })
    @DisplayName("derives the artifactId from the source repository and agent path")
    void derivesArtifactId(String org, String repo, String path, String expected) {
        assertThat(Coordinates.artifactId(org, repo, path)).isEqualTo(expected);
    }

    @Test
    @DisplayName("an agent at the repository root drops the agent segment")
    void rootAgentHasNoAgentSegment() {
        assertThat(Coordinates.artifactId("myorg", "myrepo", "")).isEqualTo("myorg__myrepo");
        assertThat(Coordinates.artifactId("myorg", "myrepo", "agents")).isEqualTo("myorg__myrepo");
    }

    @Test
    @DisplayName("moving an agent between reviewer/ and agents/reviewer/ keeps its coordinate")
    void agentsPrefixIsOptional() {
        assertThat(Coordinates.artifactId("myorg", "myrepo", "reviewer"))
                .isEqualTo(Coordinates.artifactId("myorg", "myrepo", "agents/reviewer"));
    }

    @Test
    @DisplayName("rejects a repository reference that normalises to nothing")
    void rejectsEmptySegments() {
        assertThatThrownBy(() -> Coordinates.artifactId("...", "myrepo", "reviewer"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("reads the source repository back out of the artifactId")
    void parsesSourceOutOfArtifactId() {
        Coordinates coordinates = Coordinates.of(
                "com.agentjars", "myorg__myrepo__reviewer", "2026_07_28-9c1f4ab");

        assertThat(coordinates.org()).isEqualTo("myorg");
        assertThat(coordinates.repo()).isEqualTo("myrepo");
        assertThat(coordinates.agent()).isEqualTo("reviewer");
    }

    @Test
    @DisplayName("builds the repository path and file names Maven Central serves")
    void buildsRepositoryPaths() {
        Coordinates coordinates = Coordinates.of(
                "com.agentjars", "myorg__myrepo__reviewer", "2026_07_28-9c1f4ab");

        assertThat(coordinates.repositoryPath())
                .isEqualTo("com/agentjars/myorg__myrepo__reviewer/2026_07_28-9c1f4ab");
        assertThat(coordinates.jarFileName())
                .isEqualTo("myorg__myrepo__reviewer-2026_07_28-9c1f4ab.jar");
        assertThat(coordinates.pomFileName())
                .isEqualTo("myorg__myrepo__reviewer-2026_07_28-9c1f4ab.pom");
    }

    @Test
    @DisplayName("omits the version from the repository path when there is none")
    void repositoryPathWithoutVersion() {
        assertThat(Coordinates.of("com.agentjars", "myorg__myrepo", null).repositoryPath())
                .isEqualTo("com/agentjars/myorg__myrepo");
    }
}
