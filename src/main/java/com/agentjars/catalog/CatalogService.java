package com.agentjars.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.agentjars.config.AgentJarsProperties;
import com.agentjars.model.AgentJar;
import com.agentjars.model.AgentJarVersion;
import com.agentjars.model.AgentManifest;
import com.agentjars.model.Coordinates;
import com.agentjars.model.JarFileEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * The registry's read model: the list of published agents, one agent's detail page and the file
 * listing of a packaged jar.
 *
 * <p>Results are cached because every call fans out to Maven Central. The caches are refreshed on
 * a fixed interval and can be cleared explicitly after a deployment.
 */
@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    public static final String CATALOG_CACHE = "catalog";
    public static final String JAR_CACHE = "jarContents";

    private final AgentJarsProperties properties;
    private final MavenCentralClient mavenCentral;
    private final JarInspector jarInspector;
    private final BundledCatalog bundledCatalog;

    public CatalogService(
            AgentJarsProperties properties,
            MavenCentralClient mavenCentral,
            JarInspector jarInspector,
            BundledCatalog bundledCatalog) {
        this.properties = properties;
        this.mavenCentral = mavenCentral;
        this.jarInspector = jarInspector;
        this.bundledCatalog = bundledCatalog;
    }

    /** Every agent in the registry, newest publication first. */
    @Cacheable(CATALOG_CACHE)
    public List<AgentJar> findAll() {
        Map<String, AgentJar> merged = new LinkedHashMap<>();
        for (AgentJar agent : bundledCatalog.agents()) {
            merged.put(agent.artifactId(), agent);
        }
        for (AgentJar agent : fromMavenCentral()) {
            merged.put(agent.artifactId(), agent);
        }
        List<AgentJar> agents = new ArrayList<>(merged.values());
        agents.sort(Comparator
                .comparing(AgentJar::lastUpdated, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AgentJar::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(agents);
    }

    /** Agents matching a free-text query and an optional tag, in registry order. */
    public List<AgentJar> search(String query, String tag) {
        return findAll().stream()
                .filter(agent -> agent.matches(query))
                .filter(agent -> matchesTag(agent, tag))
                .toList();
    }

    public Optional<AgentJar> findByArtifactId(String artifactId) {
        return findAll().stream()
                .filter(agent -> agent.artifactId().equals(artifactId))
                .findFirst();
    }

    /** All tags in the registry, sorted, for the filter bar. */
    public Set<String> allTags() {
        Set<String> tags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        findAll().forEach(agent -> tags.addAll(agent.tags()));
        return tags;
    }

    /** The file listing of one published jar. */
    @Cacheable(value = JAR_CACHE, key = "#artifactId + ':' + #version")
    public List<JarFileEntry> listFiles(String artifactId, String version) {
        Coordinates coordinates = Coordinates.of(properties.groupId(), artifactId, version);
        return jarInspector.listEntries(mavenCentral.downloadJar(coordinates));
    }

    /** Reads one text file out of a published jar, for the file viewer. */
    public Optional<String> readFile(String artifactId, String version, String path) {
        Coordinates coordinates = Coordinates.of(properties.groupId(), artifactId, version);
        byte[] jar = mavenCentral.downloadJar(coordinates);
        return jarInspector.readText(jar, path);
    }

    /** Drops every cached answer; called after a successful deployment. */
    @CacheEvict(value = {CATALOG_CACHE, JAR_CACHE}, allEntries = true)
    public void refresh() {
        log.info("Catalog cache cleared");
    }

    private List<AgentJar> fromMavenCentral() {
        if (!mavenCentral.isEnabled()) {
            return List.of();
        }
        List<MavenCentralClient.ArtifactSummary> summaries = mavenCentral.listArtifacts();
        List<AgentJar> agents = new ArrayList<>(summaries.size());
        for (MavenCentralClient.ArtifactSummary summary : summaries) {
            agents.add(toAgentJar(summary));
        }
        return List.copyOf(agents);
    }

    private AgentJar toAgentJar(MavenCentralClient.ArtifactSummary summary) {
        Coordinates coordinates = Coordinates.of(
                properties.groupId(), summary.artifactId(), summary.latestVersion());
        List<AgentJarVersion> versions = summary.versionCount() > 1
                ? mavenCentral.listVersions(summary.artifactId())
                : List.of(AgentJarVersion.of(summary.latestVersion(), summary.lastUpdated()));

        String fallbackName = coordinates.agent().isEmpty()
                ? coordinates.repo()
                : coordinates.agent();
        AgentManifest manifest = jarInspector
                .readManifest(mavenCentral.downloadJar(coordinates), fallbackName)
                .orElseGet(() -> AgentManifest.named(fallbackName));

        return new AgentJar(
                coordinates,
                manifest,
                versions.isEmpty()
                        ? List.of(AgentJarVersion.of(summary.latestVersion(), summary.lastUpdated()))
                        : versions,
                coordinates.org() + "/" + coordinates.repo(),
                coordinates.agent().isEmpty() ? "" : "agents/" + coordinates.agent(),
                summary.lastUpdated());
    }

    private static boolean matchesTag(AgentJar agent, String tag) {
        if (tag == null || tag.isBlank()) {
            return true;
        }
        String needle = tag.trim().toLowerCase(Locale.ROOT);
        return agent.tags().stream().anyMatch(candidate -> candidate.toLowerCase(Locale.ROOT).equals(needle));
    }
}
