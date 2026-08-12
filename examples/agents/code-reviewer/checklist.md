# Review pass order

Run these in order. Stop at the first pass that produces findings and report those before
continuing — a correctness bug outranks everything below it.

1. **Correctness** — wrong results, crashes, silent no-ops, unhandled error paths.
2. **Boundaries** — the seam between changed and unchanged code; callers and callees.
3. **Concurrency** — shared mutable state, non-atomic read-modify-write, unbounded queues.
4. **Resources** — streams, connections and locks that are not closed on every path.
5. **Coverage** — new branches with no test.
6. **Reuse** — reimplementations of something the codebase already provides.
7. **Efficiency** — repeated work, queries in loops, needless copies of large structures.

A pass with nothing to report is a normal outcome. Record it and move on.
