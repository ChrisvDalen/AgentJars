package com.agentjars.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

import com.agentjars.config.AgentJarsProperties;
import com.agentjars.model.Coordinates;
import com.agentjars.model.DiscoveredAgent;
import com.agentjars.model.GitHubRepoRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentJarPackagerTest {

    private static final Coordinates COORDINATES =
            Coordinates.of("com.agentjars", "myorg__myrepo__reviewer", "2026_07_28-9c1f4ab");
    private static final GitHubRepoRef REPO = GitHubRepoRef.parse("myorg/myrepo");

    private final AgentJarsProperties properties =
            new AgentJarsProperties(null, null, null, null, null);
    private final AgentJarPackager packager =
            new AgentJarPackager(properties, new LicenseResolver());
    private final AgentScanner scanner = new AgentScanner(new AgentManifestParser());

    private DiscoveredAgent agent;

    @BeforeEach
    void discoverAgent(@TempDir Path repo) throws IOException {
        Path directory = repo.resolve("agents/reviewer");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(AgentScanner.MANIFEST_FILE), """
                ---
                name: Reviewer
                description: Reviews a diff.
                model: reasoning
                ---
                Instructions.
                """);
        Files.writeString(directory.resolve("checklist.md"), "- read the diff first");
        agent = scanner.scan(repo).getFirst();
    }

    @Test
    @DisplayName("places the agent files under the agents root, named after the agent")
    void jarLayout(@TempDir Path output) throws IOException {
        packager.packageAgent(agent, COORDINATES, REPO, "Apache-2.0", output);

        Path jar = output.resolve(COORDINATES.jarFileName());
        assertThat(jar).exists();
        assertThat(entriesOf(jar)).contains(
                "META-INF/agents/reviewer/AGENT.md",
                "META-INF/agents/reviewer/checklist.md",
                "META-INF/agents/agentjars.index");
    }

    @Test
    @DisplayName("records the agent's origin in the jar manifest")
    void jarManifest(@TempDir Path output) throws IOException {
        packager.packageAgent(agent, COORDINATES, REPO, "Apache-2.0", output);

        try (JarFile jar = new JarFile(output.resolve(COORDINATES.jarFileName()).toFile())) {
            var attributes = jar.getManifest().getMainAttributes();
            assertThat(attributes.getValue("Agent-Name")).isEqualTo("Reviewer");
            assertThat(attributes.getValue("Agent-Root")).isEqualTo("META-INF/agents/reviewer");
            assertThat(attributes.getValue("Agent-Source-Repository"))
                    .isEqualTo("https://github.com/myorg/myrepo");
            assertThat(attributes.getValue("Agent-Source-Path")).isEqualTo("agents/reviewer");
            assertThat(attributes.getValue("Agent-Model")).isEqualTo("reasoning");
        }
    }

    @Test
    @DisplayName("writes the four artifacts Maven Central validates")
    void writesCentralArtifacts(@TempDir Path output) {
        List<Path> files = packager.packageAgent(agent, COORDINATES, REPO, "MIT", output);

        assertThat(files).extracting(path -> path.getFileName().toString()).containsExactly(
                "myorg__myrepo__reviewer-2026_07_28-9c1f4ab.pom",
                "myorg__myrepo__reviewer-2026_07_28-9c1f4ab.jar",
                "myorg__myrepo__reviewer-2026_07_28-9c1f4ab-sources.jar",
                "myorg__myrepo__reviewer-2026_07_28-9c1f4ab-javadoc.jar");
        assertThat(files).allSatisfy(path -> assertThat(path).exists());
    }

    @Test
    @DisplayName("generates a pom carrying the license, scm and description Central requires")
    void generatesPom(@TempDir Path output) throws IOException {
        packager.packageAgent(agent, COORDINATES, REPO, "MIT", output);

        String pom = Files.readString(output.resolve(COORDINATES.pomFileName()), StandardCharsets.UTF_8);
        assertThat(pom)
                .contains("<groupId>com.agentjars</groupId>")
                .contains("<artifactId>myorg__myrepo__reviewer</artifactId>")
                .contains("<version>2026_07_28-9c1f4ab</version>")
                .contains("<name>Reviewer</name>")
                .contains("<description>Reviews a diff.</description>")
                .contains("<name>MIT</name>")
                .contains("<url>https://opensource.org/licenses/MIT</url>")
                .contains("scm:git:https://github.com/myorg/myrepo.git");
    }

    @Test
    @DisplayName("lays the bundle out at the coordinate's repository path")
    void bundleLayout(@TempDir Path output) throws IOException {
        List<Path> files = packager.packageAgent(agent, COORDINATES, REPO, "Apache-2.0", output);

        Path bundle = packager.bundle(COORDINATES, files, output);

        assertThat(entriesOf(bundle)).contains(
                "com/agentjars/myorg__myrepo__reviewer/2026_07_28-9c1f4ab/"
                        + "myorg__myrepo__reviewer-2026_07_28-9c1f4ab.jar");
    }

    @Test
    @DisplayName("escapes markup in metadata so the pom stays well formed")
    void escapesPomMetadata(@TempDir Path repo, @TempDir Path output) throws IOException {
        Path directory = repo.resolve("agents/tricky");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(AgentScanner.MANIFEST_FILE), """
                ---
                name: Tricky
                description: Handles <tags> & ampersands.
                ---
                """);
        DiscoveredAgent tricky = scanner.scan(repo).getFirst();

        packager.packageAgent(tricky, COORDINATES, REPO, "MIT", output);

        String pom = Files.readString(output.resolve(COORDINATES.pomFileName()), StandardCharsets.UTF_8);
        assertThat(pom).contains("Handles &lt;tags&gt; &amp; ampersands.");
    }

    private static List<String> entriesOf(Path archive) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            return zip.stream().map(entry -> entry.getName()).toList();
        }
    }
}
