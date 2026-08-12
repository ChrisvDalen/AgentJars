package com.agentjars.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.agentjars.config.AgentJarsProperties;
import com.agentjars.model.AgentJar;
import com.agentjars.packaging.AgentManifestParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Exercises the read model against the bundled catalog, with Maven Central switched off. */
class CatalogServiceTest {

    private final AgentJarsProperties properties =
            new AgentJarsProperties(null, null,
                    new AgentJarsProperties.MavenCentral(null, null, false), null, null);

    private CatalogService catalog;

    @BeforeEach
    void setUp() {
        MavenCentralClient mavenCentral = mock(MavenCentralClient.class);
        when(mavenCentral.isEnabled()).thenReturn(false);

        catalog = new CatalogService(
                properties,
                mavenCentral,
                new JarInspector(properties, new AgentManifestParser()),
                new BundledCatalog(properties, new ObjectMapper()));
    }

    @Test
    @DisplayName("serves the bundled catalog when Maven Central is unavailable")
    void servesBundledCatalog() {
        List<AgentJar> agents = catalog.findAll();

        assertThat(agents).isNotEmpty();
        assertThat(agents).allSatisfy(agent -> {
            assertThat(agent.groupId()).isEqualTo("com.agentjars");
            assertThat(agent.artifactId()).isNotBlank();
            assertThat(agent.latestVersion()).isNotBlank();
        });
    }

    @Test
    @DisplayName("orders the catalog by publication date, newest first")
    void ordersByPublicationDate() {
        List<AgentJar> agents = catalog.findAll();

        assertThat(agents).isSortedAccordingTo((first, second) -> {
            if (first.lastUpdated() == null || second.lastUpdated() == null) {
                return 0;
            }
            return second.lastUpdated().compareTo(first.lastUpdated());
        });
    }

    @Test
    @DisplayName("matches a free-text query against name, description, tags and repository")
    void searchesAcrossFields() {
        assertThat(catalog.search("kubernetes", null))
                .extracting(AgentJar::artifactId)
                .contains("agentjars__ops-agents__manifest-reviewer");
        assertThat(catalog.search("N+1", null)).hasSize(1);
        assertThat(catalog.search("ops-agents", null)).hasSize(2);
        assertThat(catalog.search("REVIEW", null)).isNotEmpty();
        assertThat(catalog.search("nothing matches this", null)).isEmpty();
    }

    @Test
    @DisplayName("filters by tag, exactly and case-insensitively")
    void filtersByTag() {
        assertThat(catalog.search(null, "security"))
                .allSatisfy(agent -> assertThat(agent.tags()).contains("security"));
        assertThat(catalog.search(null, "SECURITY")).isNotEmpty();
        assertThat(catalog.search(null, "secur")).isEmpty();
    }

    @Test
    @DisplayName("combines the query and the tag filter")
    void combinesQueryAndTag() {
        assertThat(catalog.search("spring", "security"))
                .extracting(AgentJar::artifactId)
                .containsExactly("agentjars__spring-agents__endpoint-hardener");
    }

    @Test
    @DisplayName("an empty query returns everything")
    void emptyQueryReturnsAll() {
        assertThat(catalog.search(null, null)).hasSameSizeAs(catalog.findAll());
        assertThat(catalog.search("  ", "")).hasSameSizeAs(catalog.findAll());
    }

    @Test
    @DisplayName("looks one agent up by artifactId")
    void findsByArtifactId() {
        assertThat(catalog.findByArtifactId("agentjars__example-agents__code-reviewer"))
                .hasValueSatisfying(agent -> {
                    assertThat(agent.displayName()).isEqualTo("Code Reviewer");
                    assertThat(agent.versions()).hasSize(3);
                    assertThat(agent.latestVersion()).isEqualTo("2026_07_28-9c1f4ab");
                    assertThat(agent.sourceUrl())
                            .isEqualTo("https://github.com/agentjars/example-agents/tree/HEAD/agents/code-reviewer");
                });
        assertThat(catalog.findByArtifactId("does__not__exist")).isEmpty();
    }

    @Test
    @DisplayName("collects every tag in the registry for the filter bar")
    void collectsTags() {
        assertThat(catalog.allTags()).contains("review", "security", "kubernetes", "spring");
    }

    @Test
    @DisplayName("cannot list jar contents while Maven Central is switched off")
    void jarListingNeedsMavenCentral() {
        assertThat(catalog.listFiles("agentjars__example-agents__code-reviewer", "2026_07_28-9c1f4ab"))
                .isEmpty();
    }
}
