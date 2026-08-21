# AgentJars

A registry for **agent definitions packaged as jars** and published to Maven Central.

An agent is a directory with an `AGENT.md` in it. AgentJars packages that directory into an
ordinary jar under the `com.agentjars` group, so agents become build dependencies: versioned,
resolvable, transitive, and recorded in your lockfile — instead of markdown files copied between
repositories and slowly drifting apart.

```xml
<dependency>
    <groupId>com.agentjars</groupId>
    <artifactId>myorg__my-agents__code-reviewer</artifactId>
    <version>2026_07_28-9c1f4ab</version>
</dependency>
```

```java
Path agents = AgentJarsExtractor.create().extractTo(Path.of("build/agents"));
// build/agents/code-reviewer/AGENT.md
```

## What this repository contains

The web application behind the registry:

- **Browse and search** every agent published under the group, filtered by free text or tag.
- **Detail pages** with dependency snippets for Maven, Gradle (Kotlin and Groovy) and sbt, the
  full version history, and the file listing of any published jar.
- **Publishing**: submit a public GitHub repository and every agent in it is scanned, licensed,
  packaged, and bundled for upload to Maven Central.
- **A JSON API** at `/api/agents` for tooling that resolves agents without scraping HTML.
- **A runtime extractor** (`AgentJarsExtractor`) that unpacks every AgentJar on the classpath into
  one directory an agent runtime can read.

## Running it

Requires Java 26.

```bash
./mvnw spring-boot:run                                  # http://localhost:8080
./mvnw spring-boot:run -Dspring-boot.run.profiles=local  # bundled catalog, no network
./mvnw test
```

The `local` profile switches Maven Central off and serves the catalog bundled at
`src/main/resources/catalog/bundled-agents.json`, so the site is fully browsable offline.

With Docker:

```bash
docker build -t agentjars .
docker run --rm -p 8080:8080 agentjars
```

## Deploying

### GitHub Pages (read-only)

The registry renders to a directory of static files, which `.github/workflows/pages.yml` publishes
to GitHub Pages on every push to `main`, on a daily schedule, and on demand.

```bash
./mvnw -B -DskipTests package
java -jar target/agentjars-webapp-*.jar \
  --spring.main.web-application-type=none \
  --agentjars.static.output=target/site \
  --agentjars.static.base-path=/AgentJars
```

**One-time setup:** in the repository's *Settings → Pages*, set **Source** to **GitHub Actions**.
The workflow cannot enable Pages for you.

The base path is resolved by `actions/configure-pages`, so it is `/<repository>` for a project
site and empty once a custom domain is configured — no change needed either way.

What carries over, and what does not:

| | Static site | Running the app |
| --- | --- | --- |
| Browse, tag filter, search | ✅ (filtered in the browser) | ✅ |
| Detail pages, version history | ✅ | ✅ |
| Dependency snippets + copy button | ✅ | ✅ |
| Jar file listings | ✅ (as of the last build) | ✅ (live) |
| JSON API | ✅ at `/api/agents.json` | ✅ at `/api/agents` |
| Catalog freshness | rebuilt daily by the workflow | live, cached for an hour |
| **Publishing an agent** | ❌ needs a server | ✅ |

Publishing is the only thing a static host cannot do: packaging means cloning a repository and
building jars. The publish page says so and points at running the registry locally.

### As a server

Anywhere that runs a container — see the Dockerfile below. That gives you the publish flow and a
catalog read live from Maven Central.

## The AGENT.md format

```markdown
---
name: Code Reviewer
description: Reviews a diff for correctness bugs, missing test coverage and needless complexity.
model: reasoning
tools: [read_file, grep, list_directory, run_tests]
tags: [review, quality, testing]
license: Apache-2.0
---

# Code Reviewer

Read the diff before the surrounding code. Report findings ranked by severity...
```

Only `name` is required. `description` falls back to the first prose paragraph, and `license`
falls back to a `LICENSE` file next to the agent or at the repository root. Everything else in the
agent's directory is packaged alongside the manifest.

See [`examples/agents`](examples/agents) for working examples.

## Coordinates and versions

The artifactId is derived from the source, so any coordinate leads back to the code that produced
it:

```
<org>__<repo>__<agent>

myorg/my-agents  +  agents/reviewer     →  myorg__my-agents__reviewer
myorg/my-agents  +  <repository root>   →  myorg__my-agents
```

Each segment is lowercased and stripped of anything that is not alphanumeric, a hyphen or an
underscore. A leading `agents/` is dropped, so an agent can move between `reviewer/` and
`agents/reviewer/` without changing its coordinate.

Versions are `YYYY_MM_DD-<short-commit>` — the day the agent was packaged and the commit it came
from. Sortable, and traceable to an exact revision.

## Inside a packaged jar

```
META-INF/agents/
META-INF/agents/agentjars.index
META-INF/agents/code-reviewer/AGENT.md
META-INF/agents/code-reviewer/checklist.md
META-INF/MANIFEST.MF
```

The index file marks the jar as an AgentJar so the extractor can find it on a classpath without
opening every jar. The manifest records the agent name, its root inside the jar, and the
repository and path it was packaged from.

## Publishing rules

Two rules decide whether a repository can be packaged:

- **Agent directories may not nest.** A nested agent would be packaged twice — once alone and once
  inside its parent — under two coordinates, so an overlap fails the run rather than being
  silently resolved.
- **Every agent needs a license.** Maven Central requires one. Resolution runs most specific
  first: the front matter, a `LICENSE` next to the agent, then the repository root. An agent that
  resolves to nothing is skipped and reported; the rest of the repository still publishes.

## Configuration

| Property | Default | Purpose |
| --- | --- | --- |
| `agentjars.group-id` | `com.agentjars` | Group every AgentJar is published under |
| `agentjars.agents-root` | `META-INF/agents` | Directory inside a jar holding the agents |
| `agentjars.maven-central.enabled` | `true` | Read the catalog from Maven Central |
| `agentjars.maven-central.search-url` | Central search API | Enumerates artifacts and versions |
| `agentjars.maven-central.content-url` | `https://repo1.maven.org/maven2` | Serves poms and jars |
| `agentjars.cache.catalog-ttl` | `PT1H` | How long a catalog listing is reused |
| `agentjars.deploy.enabled` | `true` | Accept publishing submissions |
| `agentjars.deploy.max-agents` | `50` | Refuse repositories declaring more agents than this |
| `agentjars.deploy.work-dir` | temp dir | Scratch space for clones and bundles |

## Project layout

```
src/main/java/com/agentjars/
├── catalog/     reading the registry: Maven Central client, jar inspection, caching
├── config/      configuration properties, caching, clock
├── deploy/      cloning a repository and packaging the agents it declares
├── model/       coordinates, versions, manifests, repository references
├── packaging/   AGENT.md parsing, scanning, license resolution, jar building
├── runtime/     AgentJarsExtractor — the consumer-side classpath extractor
├── staticsite/  rendering the registry to files for a static host
└── web/         controllers, and the page models both renderers share
```

Both renderers take their model from `web/PageModel`, so a change to a page cannot reach the live
site without also reaching the static build.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
