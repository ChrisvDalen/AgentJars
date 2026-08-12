package com.agentjars.staticsite;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import com.agentjars.catalog.CatalogService;
import com.agentjars.config.AgentJarsProperties;
import com.agentjars.model.AgentJar;
import com.agentjars.model.AgentJarVersion;
import com.agentjars.model.BuildTool;
import com.agentjars.model.Coordinates;
import com.agentjars.web.PageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import tools.jackson.databind.ObjectMapper;

/**
 * Renders the registry to a directory of static files.
 *
 * <p>Everything that only reads the catalog survives this treatment: browsing, the detail pages
 * with their dependency snippets, the version history, the file listings and the JSON API. Search
 * and tag filtering move to the browser, which filters the one prerendered listing rather than
 * asking the server for a narrowed one.
 *
 * <p>Publishing does not survive: packaging a repository needs to clone it and write jars, which
 * a static host cannot do. The publish page is rendered as instructions instead of a form.
 */
@Component
public class StaticSiteGenerator {

    private static final Logger log = LoggerFactory.getLogger(StaticSiteGenerator.class);

    /** Marks a rendered page as static so the templates can drop server-only affordances. */
    public static final String STATIC_FLAG = "staticSite";

    private final CatalogService catalog;
    private final AgentJarsProperties properties;
    private final PageModel pages;
    private final SpringTemplateEngine templateEngine;
    private final ObjectMapper objectMapper;

    public StaticSiteGenerator(
            CatalogService catalog,
            AgentJarsProperties properties,
            PageModel pages,
            SpringTemplateEngine templateEngine,
            ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.properties = properties;
        this.pages = pages;
        this.templateEngine = templateEngine;
        this.objectMapper = objectMapper;
    }

    /**
     * Writes the whole site into {@code outputDirectory}.
     *
     * @param outputDirectory directory to write into; created if missing, emptied if present
     * @param basePath        path the site will be served under, e.g. {@code /AgentJars}
     * @return the number of files written
     */
    public int generate(Path outputDirectory, String basePath) {
        SpringTemplateEngine engine = engineFor(basePath);

        List<AgentJar> agents = catalog.findAll();
        log.info("Generating a static site for {} agents under base path '{}'",
                agents.size(), BasePathLinkBuilder.normalise(basePath));

        prepare(outputDirectory);
        int written = 0;

        written += render(engine, "index", pages.home(), outputDirectory.resolve("index.html"));
        written += render(engine, "agents", pages.agents(null, null),
                outputDirectory.resolve("agents/index.html"));
        written += render(engine, "docs", pages.docs(), outputDirectory.resolve("docs/index.html"));
        written += render(engine, "deploy", pages.deploy(null),
                outputDirectory.resolve("deploy/index.html"));

        // GitHub Pages serves 404.html for any path it cannot resolve.
        written += render(engine, "error",
                pages.error(404, "Not found", "The page you asked for is not part of this registry."),
                outputDirectory.resolve("404.html"));

        for (AgentJar agent : agents) {
            written += renderAgent(engine, agent, outputDirectory);
        }

        written += writeApi(agents, outputDirectory);
        written += copyStaticResources(outputDirectory);
        written += writeMarker(outputDirectory.resolve(".nojekyll"), "");

        log.info("Wrote {} files to {}", written, outputDirectory.toAbsolutePath());
        return written;
    }

    private int renderAgent(SpringTemplateEngine engine, AgentJar agent, Path outputDirectory) {
        Path agentDirectory = outputDirectory.resolve("agents").resolve(agent.artifactId());
        int written = render(engine, "agent",
                pages.agent(agent, agent.latestVersion()),
                agentDirectory.resolve("index.html"));

        for (AgentJarVersion version : agent.versions()) {
            written += render(engine, "files",
                    pages.files(agent, version.version(),
                            catalog.listFiles(agent.artifactId(), version.version())),
                    agentDirectory.resolve(version.version()).resolve("files/index.html"));
        }
        return written;
    }

    /** Renders one template, flagged as static, to a file. */
    private int render(
            SpringTemplateEngine engine, String template, Map<String, Object> model, Path target) {
        model.put(STATIC_FLAG, Boolean.TRUE);
        Context context = new Context(Locale.ENGLISH, model);
        write(target, engine.process(template, context));
        return 1;
    }

    /**
     * A private engine sharing the application's template resolvers.
     *
     * <p>The base path is a property of one generation run, not of the application, so it is set
     * on a throwaway engine rather than on the shared bean the live registry renders with.
     */
    private SpringTemplateEngine engineFor(String basePath) {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolvers(templateEngine.getTemplateResolvers());
        engine.setLinkBuilder(new BasePathLinkBuilder(basePath));
        return engine;
    }

    /**
     * Writes the JSON API as flat files. The live API answers {@code /api/agents}; a static host
     * cannot serve a directory as JSON, so the same payloads get an explicit {@code .json} name.
     */
    private int writeApi(List<AgentJar> agents, Path outputDirectory) {
        Path api = outputDirectory.resolve("api");
        write(api.resolve("agents.json"), objectMapper.writeValueAsString(Map.of(
                "groupId", properties.groupId(),
                "count", agents.size(),
                "agents", agents.stream().map(StaticSiteGenerator::summary).toList())));

        int written = 1;
        for (AgentJar agent : agents) {
            write(api.resolve("agents").resolve(agent.artifactId() + ".json"),
                    objectMapper.writeValueAsString(detail(agent)));
            written++;
        }
        return written;
    }

    private static Map<String, Object> summary(AgentJar agent) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("artifactId", agent.artifactId());
        summary.put("name", agent.displayName());
        summary.put("description", agent.description());
        summary.put("latestVersion", agent.latestVersion() == null ? "" : agent.latestVersion());
        summary.put("license", agent.license());
        summary.put("tags", agent.tags());
        summary.put("sourceRepository", agent.sourceRepo() == null ? "" : agent.sourceRepo());
        return summary;
    }

    private static Map<String, Object> detail(AgentJar agent) {
        Coordinates coordinates = agent.latestCoordinates();
        Map<String, Object> detail = new LinkedHashMap<>(summary(agent));
        detail.put("groupId", agent.groupId());
        detail.put("tools", agent.manifest() == null ? List.of() : agent.manifest().tools());
        detail.put("versions", agent.versions().stream()
                .map(version -> Map.of(
                        "version", version.version(),
                        "commit", version.commit() == null ? "" : version.commit()))
                .toList());
        Map<String, String> dependencies = new LinkedHashMap<>();
        for (BuildTool tool : BuildTool.values()) {
            dependencies.put(tool.id(), tool.snippet(coordinates));
        }
        detail.put("dependencies", dependencies);
        return detail;
    }

    /** Copies the CSS, JavaScript and images the templates link to. */
    private int copyStaticResources(Path outputDirectory) {
        int copied = 0;
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:/static/**");
            for (Resource resource : resources) {
                String path = relativeStaticPath(resource);
                if (path == null) {
                    continue;
                }
                Path target = outputDirectory.resolve(path);
                Files.createDirectories(target.getParent());
                try (var in = resource.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                copied++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not copy static resources", e);
        }
        return copied;
    }

    /** The path of a classpath resource below {@code /static}, or null for directories. */
    private static String relativeStaticPath(Resource resource) throws IOException {
        if (!resource.isReadable()) {
            return null;
        }
        String url = resource.getURL().toString();
        int marker = url.lastIndexOf("/static/");
        if (marker < 0) {
            return null;
        }
        String path = url.substring(marker + "/static/".length());
        return path.isEmpty() || path.endsWith("/") ? null : path;
    }

    private int writeMarker(Path target, String content) {
        write(target, content);
        return 1;
    }

    private static void write(Path target, String content) {
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + target, e);
        }
    }

    /** Creates the output directory, clearing it first so removed pages do not linger. */
    private static void prepare(Path outputDirectory) {
        if (Files.exists(outputDirectory)) {
            try (Stream<Path> paths = Files.walk(outputDirectory)) {
                List<Path> entries = paths.sorted(Comparator.reverseOrder()).toList();
                for (Path entry : entries) {
                    Files.deleteIfExists(entry);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Could not clear " + outputDirectory, e);
            }
        }
        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create " + outputDirectory, e);
        }
    }

    /** Kept for callers that want the list of pages without writing them. */
    public List<String> pagePaths() {
        List<String> paths = new ArrayList<>(
                List.of("index.html", "agents/index.html", "docs/index.html",
                        "deploy/index.html", "404.html", "api/agents.json"));
        for (AgentJar agent : catalog.findAll()) {
            paths.add("agents/" + agent.artifactId() + "/index.html");
            for (AgentJarVersion version : agent.versions()) {
                paths.add("agents/" + agent.artifactId() + "/" + version.version() + "/files/index.html");
            }
        }
        return List.copyOf(paths);
    }
}
