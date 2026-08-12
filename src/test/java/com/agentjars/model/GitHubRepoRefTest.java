package com.agentjars.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GitHubRepoRefTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "myorg/my-agents",
            "https://github.com/myorg/my-agents",
            "https://www.github.com/myorg/my-agents",
            "https://github.com/myorg/my-agents.git",
            "https://github.com/myorg/my-agents/",
            "git@github.com:myorg/my-agents.git",
    })
    @DisplayName("accepts the shapes people paste into the deploy form")
    void parsesCommonShapes(String input) {
        GitHubRepoRef ref = GitHubRepoRef.parse(input);

        assertThat(ref.org()).isEqualTo("myorg");
        assertThat(ref.repo()).isEqualTo("my-agents");
        assertThat(ref.cloneUrl()).isEqualTo("https://github.com/myorg/my-agents.git");
        assertThat(ref.browseUrl()).isEqualTo("https://github.com/myorg/my-agents");
    }

    @Test
    @DisplayName("keeps the branch out of a tree url")
    void parsesTreeUrl() {
        GitHubRepoRef ref = GitHubRepoRef.parse("https://github.com/myorg/my-agents/tree/main");

        assertThat(ref.slug()).isEqualTo("myorg/my-agents");
        assertThat(ref.ref()).isEqualTo("main");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "not a repo",
            "https://gitlab.com/myorg/my-agents",
            "myorg",
    })
    @DisplayName("rejects anything that is not a public GitHub repository")
    void rejectsInvalidInput(String input) {
        assertThatThrownBy(() -> GitHubRepoRef.parse(input))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects null")
    void rejectsNull() {
        assertThatThrownBy(() -> GitHubRepoRef.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
