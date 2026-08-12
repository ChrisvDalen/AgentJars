package com.agentjars.web;

import java.util.List;
import java.util.Map;

import com.agentjars.catalog.CatalogService;
import com.agentjars.config.AgentJarsProperties;
import com.agentjars.model.AgentJar;
import com.agentjars.model.BuildTool;
import com.agentjars.model.Coordinates;
import com.agentjars.model.JarFileEntry;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** JSON view of the registry, for tooling that resolves agents without scraping the site. */
@RestController
@RequestMapping("/api")
public class AgentApiController {

    private final CatalogService catalog;
    private final AgentJarsProperties properties;

    public AgentApiController(CatalogService catalog, AgentJarsProperties properties) {
        this.catalog = catalog;
        this.properties = properties;
    }

    @GetMapping("/agents")
    public Map<String, Object> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tag) {
        List<AgentJar> agents = catalog.search(q, tag);
        return Map.of(
                "groupId", properties.groupId(),
                "count", agents.size(),
                "agents", agents.stream().map(AgentApiController::summary).toList());
    }

    @GetMapping("/agents/{artifactId}")
    public Map<String, Object> detail(@PathVariable String artifactId) {
        AgentJar agent = catalog.findByArtifactId(artifactId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No agent published as %s:%s".formatted(properties.groupId(), artifactId)));
        Coordinates coordinates = agent.latestCoordinates();
        return Map.of(
                "groupId", agent.groupId(),
                "artifactId", agent.artifactId(),
                "name", agent.displayName(),
                "description", agent.description(),
                "license", agent.license(),
                "tags", agent.tags(),
                "tools", agent.manifest() == null ? List.of() : agent.manifest().tools(),
                "sourceRepository", agent.sourceRepo(),
                "versions", agent.versions().stream().map(version -> Map.of(
                        "version", version.version(),
                        "commit", version.commit() == null ? "" : version.commit())).toList(),
                "dependencies", Map.of(
                        BuildTool.MAVEN.id(), BuildTool.MAVEN.snippet(coordinates),
                        BuildTool.GRADLE.id(), BuildTool.GRADLE.snippet(coordinates),
                        BuildTool.SBT.id(), BuildTool.SBT.snippet(coordinates)));
    }

    @GetMapping("/agents/{artifactId}/{version}/files")
    public Map<String, Object> files(@PathVariable String artifactId, @PathVariable String version) {
        List<JarFileEntry> entries = catalog.listFiles(artifactId, version);
        return Map.of(
                "artifactId", artifactId,
                "version", version,
                "files", entries.stream()
                        .filter(entry -> !entry.directory())
                        .map(entry -> Map.of("path", entry.path(), "size", entry.size()))
                        .toList());
    }

    private static Map<String, Object> summary(AgentJar agent) {
        return Map.of(
                "artifactId", agent.artifactId(),
                "name", agent.displayName(),
                "description", agent.description(),
                "latestVersion", agent.latestVersion() == null ? "" : agent.latestVersion(),
                "license", agent.license(),
                "tags", agent.tags(),
                "sourceRepository", agent.sourceRepo() == null ? "" : agent.sourceRepo());
    }
}
