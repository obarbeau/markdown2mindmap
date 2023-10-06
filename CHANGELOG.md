# Change Log

All notable changes to this project will be documented in this file.
This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased] 0.1.0-SNAPSHOT - 2021-12-21

### Added

- git hooks to ensure documentation and changelog are always up to date.
- clean output directory before tests.
- generates either SVG or PNG format.
- uses `puml` instead of plantuml.
- adds `list-all-fonts` action.
- adds `--style STYLE-FILE` option.
- adds `--with-puml`.
- adds `run-task-enhanced` and refactor `build.clj`.
- corrects Eastwood's linting errors.
- removes aliases that are already provided by `practicalli`.
- adds `markdown2mindmap_convert` helper.
- uses `style` instead of `skinparam` for custom styles.
- `build` is now an external lib.
- adds `github_actions_local` with nix-shell.
- `build` is now on Github.
- `puml` files were missing plantuml directives
- process complete directory; Both SVG and PUML output are optional
