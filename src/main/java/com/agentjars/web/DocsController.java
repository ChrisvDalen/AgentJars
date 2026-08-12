package com.agentjars.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Static documentation pages. */
@Controller
public class DocsController {

    private final PageModel pages;

    public DocsController(PageModel pages) {
        this.pages = pages;
    }

    @GetMapping("/docs")
    public String docs(Model model) {
        model.addAllAttributes(pages.docs());
        return "docs";
    }
}
