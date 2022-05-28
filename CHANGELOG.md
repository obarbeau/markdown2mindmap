# Change Log

All notable changes to this project will be documented in this file.
This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased] 0.1.0-SNAPSHOT - 2021-12-21

### Added

- git hooks to ensure documentation and changelog are always up to date
- clean output directory before tests
- generates either SVG or PNG format
- uses `puml` instead of plantuml
- adds `list-all-fonts` action
- adds `--style STYLE-FILE` option
- adds `--with-puml`
- adds `run-task-enhanced` and refactor `build.clj`
- corrects Eastwood's linting errors
- remove aliases that are already provided by `practicalli`
- add `markdown2mindmap_convert` helper
- uses `style` instead of `skinparam` for custom styles
