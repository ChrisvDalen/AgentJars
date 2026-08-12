package com.agentjars.web;

import com.agentjars.catalog.CatalogService;
import com.agentjars.config.AgentJarsProperties;
import com.agentjars.model.AgentJar;
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
    private final PageModel pages;

    public AgentDetailController(
            CatalogService catalog, AgentJarsProperties properties, PageModel pages) {
        this.catalog = catalog;
        this.properties = properties;
        this.pages = pages;
    }

    @GetMapping("/agents/{artifactId}")
    public String detail(
            @PathVariable String artifactId,
            @RequestParam(required = false) String version,
            Model model) {
        AgentJar agent = require(artifactId);
        model.addAllAttributes(pages.agent(agent, resolveVersion(agent, version)));
        return "agent";
    }

    @GetMapping("/agents/{artifactId}/{version}/files")
    public String files(
            @PathVariable String artifactId, @PathVariable String version, Model model) {
        AgentJar agent = require(artifactId);
        requireVersion(agent, version);
        model.addAllAttributes(pages.files(agent, version, catalog.listFiles(artifactId, version)));
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
        requireVersion(agent, requested);
        return requested;
    }

    private void requireVersion(AgentJar agent, String version) {
        boolean known = agent.versions().stream()
                .anyMatch(candidate -> candidate.version().equals(version));
        if (!known) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "%s has no version %s".formatted(agent.artifactId(), version));
        }
    }
}
