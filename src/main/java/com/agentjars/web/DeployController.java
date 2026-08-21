package com.agentjars.web;

import com.agentjars.deploy.DeployResult;
import com.agentjars.deploy.DeployService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The form that packages a public GitHub repository into AgentJars. */
@Controller
public class DeployController {

    private static final Logger log = LoggerFactory.getLogger(DeployController.class);

    private final DeployService deployService;
    private final PageModel pages;

    public DeployController(DeployService deployService, PageModel pages) {
        this.deployService = deployService;
        this.pages = pages;
    }

    @GetMapping("/deploy")
    public String form(@RequestParam(name = "repository", required = false) String repository, Model model) {
        model.addAllAttributes(pages.deploy(repository));
        return "deploy";
    }

    @PostMapping("/deploy")
    public String deploy(@RequestParam("repository") String repository, Model model) {
        model.addAllAttributes(pages.deploy(repository));
        try {
            DeployResult result = deployService.deploy(repository);
            model.addAttribute("result", result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.info("Deployment of {} rejected: {}", repository, e.getMessage());
            model.addAttribute("error", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Deployment of {} failed", repository, e);
            model.addAttribute("error",
                    "Packaging failed unexpectedly. Check that the repository is public and try again.");
        }
        return "deploy";
    }
}
