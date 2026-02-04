# PROJECT KNOWLEDGE BASE

**Generated:** 2026-02-04
**Commit:** 1b55f13
**Branch:** main

## OVERVIEW

Clojure CLI tool converting Markdown files to PlantUML mindmap images (SVG/PNG).
Uses Cybermonday for MD parsing and PlantUML for rendering.

## STRUCTURE

```
markdown2mindmap/
├── src/markdown2mindmap/   # Core namespaces (5 files, ~500 LOC)
├── test/markdown2mindmap/  # Kaocha tests
├── test-resources/         # Input .md + expected .edn/.puml fixtures
├── codox/                  # Generated API docs (DO NOT EDIT)
├── resources/              # custom.css style, shell helpers
└── dev/                    # Symlink to user.clj (external)
```

## WHERE TO LOOK

| Task                      | Location               | Notes                                                            |
| ------------------------- | ---------------------- | ---------------------------------------------------------------- |
| CLI entry, arg parsing    | `core.clj`             | `-main`, `validate-args`, `cli-options`                          |
| MD->hiccup->puml pipeline | `transform.clj`        | `md->mindmap` orchestrates full flow                             |
| Hiccup AST walking        | `transform.clj`        | `walk-fn`, `walk-hiccup` with prewalk                            |
| Element enter handlers    | `enter.clj`            | `enter-heading`, `enter-ol-ul`, `enter-li`, `enter-p`, modifiers |
| Element exit/buffer mgmt  | `exit.clj`             | `process-string`, `exit-heading`, `exit-li`                      |
| PlantUML image creation   | `transform.clj:86-100` | `create-image!` with SourceStringReader                          |
| Test fixtures             | `test-resources/`      | `input-0N.md` -> `hiccup-0N.edn` -> `map-0N.puml`                |
| Custom mindmap styling    | `resources/custom.css` | PlantUML `<style>` block                                         |

## CODE MAP

| Symbol          | Type | Location          | Role                            |
| --------------- | ---- | ----------------- | ------------------------------- |
| `md->mindmap`   | fn   | transform.clj:136 | Main entry: file/dir -> images  |
| `md->hiccup`    | fn   | transform.clj:104 | Cybermonday wrapper             |
| `hiccup->puml`  | fn   | transform.clj:118 | AST walk, reverse puml list     |
| `walk-fn`       | fn   | transform.clj:17  | Dispatch on hiccup element type |
| `-main`         | fn   | core.clj:76       | CLI entry point                 |
| `validate-args` | fn   | core.clj:47       | tools.cli arg validation        |

## CONVENTIONS

File extension: `.3md` (not `.md`) triggers processing in `md->mindmap`.

Atom-based state threading: `result` atom passed through `enter-*`/`exit-*` functions,
accumulates `:puml` list (reversed), `:buffer` string, nesting state.

Logging: Timbre configured in `log.clj`, writes to `./output/markdown2mindmap.log`.

Test pattern: Numbered fixtures `input-0N.md` -> `hiccup-0N.edn` -> `map-0N.puml`.
Tests iterate 1-8, compare round-trip conversions.

## ANTI-PATTERNS

Known bug in `input-08.md`: Hiccup nesting inconsistency.
See `todo.md` for potential fix using `nextjournal/markdown`.

Do not edit `codox/` directory (generated).

Do not edit `dev/user.clj` (symlink to external config).

## COMMANDS

```bash
# Run application
clojure -M:run-m convert --style resources/custom.css test-resources/input-07.md

# List system fonts
clojure -M:run-m list-all-fonts

# Run tests (generates SVG in output/)
clojure -T:dev:build org.corfield.build/test

# Generate API docs
clojure -T:dev:build org.corfield.build/codox

# Build uberjar
clojure -T:dev:build org.corfield.build/ci
```

## DEPENDENCIES

| Library       | Purpose                   |
| ------------- | ------------------------- |
| `cybermonday` | Markdown -> Hiccup AST    |
| `plantuml`    | Hiccup -> SVG/PNG mindmap |
| `timbre`      | Logging                   |
| `puget`       | Pretty-print EDN          |
| `tools.cli`   | CLI argument parsing      |

## NOTES

Git hooks enforce: codox docs up-to-date (pre-commit), CHANGELOG updated (pre-push).

Test runner: Kaocha with audio notification (paplay) on Linux.

Practicalli deps.edn config expected at `$XDG_CONFIG_HOME/clojure/deps.edn`.

CI: GitHub Actions on ubuntu/macOS, Java 17, clojure-cli.
