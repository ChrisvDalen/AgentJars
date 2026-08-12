package com.agentjars.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the registry: which Maven coordinates AgentJars live under, where the
 * catalog is read from, and how long remote answers are cached.
 *
 * @param groupId          the Maven groupId every AgentJar is published under
 * @param agentsRoot       directory inside a jar that holds the packaged agents
 * @param mavenCentral     endpoints used to read the catalog from Maven Central
 * @param cache            cache tuning
 * @param deploy           deployment (packaging + publishing) settings
 */
@ConfigurationProperties(prefix = "agentjars")
public record AgentJarsProperties(
        String groupId,
        String agentsRoot,
        MavenCentral mavenCentral,
        Cache cache,
        Deploy deploy) {

    public AgentJarsProperties {
        groupId = groupId == null ? "com.agentjars" : groupId;
        agentsRoot = agentsRoot == null ? "META-INF/agents" : agentsRoot;
        mavenCentral = mavenCentral == null ? new MavenCentral(null, null, null) : mavenCentral;
        cache = cache == null ? new Cache(null, null) : cache;
        deploy = deploy == null ? new Deploy(null, null, null) : deploy;
    }

    /**
     * @param searchUrl  Maven Central search API, used to enumerate artifacts in {@code groupId}
     * @param contentUrl Maven Central repository root, used to download poms and jars
     * @param enabled    when false the registry serves the bundled catalog only
     */
    public record MavenCentral(String searchUrl, String contentUrl, Boolean enabled) {
        public MavenCentral {
            searchUrl = searchUrl == null ? "https://search.maven.org/solrsearch/select" : searchUrl;
            contentUrl = contentUrl == null ? "https://repo1.maven.org/maven2" : contentUrl;
            enabled = enabled == null ? Boolean.TRUE : enabled;
        }

        public boolean isEnabled() {
            return Boolean.TRUE.equals(enabled);
        }
    }

    /**
     * @param catalogTtl how long a successful catalog listing is reused
     * @param maxEntries upper bound on cached jar file listings
     */
    public record Cache(Duration catalogTtl, Integer maxEntries) {
        public Cache {
            catalogTtl = catalogTtl == null ? Duration.ofHours(1) : catalogTtl;
            maxEntries = maxEntries == null ? 500 : maxEntries;
        }
    }

    /**
     * @param workDir   scratch directory for clones and generated bundles
     * @param enabled   whether the /deploy endpoint accepts submissions
     * @param maxAgents refuse repositories that declare more agents than this
     */
    public record Deploy(String workDir, Boolean enabled, Integer maxAgents) {
        public Deploy {
            workDir = workDir == null ? System.getProperty("java.io.tmpdir") + "/agentjars" : workDir;
            enabled = enabled == null ? Boolean.TRUE : enabled;
            maxAgents = maxAgents == null ? 50 : maxAgents;
        }

        public boolean isEnabled() {
            return Boolean.TRUE.equals(enabled);
        }
    }
}
