package com.agentjars.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.agentjars.config.AgentJarsProperties;
import com.agentjars.model.AgentJar;
import com.agentjars.model.AgentJarVersion;
import com.agentjars.model.AgentManifest;
import com.agentjars.model.Coordinates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The catalog shipped with the application.
 *
 * <p>It backs local development and keeps the registry readable when Maven Central cannot be
 * reached. Entries are merged under those from Central, so a bundled entry never masks a real
 * published artifact.
 */
@Component
public class BundledCatalog {

    private static final Logger log = LoggerFactory.getLogger(BundledCatalog.class);
    private static final String RESOURCE = "catalog/bundled-agents.json";

    private final List<AgentJar> agents;

    public BundledCatalog(AgentJarsProperties properties, ObjectMapper objectMapper) {
        this.agents = load(properties, objectMapper);
    }

    public List<AgentJar> agents() {
        return agents;
    }

    private static List<AgentJar> load(AgentJarsProperties properties, ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        if (!resource.exists()) {
            log.info("No bundled catalog at {}", RESOURCE);
            return List.of();
        }
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            List<AgentJar> parsed = new ArrayList<>();
            for (JsonNode node : root) {
                parsed.add(toAgentJar(node, properties.groupId()));
            }
            log.info("Loaded {} agents from the bundled catalog", parsed.size());
            return List.copyOf(parsed);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RESOURCE, e);
        }
    }

    private static AgentJar toAgentJar(JsonNode node, String groupId) {
        List<AgentJarVersion> versions = new ArrayList<>();
        for (JsonNode versionNode : node.path("versions")) {
            String version = versionNode.path("version").asString();
            String published = versionNode.path("published").asString(null);
            versions.add(AgentJarVersion.of(
                    version, published == null ? null : Instant.parse(published)));
        }
        AgentManifest manifest = new AgentManifest(
                text(node, "name"),
                text(node, "description"),
                text(node, "model"),
                strings(node, "tools"),
                strings(node, "tags"),
                text(node, "license"),
                text(node, "homepage"));
        String latestVersion = versions.isEmpty() ? null : versions.getFirst().version();
        return new AgentJar(
                Coordinates.of(groupId, node.path("artifactId").asString(), latestVersion),
                manifest,
                versions,
                text(node, "sourceRepo"),
                text(node, "sourcePath"),
                versions.isEmpty() ? null : versions.getFirst().published());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }

    private static List<String> strings(JsonNode node, String field) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : node.path(field)) {
            values.add(item.asString());
        }
        return List.copyOf(values);
    }
}
