package com.agentjars.web;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.agentjars.catalog.CatalogService;
import com.agentjars.config.AgentJarsProperties;
import com.agentjars.model.AgentJar;
import com.agentjars.model.BuildTool;
import com.agentjars.model.Coordinates;
import com.agentjars.model.JarFileEntry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** The detail page for one agent and the file listing of one of its versions. */
@Controller
public class AgentDetailController {

    private final CatalogService catalog;
    private final AgentJarsProperties properties;

    public AgentDetailController(CatalogService catalog, AgentJarsProperties properties) {
        this.catalog = catalog;
        this.properties = properties;
    }

    @GetMapping("/agents/{artifactId}")
    public String detail(
            @PathVariable String artifactId,
            @RequestParam(required = false) String version,
            @RequestParam(name = "build", required = false) String build,
            Model model) {
        AgentJar agent = require(artifactId);
        String resolvedVersion = resolveVersion(agent, version);
        Coordinates coordinates = agent.coordinates().withVersion(resolvedVersion);
        BuildTool selected = BuildTool.fromId(build);

        model.addAttribute("agent", agent);
        model.addAttribute("selectedVersion", resolvedVersion);
        model.addAttribute("coordinates", coordinates);
        model.addAttribute("buildTools", BuildTool.values());
        model.addAttribute("selectedBuildTool", selected);
        model.addAttribute("snippets", snippets(coordinates));
        model.addAttribute("activeNav", "browse");
        return "agent";
    }

    @GetMapping("/agents/{artifactId}/{version}/files")
    public String files(
            @PathVariable String artifactId,
            @PathVariable String version,
            Model model) {
        AgentJar agent = require(artifactId);
        if (agent.versions().stream().noneMatch(candidate -> candidate.version().equals(version))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "%s has no version %s".formatted(artifactId, version));
        }
        List<JarFileEntry> entries = catalog.listFiles(artifactId, version);

        model.addAttribute("agent", agent);
        model.addAttribute("selectedVersion", version);
        model.addAttribute("coordinates", agent.coordinates().withVersion(version));
        model.addAttribute("entries", entries);
        model.addAttribute("downloadUrl", "%s/%s/%s".formatted(
                properties.mavenCentral().contentUrl(),
                agent.coordinates().withVersion(version).repositoryPath(),
                agent.coordinates().withVersion(version).jarFileName()));
        model.addAttribute("activeNav", "browse");
        return "files";
    }

    private AgentJar require(String artifactId) {
        return catalog.findByArtifactId(artifactId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No agent published as %s:%s".formatted(properties.groupId(), artifactId)));
    }

    private String resolveVersion(AgentJar agent, String requested) {
        if (requested == null || requested.isBlank()) {
            return agent.latestVersion();
        }
        boolean known = agent.versions().stream()
                .anyMatch(candidate -> candidate.version().equals(requested));
        if (!known) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "%s has no version %s".formatted(agent.artifactId(), requested));
        }
        return requested;
    }

    private Map<String, String> snippets(Coordinates coordinates) {
        return java.util.Arrays.stream(BuildTool.values())
                .collect(Collectors.toMap(
                        BuildTool::id,
                        tool -> tool.snippet(coordinates),
                        (first, second) -> first,
                        java.util.LinkedHashMap::new));
    }
}
