package com.agentjars.deploy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.agentjars.catalog.CatalogService;
import com.agentjars.config.AgentJarsProperties;
import com.agentjars.model.AgentJarVersion;
import com.agentjars.model.Coordinates;
import com.agentjars.model.DiscoveredAgent;
import com.agentjars.model.GitHubRepoRef;
import com.agentjars.packaging.AgentJarPackager;
import com.agentjars.packaging.AgentScanner;
import com.agentjars.packaging.LicenseResolver;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Packages every agent in a public GitHub repository into AgentJars.
 *
 * <p>The run is deliberately forgiving: an agent without a resolvable license is skipped and
 * reported rather than failing the repository, because a repository usually holds several agents
 * and one missing LICENSE should not block the rest. Structural problems — overlapping agent
 * directories, an unreachable repository — do fail the run, since they cannot be worked around.
 */
@Service
public class DeployService {

    private static final Logger log = LoggerFactory.getLogger(DeployService.class);

    private final AgentJarsProperties properties;
    private final AgentScanner scanner;
    private final LicenseResolver licenseResolver;
    private final AgentJarPackager packager;
    private final CatalogService catalogService;
    private final Clock clock;

    public DeployService(
            AgentJarsProperties properties,
            AgentScanner scanner,
            LicenseResolver licenseResolver,
            AgentJarPackager packager,
            CatalogService catalogService,
            Clock clock) {
        this.properties = properties;
        this.scanner = scanner;
        this.licenseResolver = licenseResolver;
        this.packager = packager;
        this.catalogService = catalogService;
        this.clock = clock;
    }

    /**
     * Clones {@code repository}, packages the agents it declares and writes an upload bundle per
     * agent.
     *
     * @throws IllegalStateException when the repository cannot be packaged as a whole
     */
    public DeployResult deploy(String repository) {
        if (!properties.deploy().isEnabled()) {
            throw new IllegalStateException("Deployment is disabled on this instance");
        }
        GitHubRepoRef repo = GitHubRepoRef.parse(repository);
        Path workspace = createWorkspace(repo);
        try {
            String commit = clone(repo, workspace.resolve("source"));
            return packageAgents(repo, commit, workspace);
        } finally {
            deleteRecursively(workspace.resolve("source"));
        }
    }

    private DeployResult packageAgents(GitHubRepoRef repo, String commit, Path workspace) {
        Path source = workspace.resolve("source");
        List<DiscoveredAgent> agents = scanner.scan(source);
        if (agents.isEmpty()) {
            throw new IllegalStateException(
                    "No %s found in %s. Add one per agent, either at the repository root or under agents/."
                            .formatted(AgentScanner.MANIFEST_FILE, repo.slug()));
        }
        if (agents.size() > properties.deploy().maxAgents()) {
            throw new IllegalStateException(
                    "%s declares %d agents, more than the limit of %d"
                            .formatted(repo.slug(), agents.size(), properties.deploy().maxAgents()));
        }

        String version = AgentJarVersion.format(LocalDate.now(clock), commit);
        Path output = workspace.resolve("artifacts");

        List<DeployResult.PackagedAgent> packaged = new ArrayList<>();
        List<DeployResult.SkippedAgent> skipped = new ArrayList<>();
        for (DiscoveredAgent agent : agents) {
            String agentPath = agent.relativePath().isEmpty() ? "." : agent.relativePath();
            Optional<String> license = licenseResolver.resolve(agent, source);
            if (license.isEmpty()) {
                skipped.add(new DeployResult.SkippedAgent(agentPath,
                        "No license could be resolved. Add a license to the AGENT.md front matter "
                                + "or a LICENSE file to the agent directory or repository root."));
                continue;
            }
            Coordinates coordinates = Coordinates.of(
                    properties.groupId(),
                    Coordinates.artifactId(repo.org(), repo.repo(), agent.relativePath()),
                    version);
            List<Path> files = packager.packageAgent(agent, coordinates, repo, license.get(), output);
            Path bundle = packager.bundle(coordinates, files, output);
            packaged.add(new DeployResult.PackagedAgent(
                    coordinates, agentPath, license.get(), bundle.toString()));
            log.info("Packaged {} from {}", coordinates, repo.slug());
        }

        if (!packaged.isEmpty()) {
            catalogService.refresh();
        }
        return new DeployResult(repo.slug(), commit, packaged, skipped);
    }

    /** Shallow-clones the repository and returns the commit that was checked out. */
    private String clone(GitHubRepoRef repo, Path target) {
        try (Git git = Git.cloneRepository()
                .setURI(repo.cloneUrl())
                .setDirectory(target.toFile())
                .setDepth(1)
                .setBranch(repo.ref())
                .call()) {
            ObjectId head = git.getRepository().resolve("HEAD");
            if (head == null) {
                throw new IllegalStateException("%s has no commits".formatted(repo.slug()));
            }
            return head.getName();
        } catch (GitAPIException | IOException e) {
            throw new IllegalStateException(
                    "Could not clone %s: %s".formatted(repo.browseUrl(), e.getMessage()), e);
        }
    }

    private Path createWorkspace(GitHubRepoRef repo) {
        try {
            Path root = Path.of(properties.deploy().workDir());
            Files.createDirectories(root);
            return Files.createTempDirectory(root, repo.normalisedOrg() + "-" + repo.repo() + "-");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create a workspace for " + repo.slug(), e);
        }
    }

    private static void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(entry -> {
                try {
                    Files.deleteIfExists(entry);
                } catch (IOException e) {
                    log.debug("Could not delete {}: {}", entry, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.debug("Could not clean up {}: {}", path, e.getMessage());
        }
    }
}
