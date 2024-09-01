#!/usr/bin/env zsh

function markdown2mindmap_github_actions_local() {
  cd $CLJ/markdown2mindmap
  nix-shell --packages act --command "act --secret GITHUB_TOKEN=$GITHUB_PAT_WORKFLOWS; return"
}
