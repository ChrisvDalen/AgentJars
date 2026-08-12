package com.agentjars.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.agentjars.catalog.CatalogService;
import com.agentjars.config.AgentJarsProperties;
import com.agentjars.model.AgentJar;
import com.agentjars.model.BuildTool;
import com.agentjars.model.Coordinates;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The landing page and the browse view. */
@Controller
public class HomeController {

    private static final int FEATURED_COUNT = 6;

    private final CatalogService catalog;
    private final AgentJarsProperties properties;

    public HomeController(CatalogService catalog, AgentJarsProperties properties) {
        this.catalog = catalog;
        this.properties = properties;
    }

    @GetMapping("/")
    public String home(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tag,
            @RequestParam(name = "build", required = false) String build,
            Model model) {
        boolean filtered = (q != null && !q.isBlank()) || (tag != null && !tag.isBlank());
        List<AgentJar> results = catalog.search(q, tag);

        model.addAttribute("query", q);
        model.addAttribute("tag", tag);
        model.addAttribute("filtered", filtered);
        model.addAttribute("agents", filtered ? results : results.stream().limit(FEATURED_COUNT).toList());
        model.addAttribute("resultCount", results.size());
        model.addAttribute("totalCount", catalog.findAll().size());
        model.addAttribute("tags", catalog.allTags());
        model.addAttribute("groupId", properties.groupId());
        model.addAttribute("buildTools", BuildTool.values());
        model.addAttribute("selectedBuildTool", BuildTool.fromId(build));
        model.addAttribute("exampleSnippets", exampleSnippets());
        model.addAttribute("activeNav", "browse");
        return "index";
    }

    @GetMapping("/agents")
    public String agents(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tag,
            Model model) {
        List<AgentJar> results = catalog.search(q, tag);
        model.addAttribute("query", q);
        model.addAttribute("tag", tag);
        model.addAttribute("agents", results);
        model.addAttribute("resultCount", results.size());
        model.addAttribute("totalCount", catalog.findAll().size());
        model.addAttribute("tags", catalog.allTags());
        model.addAttribute("groupId", properties.groupId());
        model.addAttribute("activeNav", "browse");
        return "agents";
    }

    /** Dependency snippets for a made-up coordinate, shown on the landing page. */
    private Map<String, String> exampleSnippets() {
        Coordinates example = Coordinates.of(
                properties.groupId(), "myorg__myrepo__reviewer", "2026_07_28-9c1f4ab");
        Map<String, String> snippets = new LinkedHashMap<>();
        for (BuildTool tool : BuildTool.values()) {
            snippets.put(tool.id(), tool.snippet(example));
        }
        return snippets;
    }
}
