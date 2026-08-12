package com.agentjars.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.agentjars.model.AgentJarVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class MavenCentralClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("reads artifacts out of a search response")
    void parsesArtifacts() {
        JsonNode response = json("""
                {"response": {"numFound": 2, "docs": [
                  {"a": "myorg__myrepo__reviewer", "latestVersion": "2026_07_28-9c1f4ab",
                   "versionCount": 3, "timestamp": 1785235200000},
                  {"a": "myorg__myrepo__planner", "latestVersion": "2026_06_11-4de20b7",
                   "versionCount": 1, "timestamp": 1781510400000}
                ]}}
                """);

        List<MavenCentralClient.ArtifactSummary> artifacts = MavenCentralClient.parseArtifacts(response);

        assertThat(artifacts).extracting(MavenCentralClient.ArtifactSummary::artifactId)
                .containsExactly("myorg__myrepo__planner", "myorg__myrepo__reviewer");
        assertThat(artifacts.getLast().versionCount()).isEqualTo(3);
        assertThat(artifacts.getLast().lastUpdated()).isNotNull();
    }

    @Test
    @DisplayName("reads versions newest first and pulls the commit out of each")
    void parsesVersions() {
        JsonNode response = json("""
                {"response": {"docs": [
                  {"v": "2026_04_02-1aa77c3", "timestamp": 1775088000000},
                  {"v": "2026_07_28-9c1f4ab", "timestamp": 1785235200000}
                ]}}
                """);

        List<AgentJarVersion> versions = MavenCentralClient.parseVersions(response);

        assertThat(versions).extracting(AgentJarVersion::version)
                .containsExactly("2026_07_28-9c1f4ab", "2026_04_02-1aa77c3");
        assertThat(versions.getFirst().commit()).isEqualTo("9c1f4ab");
    }

    @Test
    @DisplayName("treats a missing or malformed response as an empty catalog")
    void toleratesMissingFields() {
        assertThat(MavenCentralClient.parseArtifacts(null)).isEmpty();
        assertThat(MavenCentralClient.parseArtifacts(json("{}"))).isEmpty();
        assertThat(MavenCentralClient.parseArtifacts(json("""
                {"response": {"docs": [{"latestVersion": "1"}]}}"""))).isEmpty();
        assertThat(MavenCentralClient.parseVersions(json("""
                {"response": {"docs": [{"timestamp": 1}]}}"""))).isEmpty();
    }

    private JsonNode json(String content) {
        return objectMapper.readTree(content);
    }
}
