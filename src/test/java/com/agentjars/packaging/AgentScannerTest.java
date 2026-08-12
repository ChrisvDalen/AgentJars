package com.agentjars.packaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.agentjars.model.DiscoveredAgent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentScannerTest {

    private final AgentScanner scanner = new AgentScanner(new AgentManifestParser());

    @Test
    @DisplayName("finds every agent under agents/")
    void findsAgentsUnderAgentsDirectory(@TempDir Path repo) throws IOException {
        writeAgent(repo.resolve("agents/reviewer"), "Reviewer");
        writeAgent(repo.resolve("agents/planner"), "Planner");

        List<DiscoveredAgent> agents = scanner.scan(repo);

        assertThat(agents).hasSize(2);
        assertThat(agents).extracting(DiscoveredAgent::relativePath)
                .containsExactlyInAnyOrder("agents/planner", "agents/reviewer");
        assertThat(agents).extracting(agent -> agent.manifest().name())
                .containsExactlyInAnyOrder("Planner", "Reviewer");
    }

    @Test
    @DisplayName("finds an agent defined at the repository root")
    void findsRootAgent(@TempDir Path repo) throws IOException {
        writeAgent(repo, "Root Agent");

        List<DiscoveredAgent> agents = scanner.scan(repo);

        assertThat(agents).hasSize(1);
        assertThat(agents.getFirst().relativePath()).isEmpty();
    }

    @Test
    @DisplayName("rejects an agent nested inside another agent")
    void rejectsNestedAgents(@TempDir Path repo) throws IOException {
        writeAgent(repo.resolve("agents/reviewer"), "Reviewer");
        writeAgent(repo.resolve("agents/reviewer/inner"), "Inner");

        assertThatThrownBy(() -> scanner.scan(repo))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Overlapping agents");
    }

    @Test
    @DisplayName("rejects a root agent that contains other agents")
    void rejectsRootAgentWithChildren(@TempDir Path repo) throws IOException {
        writeAgent(repo, "Root");
        writeAgent(repo.resolve("agents/reviewer"), "Reviewer");

        assertThatThrownBy(() -> scanner.scan(repo))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("<repository root>");
    }

    @Test
    @DisplayName("skips build output and version control directories")
    void skipsIgnoredDirectories(@TempDir Path repo) throws IOException {
        writeAgent(repo.resolve("agents/reviewer"), "Reviewer");
        writeAgent(repo.resolve("target/classes/copied"), "Copied");
        writeAgent(repo.resolve("node_modules/pkg/agent"), "Vendored");

        List<DiscoveredAgent> agents = scanner.scan(repo);

        assertThat(agents).extracting(DiscoveredAgent::relativePath).containsExactly("agents/reviewer");
    }

    @Test
    @DisplayName("returns nothing for a repository without agents")
    void noAgents(@TempDir Path repo) throws IOException {
        Files.createDirectories(repo.resolve("src"));
        Files.writeString(repo.resolve("README.md"), "no agents here");

        assertThat(scanner.scan(repo)).isEmpty();
    }

    @Test
    @DisplayName("names an agent after its directory when the front matter has no name")
    void namesAgentAfterDirectory(@TempDir Path repo) throws IOException {
        Path directory = repo.resolve("agents/incident-triage");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(AgentScanner.MANIFEST_FILE), "Body only.");

        List<DiscoveredAgent> agents = scanner.scan(repo);

        assertThat(agents.getFirst().manifest().name()).isEqualTo("incident-triage");
        assertThat(agents.getFirst().slug()).isEqualTo("incident-triage");
    }

    private static void writeAgent(Path directory, String name) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(AgentScanner.MANIFEST_FILE), """
                ---
                name: %s
                description: An agent used in a test.
                ---
                Instructions.
                """.formatted(name));
    }
}
