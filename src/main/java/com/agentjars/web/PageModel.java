package com.agentjars.web;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.agentjars.catalog.CatalogService;
import com.agentjars.config.AgentJarsProperties;
import com.agentjars.model.AgentJar;
import com.agentjars.model.BuildTool;
import com.agentjars.model.Coordinates;
import com.agentjars.model.JarFileEntry;
import org.springframework.stereotype.Component;

/**
 * Builds the model behind each page.
 *
 * <p>The registry renders the same templates two ways: live from the controllers, and ahead of
 * time by the static site generator. Both take their model from here so a change to a page cannot
 * apply to only one of them.
 *
 * <p>Every method returns a mutable map: callers add their own rendering flags on top.
 */
@Component
public class PageModel {

    /** How many agents the landing page shows before sending the reader to the full listing. */
    private static final int FEATURED_COUNT = 6;

    private static final String EXAMPLE_ARTIFACT_ID = "myorg__myrepo__reviewer";
    private static final String EXAMPLE_VERSION = "2026_07_28-9c1f4ab";

    private final CatalogService catalog;
    private final AgentJarsProperties properties;

    public PageModel(CatalogService catalog, AgentJarsProperties properties) {
        this.catalog = catalog;
        this.properties = properties;
    }

    /** The landing page: a hero, the most recently published agents, and the tag filter. */
    public Map<String, Object> home() {
        List<AgentJar> all = catalog.findAll();
        Map<String, Object> model = shell("browse");
        model.put("agents", all.stream().limit(FEATURED_COUNT).toList());
        model.put("totalCount", all.size());
        model.put("tags", catalog.allTags());
        model.put("buildTools", BuildTool.values());
        model.put("exampleSnippets", snippets(
                Coordinates.of(properties.groupId(), EXAMPLE_ARTIFACT_ID, EXAMPLE_VERSION)));
        return model;
    }

    /** The full listing, optionally narrowed by a query and a tag. */
    public Map<String, Object> agents(String query, String tag) {
        List<AgentJar> results = catalog.search(query, tag);
        Map<String, Object> model = shell("browse");
        model.put("query", query);
        model.put("tag", tag);
        model.put("agents", results);
        model.put("resultCount", results.size());
        model.put("totalCount", catalog.findAll().size());
        model.put("tags", catalog.allTags());
        return model;
    }

    /** One agent, pinned to a version, with a dependency snippet per build tool. */
    public Map<String, Object> agent(AgentJar agent, String version) {
        Coordinates coordinates = agent.coordinates().withVersion(version);
        Map<String, Object> model = shell("browse");
        model.put("agent", agent);
        model.put("selectedVersion", version);
        model.put("coordinates", coordinates);
        model.put("buildTools", BuildTool.values());
        model.put("snippets", snippets(coordinates));
        return model;
    }

    /** The file listing of one published jar. */
    public Map<String, Object> files(AgentJar agent, String version, List<JarFileEntry> entries) {
        Coordinates coordinates = agent.coordinates().withVersion(version);
        Map<String, Object> model = shell("browse");
        model.put("agent", agent);
        model.put("selectedVersion", version);
        model.put("coordinates", coordinates);
        model.put("entries", entries);
        model.put("downloadUrl", "%s/%s/%s".formatted(
                properties.mavenCentral().contentUrl(),
                coordinates.repositoryPath(),
                coordinates.jarFileName()));
        return model;
    }

    public Map<String, Object> docs() {
        Coordinates example = Coordinates.of(
                properties.groupId(), EXAMPLE_ARTIFACT_ID, EXAMPLE_VERSION);
        Map<String, Object> model = shell("docs");
        model.put("agentsRoot", properties.agentsRoot());
        model.put("buildTools", BuildTool.values());
        model.put("mavenSnippet", BuildTool.MAVEN.snippet(example));
        model.put("gradleSnippet", BuildTool.GRADLE.snippet(example));
        model.put("sbtSnippet", BuildTool.SBT.snippet(example));
        return model;
    }

    public Map<String, Object> deploy(String repository) {
        Map<String, Object> model = shell("deploy");
        model.put("repository", repository);
        model.put("deployEnabled", properties.deploy().isEnabled());
        return model;
    }

    /** The error page, used for live errors and for the static site's 404. */
    public Map<String, Object> error(int status, String title, String detail) {
        Map<String, Object> model = shell("");
        model.put("status", status);
        model.put("title", title);
        model.put("detail", detail);
        return model;
    }

    /**
     * Attributes every page needs.
     *
     * <p>{@code staticSite} defaults to false here rather than being left absent, so templates can
     * negate it without having to guard against a missing variable. The static site generator
     * overwrites it.
     */
    private Map<String, Object> shell(String activeNav) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("activeNav", activeNav);
        model.put("groupId", properties.groupId());
        model.put("staticSite", Boolean.FALSE);
        return model;
    }

    private static Map<String, String> snippets(Coordinates coordinates) {
        Map<String, String> snippets = new LinkedHashMap<>();
        Arrays.stream(BuildTool.values())
                .forEach(tool -> snippets.put(tool.id(), tool.snippet(coordinates)));
        return snippets;
    }
}
