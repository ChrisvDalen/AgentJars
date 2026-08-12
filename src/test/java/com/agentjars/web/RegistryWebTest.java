package com.agentjars.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * End-to-end checks over the rendered site, running against the bundled catalog so the tests
 * never depend on Maven Central being reachable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistryWebTest {

    private static final String CODE_REVIEWER = "agentjars__example-agents__code-reviewer";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("the landing page lists agents from the registry")
    void landingPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("agents", "tags", "exampleSnippets"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Code Reviewer")));
    }

    @Test
    @DisplayName("searching narrows the listing")
    void search() {
        assertThat(mvc.get().uri("/agents").param("q", "kubernetes"))
                .hasStatusOk()
                .bodyText()
                .contains("Kubernetes Manifest Reviewer")
                .doesNotContain("Release Notes Writer");
    }

    @Test
    @DisplayName("filtering by tag narrows the listing")
    void tagFilter() {
        assertThat(mvc.get().uri("/agents").param("tag", "security"))
                .hasStatusOk()
                .bodyText()
                .contains("Endpoint Hardener")
                .doesNotContain("Release Notes Writer");
    }

    @Test
    @DisplayName("the detail page renders a dependency snippet for every build tool")
    void detailPage() {
        assertThat(mvc.get().uri("/agents/{artifactId}", CODE_REVIEWER))
                .hasStatusOk()
                .bodyText()
                .contains("Code Reviewer")
                .contains("&lt;artifactId&gt;" + CODE_REVIEWER + "&lt;/artifactId&gt;")
                .contains("implementation(&quot;com.agentjars:" + CODE_REVIEWER)
                .contains("libraryDependencies");
    }

    @Test
    @DisplayName("an unknown agent is a 404, not a 500")
    void unknownAgent() throws Exception {
        mockMvc.perform(get("/agents/{artifactId}", "does__not__exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an unknown version of a known agent is a 404")
    void unknownVersion() throws Exception {
        mockMvc.perform(get("/agents/{artifactId}", CODE_REVIEWER).param("version", "1999_01_01-0000000"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/agents/{artifactId}/{version}/files", CODE_REVIEWER, "1999_01_01-0000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the docs and publish pages render")
    void staticPages() throws Exception {
        mockMvc.perform(get("/docs")).andExpect(status().isOk()).andExpect(view().name("docs"));
        mockMvc.perform(get("/deploy")).andExpect(status().isOk()).andExpect(view().name("deploy"));
    }

    @Test
    @DisplayName("the API lists agents as JSON")
    void apiList() throws Exception {
        mockMvc.perform(get("/api/agents").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value("com.agentjars"))
                .andExpect(jsonPath("$.count").isNumber())
                .andExpect(jsonPath("$.agents[0].artifactId").isNotEmpty());
    }

    @Test
    @DisplayName("the API returns one agent with its versions and snippets")
    void apiDetail() throws Exception {
        mockMvc.perform(get("/api/agents/{artifactId}", CODE_REVIEWER).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Code Reviewer"))
                .andExpect(jsonPath("$.license").value("Apache-2.0"))
                .andExpect(jsonPath("$.versions.length()").value(3))
                .andExpect(jsonPath("$.dependencies.maven").value(
                        org.hamcrest.Matchers.containsString("<groupId>com.agentjars</groupId>")));
    }

    @Test
    @DisplayName("the API 404s on an unknown agent")
    void apiUnknownAgent() throws Exception {
        mockMvc.perform(get("/api/agents/{artifactId}", "does__not__exist"))
                .andExpect(status().isNotFound());
    }
}
