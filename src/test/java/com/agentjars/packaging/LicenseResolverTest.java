package com.agentjars.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.agentjars.model.AgentManifest;
import com.agentjars.model.DiscoveredAgent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicenseResolverTest {

    private static final String APACHE_TEXT = """
            Apache License
            Version 2.0, January 2004
            """;

    private final LicenseResolver resolver = new LicenseResolver();

    @Test
    @DisplayName("prefers the identifier declared in the front matter")
    void frontMatterWins(@TempDir Path repo) throws IOException {
        Path directory = Files.createDirectories(repo.resolve("agents/reviewer"));
        Files.writeString(repo.resolve("LICENSE"), APACHE_TEXT);

        DiscoveredAgent agent = agentAt(directory, "MIT");

        assertThat(resolver.resolve(agent, repo)).contains("MIT");
    }

    @Test
    @DisplayName("falls back to a LICENSE file next to the agent")
    void agentDirectoryLicense(@TempDir Path repo) throws IOException {
        Path directory = Files.createDirectories(repo.resolve("agents/reviewer"));
        Files.writeString(directory.resolve("LICENSE"), APACHE_TEXT);

        assertThat(resolver.resolve(agentAt(directory, null), repo)).contains("Apache-2.0");
    }

    @Test
    @DisplayName("falls back to the repository root LICENSE")
    void repositoryRootLicense(@TempDir Path repo) throws IOException {
        Path directory = Files.createDirectories(repo.resolve("agents/reviewer"));
        Files.writeString(repo.resolve("LICENSE"), """
                MIT License

                Permission is hereby granted, free of charge, to any person obtaining a copy
                """);

        assertThat(resolver.resolve(agentAt(directory, null), repo)).contains("MIT");
    }

    @Test
    @DisplayName("resolves nothing when no license is present anywhere")
    void noLicense(@TempDir Path repo) throws IOException {
        Path directory = Files.createDirectories(repo.resolve("agents/reviewer"));

        assertThat(resolver.resolve(agentAt(directory, null), repo)).isEmpty();
    }

    @Test
    @DisplayName("maps the well-known identifiers to canonical urls")
    void licenseUrls() {
        assertThat(resolver.urlFor("Apache-2.0")).isEqualTo("https://www.apache.org/licenses/LICENSE-2.0.txt");
        assertThat(resolver.urlFor("MIT")).isEqualTo("https://opensource.org/licenses/MIT");
        assertThat(resolver.urlFor("MPL-2.0")).isEqualTo("https://www.mozilla.org/media/MPL/2.0/index.txt");
        assertThat(resolver.urlFor("BSD-2-Clause")).isEqualTo("https://spdx.org/licenses/BSD-2-Clause");
    }

    private static DiscoveredAgent agentAt(Path directory, String declaredLicense) {
        AgentManifest manifest = new AgentManifest(
                "Reviewer", "Reviews a diff.", null, List.of(), List.of(), declaredLicense, null);
        return new DiscoveredAgent(directory, "agents/reviewer", manifest);
    }
}
