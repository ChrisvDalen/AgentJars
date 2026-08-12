package com.agentjars.staticsite;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.agentjars.catalog.CatalogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Checks the prerendered site: what it contains, that its links carry the base path, and that the
 * pages needing a server degrade honestly rather than rendering a form that cannot work.
 */
@SpringBootTest
@ActiveProfiles("test")
class StaticSiteGeneratorTest {

    private static final String BASE_PATH = "/AgentJars";
    private static final String CODE_REVIEWER = "agentjars__example-agents__code-reviewer";

    @Autowired
    private StaticSiteGenerator generator;

    @Autowired
    private CatalogService catalog;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("writes a page per agent plus the shared pages and assets")
    void writesEveryPage(@TempDir Path output) {
        generator.generate(output, BASE_PATH);

        assertThat(output.resolve("index.html")).exists();
        assertThat(output.resolve("agents/index.html")).exists();
        assertThat(output.resolve("docs/index.html")).exists();
        assertThat(output.resolve("deploy/index.html")).exists();
        assertThat(output.resolve("404.html")).exists();
        assertThat(output.resolve(".nojekyll")).exists();
        assertThat(output.resolve("css/agentjars.css")).exists();
        assertThat(output.resolve("js/search.js")).exists();
        assertThat(output.resolve("img/jar.svg")).exists();

        assertThat(output.resolve("agents/" + CODE_REVIEWER + "/index.html")).exists();
        assertThat(output.resolve("agents/" + CODE_REVIEWER + "/2026_07_28-9c1f4ab/files/index.html"))
                .exists();
    }

    @Test
    @DisplayName("every generated path is one a static host can serve")
    void everyAgentIsReachable(@TempDir Path output) {
        generator.generate(output, BASE_PATH);

        assertThat(generator.pagePaths())
                .isNotEmpty()
                .allSatisfy(path -> assertThat(output.resolve(path)).exists());
    }

    @Test
    @DisplayName("absolute links carry the base path the site is served under")
    void linksCarryBasePath(@TempDir Path output) throws IOException {
        generator.generate(output, BASE_PATH);

        String home = read(output.resolve("index.html"));
        assertThat(home)
                .contains("href=\"/AgentJars/css/agentjars.css\"")
                .contains("href=\"/AgentJars/agents\"")
                .contains("href=\"/AgentJars/agents/" + CODE_REVIEWER + "\"");
        assertThat(home).doesNotContain("href=\"/css/agentjars.css\"");
    }

    @Test
    @DisplayName("a site served from the domain root gets unprefixed links")
    void emptyBasePath(@TempDir Path output) throws IOException {
        generator.generate(output, "");

        assertThat(read(output.resolve("index.html")))
                .contains("href=\"/css/agentjars.css\"")
                .doesNotContain("href=\"/AgentJars/");
    }

    @Test
    @DisplayName("the listing carries every agent and the data the browser filters on")
    void listingIsFilterableInTheBrowser(@TempDir Path output) throws IOException {
        generator.generate(output, BASE_PATH);

        String listing = read(output.resolve("agents/index.html"));
        assertThat(listing).contains("data-client-search=\"true\"");
        assertThat(listing).contains("data-tags=\"review quality testing\"");
        assertThat(countOccurrences(listing, "data-agent ")).isEqualTo(catalog.findAll().size());
    }

    @Test
    @DisplayName("the detail page keeps a copyable snippet for every build tool")
    void detailPageKeepsSnippets(@TempDir Path output) throws IOException {
        String detail = generateAndRead(output, "agents/" + CODE_REVIEWER + "/index.html");

        assertThat(detail)
                .contains("&lt;artifactId&gt;" + CODE_REVIEWER + "&lt;/artifactId&gt;")
                .contains("implementation(&quot;com.agentjars:" + CODE_REVIEWER)
                .contains("libraryDependencies")
                .contains("data-copy");
    }

    @Test
    @DisplayName("the publish page explains itself instead of showing a form that cannot work")
    void publishPageHasNoForm(@TempDir Path output) throws IOException {
        String deploy = generateAndRead(output, "deploy/index.html");

        assertThat(deploy)
                .contains("read-only copy of the registry")
                .doesNotContain("method=\"post\"");
    }

    @Test
    @DisplayName("the JSON API is written as files a static host can serve")
    void writesJsonApi(@TempDir Path output) throws IOException {
        generator.generate(output, BASE_PATH);

        JsonNode index = objectMapper.readTree(read(output.resolve("api/agents.json")));
        assertThat(index.path("groupId").asString()).isEqualTo("com.agentjars");
        assertThat(index.path("count").asInt()).isEqualTo(catalog.findAll().size());

        JsonNode detail = objectMapper.readTree(
                read(output.resolve("api/agents/" + CODE_REVIEWER + ".json")));
        assertThat(detail.path("name").asString()).isEqualTo("Code Reviewer");
        assertThat(detail.path("versions")).hasSize(3);
        assertThat(detail.path("dependencies").path("maven").asString())
                .contains("<groupId>com.agentjars</groupId>");
    }

    @Test
    @DisplayName("regenerating clears pages that no longer exist")
    void regenerationClearsStaleFiles(@TempDir Path output) throws IOException {
        Files.createDirectories(output);
        Path stale = output.resolve("agents/removed-agent/index.html");
        Files.createDirectories(stale.getParent());
        Files.writeString(stale, "an agent that was unpublished");

        generator.generate(output, BASE_PATH);

        assertThat(stale).doesNotExist();
        assertThat(output.resolve("index.html")).exists();
    }

    private String generateAndRead(Path output, String path) throws IOException {
        generator.generate(output, BASE_PATH);
        return read(output.resolve(path));
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
