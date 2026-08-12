# AgentJars — specification

The contract the registry implements. Where the implementation and this document disagree, the
tests decide.

## 1. Purpose

AgentJars is a Maven Central registry for **agent definitions**, following the WebJars model. An
agent published as a jar becomes a build dependency: pinned to a version, resolved transitively,
recorded in a lockfile, and cached by the same infrastructure that already serves the rest of the
build.

The registry lets a user:

- browse and search agents published under the `com.agentjars` groupId;
- read the coordinates and dependency snippet for any version;
- inspect the files inside a published jar;
- publish new AgentJars from a public GitHub repository.

## 2. The agent format

An agent is a directory containing an `AGENT.md`.

```markdown
---
name: Code Reviewer                 # required
description: Reviews a diff...      # optional, falls back to the first prose paragraph
model: reasoning                    # optional
tools: [read_file, grep]            # optional, inline or block list
tags: [review, quality]             # optional, inline or block list
license: Apache-2.0                 # optional, falls back to a LICENSE file
homepage: https://...               # optional
---

Instructions.
```

The front matter parser accepts the subset of YAML that agent manifests use: scalars, inline
lists, and block lists. Quoted values are unquoted; a comma-separated scalar in a list field is
split. A document with no front matter yields a manifest whose name is the directory name and
whose description is the first prose paragraph.

Every other file in the agent's directory is packaged with it, at the same relative path.

## 3. Discovery

Scanning a repository walks it to a depth of 8, skipping `.git`, `.github`, `node_modules`,
`target`, `build`, `out`, `.idea` and `dist`. Every `AGENT.md` found marks its directory as an
agent.

**Agent directories may not nest.** If one discovered agent's path is a prefix of another's, the
scan fails: the outer jar would contain the inner agent, publishing the same content under two
coordinates.

## 4. Coordinates

- **groupId** — always `com.agentjars` (configurable per instance).
- **artifactId** — `<org>__<repo>__<agent>`, derived from the source. Each segment is lowercased;
  path separators inside the agent path become single hyphens; every character that is not
  alphanumeric, a hyphen or an underscore is dropped. A leading `agents/` segment is stripped, and
  an agent at the repository root omits the third segment entirely.
- **version** — `YYYY_MM_DD-<short-commit>`: the packaging date and the 7-character commit hash.

| Source | artifactId |
| --- | --- |
| `myorg/myrepo` + `agents/reviewer` | `myorg__myrepo__reviewer` |
| `myorg/myrepo` + `reviewer` | `myorg__myrepo__reviewer` |
| `myorg/myrepo` + repository root | `myorg__myrepo` |
| `MyOrg/My-Repo` + `Agents/Review` | `myorg__my-repo__review` |
| `myorg/myrepo` + `agents/a/b` | `myorg__myrepo__a-b` |

## 5. Licensing

Maven Central requires a license on every artifact. Resolution runs most specific first:

1. the `license` (or `spdx`) key in the `AGENT.md` front matter;
2. a `LICENSE`, `LICENSE.txt`, `LICENSE.md`, `COPYING` or `COPYING.txt` in the agent's directory;
3. the same, at the repository root.

License files are identified by fingerprinting their text against the common licenses. An agent
whose license cannot be resolved is **skipped and reported**, not failed — a repository usually
holds several agents, and one missing LICENSE should not block the rest.

## 6. Jar layout

```
META-INF/agents/agentjars.index          <agent-slug>=META-INF/agents/<slug>/AGENT.md
META-INF/agents/<slug>/AGENT.md
META-INF/agents/<slug>/...               everything else from the agent directory
META-INF/MANIFEST.MF
```

Manifest attributes:

| Attribute | Value |
| --- | --- |
| `Agent-Name` | the agent's name |
| `Agent-Root` | `META-INF/agents/<slug>` |
| `Agent-Source-Repository` | the GitHub repository url |
| `Agent-Source-Path` | path inside that repository (absent for root agents) |
| `Agent-Model` | suggested model tier, when declared |

The index file exists in every AgentJar and nowhere else; it is the marker the extractor uses to
find AgentJars on a classpath without opening every jar.

## 7. Publishing

`POST /deploy` with a repository reference:

1. Parse the reference. `org/repo`, https, ssh and browser urls are all accepted.
2. Shallow-clone (depth 1) the public repository into a scratch workspace.
3. Scan for agents; fail on overlap, on zero agents, or on more agents than
   `agentjars.deploy.max-agents`.
4. Resolve a license per agent; skip and report the ones that resolve to nothing.
5. Package each remaining agent: the agent jar, empty sources and javadoc jars, and a pom carrying
   the name, description, url, license, developer and scm entries Central validates.
6. Zip each agent's four files into an upload bundle laid out at the coordinate's repository path.
7. Evict the catalog cache if anything was packaged, and delete the clone.

The run reports what was packaged and what was skipped, with a reason for each skip.

## 8. Reading the catalog

The catalog is assembled from two sources, merged by artifactId:

1. the **bundled catalog** (`catalog/bundled-agents.json`), always available;
2. **Maven Central**, when enabled — the search API enumerates artifacts and versions, and each
   agent's descriptive metadata is read from the `AGENT.md` inside its published jar.

Central entries win over bundled ones, so a bundled entry can never mask a real published
artifact. Every Central call is bounded by a connect and read timeout and degrades to an empty
result on failure, so an outage downgrades the registry to the bundled catalog rather than taking
it down.

Results are cached in memory and expire as one unit on `agentjars.cache.catalog-ttl`; a successful
deployment evicts them immediately.

Search is case-insensitive over name, artifactId, description, source repository and tags. Tag
filtering is an exact, case-insensitive match. There is no pagination.

## 9. Routes

| Route | Purpose |
| --- | --- |
| `GET /` | Landing page: search, tag filter, recently published |
| `GET /agents` | Full listing, with `q` and `tag` |
| `GET /agents/{artifactId}` | Detail page, with optional `version` and `build` |
| `GET /agents/{artifactId}/{version}/files` | File listing of one published jar |
| `GET /docs` | Documentation |
| `GET /deploy`, `POST /deploy` | Publishing form and submission |
| `GET /api/agents` | JSON listing, with `q` and `tag` |
| `GET /api/agents/{artifactId}` | JSON detail with versions and dependency snippets |
| `GET /api/agents/{artifactId}/{version}/files` | JSON file listing |
| `GET /actuator/health` | Liveness and readiness |

An unknown agent or version is a 404. Errors render in the site's own layout.

## 10. Consuming

`AgentJarsExtractor` walks the classpath for jars carrying an agents root and copies each agent
into a target directory, keeping the `<slug>/...` layout so agents from different jars never
collide. Entry names that would resolve outside the target directory are rejected. The class
depends on nothing but the JDK so it can be lifted into a standalone library.

## 11. Technical stack

Java 21, Spring Boot 4 (Spring MVC, Thymeleaf, caching, actuator), Eclipse JGit for cloning, and
hand-written CSS with no build step. Storage is in-memory only: Maven Central is the system of
record.
