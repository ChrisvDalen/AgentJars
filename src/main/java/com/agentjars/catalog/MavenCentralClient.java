package com.agentjars.catalog;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.agentjars.config.AgentJarsProperties;
import com.agentjars.model.AgentJarVersion;
import com.agentjars.model.Coordinates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

/**
 * Reads the AgentJars catalog from Maven Central.
 *
 * <p>Two endpoints are used: the search API enumerates artifacts and versions under the AgentJars
 * groupId, and the repository itself serves the jars whose {@code AGENT.md} supplies the
 * descriptive metadata. Every call degrades to an empty result instead of throwing, so a Central
 * outage downgrades the registry to its bundled catalog rather than taking it down.
 */
@Component
public class MavenCentralClient {

    private static final Logger log = LoggerFactory.getLogger(MavenCentralClient.class);
    private static final int MAX_ROWS = 200;

    private final AgentJarsProperties properties;
    private final RestClient search;
    private final RestClient content;

    public MavenCentralClient(AgentJarsProperties properties) {
        this.properties = properties;
        ClientHttpRequestFactory requestFactory = timeoutBoundedRequestFactory();
        this.search = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.mavenCentral().searchUrl())
                .build();
        this.content = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.mavenCentral().contentUrl())
                .build();
    }

    /**
     * Maven Central is a third party on the request path of every page, so both timeouts are
     * bounded: a slow upstream degrades the registry to its bundled catalog rather than hanging.
     */
    private static ClientHttpRequestFactory timeoutBoundedRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return factory;
    }

    public boolean isEnabled() {
        return properties.mavenCentral().isEnabled();
    }

    /** Every artifactId published under the AgentJars groupId, with its newest version. */
    public List<ArtifactSummary> listArtifacts() {
        if (!isEnabled()) {
            return List.of();
        }
        try {
            JsonNode response = search.get()
                    .uri(uri -> uri.queryParam("q", "g:" + properties.groupId())
                            .queryParam("rows", MAX_ROWS)
                            .queryParam("wt", "json")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            return parseArtifacts(response);
        } catch (RestClientException e) {
            log.warn("Could not list artifacts for groupId {}: {}", properties.groupId(), e.getMessage());
            return List.of();
        }
    }

    /** Every published version of one artifact, newest first. */
    public List<AgentJarVersion> listVersions(String artifactId) {
        if (!isEnabled()) {
            return List.of();
        }
        try {
            JsonNode response = search.get()
                    .uri(uri -> uri.queryParam("q",
                                    "g:" + properties.groupId() + " AND a:" + artifactId)
                            .queryParam("core", "gav")
                            .queryParam("rows", MAX_ROWS)
                            .queryParam("wt", "json")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            return parseVersions(response);
        } catch (RestClientException e) {
            log.warn("Could not list versions for {}: {}", artifactId, e.getMessage());
            return List.of();
        }
    }

    /** Downloads the main jar for a coordinate, or empty when it cannot be fetched. */
    public byte[] downloadJar(Coordinates coordinates) {
        if (!isEnabled()) {
            return new byte[0];
        }
        String path = coordinates.repositoryPath() + "/" + coordinates.jarFileName();
        try {
            byte[] body = content.get().uri(path).retrieve().body(byte[].class);
            return body == null ? new byte[0] : body;
        } catch (RestClientException e) {
            log.warn("Could not download {}: {}", path, e.getMessage());
            return new byte[0];
        }
    }

    static List<ArtifactSummary> parseArtifacts(JsonNode response) {
        List<ArtifactSummary> artifacts = new ArrayList<>();
        JsonNode docs = docs(response);
        for (JsonNode doc : docs) {
            String artifactId = doc.path("a").asString(null);
            if (artifactId == null || artifactId.isBlank()) {
                continue;
            }
            artifacts.add(new ArtifactSummary(
                    artifactId,
                    doc.path("latestVersion").asString(null),
                    doc.path("versionCount").asInt(0),
                    timestamp(doc)));
        }
        artifacts.sort(Comparator.comparing(ArtifactSummary::artifactId));
        return List.copyOf(artifacts);
    }

    static List<AgentJarVersion> parseVersions(JsonNode response) {
        List<AgentJarVersion> versions = new ArrayList<>();
        for (JsonNode doc : docs(response)) {
            String version = doc.path("v").asString(null);
            if (version == null || version.isBlank()) {
                continue;
            }
            versions.add(AgentJarVersion.of(version, timestamp(doc)));
        }
        versions.sort(Comparator.comparing(AgentJarVersion::version).reversed());
        return List.copyOf(versions);
    }

    private static JsonNode docs(JsonNode response) {
        if (response == null) {
            return MissingNode.getInstance();
        }
        return response.path("response").path("docs");
    }

    private static Instant timestamp(JsonNode doc) {
        long millis = doc.path("timestamp").asLong(0L);
        return millis > 0 ? Instant.ofEpochMilli(millis) : null;
    }

    /**
     * A row from the search API's artifact listing.
     *
     * @param artifactId    the artifactId
     * @param latestVersion newest published version
     * @param versionCount  number of published versions
     * @param lastUpdated   publication time of the newest version
     */
    public record ArtifactSummary(
            String artifactId, String latestVersion, int versionCount, Instant lastUpdated) {
    }
}
