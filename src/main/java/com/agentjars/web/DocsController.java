package com.agentjars.web;

import com.agentjars.config.AgentJarsProperties;
import com.agentjars.model.BuildTool;
import com.agentjars.model.Coordinates;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Static documentation pages. */
@Controller
public class DocsController {

    private final AgentJarsProperties properties;

    public DocsController(AgentJarsProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/docs")
    public String docs(Model model) {
        Coordinates example = Coordinates.of(
                properties.groupId(), "myorg__myrepo__reviewer", "2026_07_28-9c1f4ab");
        model.addAttribute("groupId", properties.groupId());
        model.addAttribute("agentsRoot", properties.agentsRoot());
        model.addAttribute("buildTools", BuildTool.values());
        model.addAttribute("mavenSnippet", BuildTool.MAVEN.snippet(example));
        model.addAttribute("gradleSnippet", BuildTool.GRADLE.snippet(example));
        model.addAttribute("sbtSnippet", BuildTool.SBT.snippet(example));
        model.addAttribute("activeNav", "docs");
        return "docs";
    }
}
