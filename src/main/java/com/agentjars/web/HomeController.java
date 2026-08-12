package com.agentjars.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The landing page and the browse view. */
@Controller
public class HomeController {

    private final PageModel pages;

    public HomeController(PageModel pages) {
        this.pages = pages;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAllAttributes(pages.home());
        return "index";
    }

    /**
     * Searching and tag filtering both land here rather than on the landing page, so the listing
     * is the one place that answers a query — which is also what lets the static build serve
     * search from a single prerendered page.
     */
    @GetMapping("/agents")
    public String agents(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tag,
            Model model) {
        model.addAllAttributes(pages.agents(q, tag));
        return "agents";
    }
}
