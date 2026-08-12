# Example agents

Two working agents laid out the way a publishable repository looks.

```
examples/
└── agents/
    ├── code-reviewer/
    │   ├── AGENT.md
    │   └── checklist.md
    └── release-notes/
        └── AGENT.md
```

Packaged from a repository at `myorg/my-agents`, these would be published as:

| Agent | Coordinate |
| --- | --- |
| `agents/code-reviewer` | `com.agentjars:myorg__my-agents__code-reviewer` |
| `agents/release-notes` | `com.agentjars:myorg__my-agents__release-notes` |

Everything in an agent's directory travels with it — `code-reviewer` ships `checklist.md`
alongside its manifest, and the agent references it by name because both land in the same
directory after extraction.

Note that these live under `examples/` rather than `agents/` on purpose: this repository holds the
registry itself, not agents meant to be published from it.
