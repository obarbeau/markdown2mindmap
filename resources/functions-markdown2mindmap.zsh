#!/usr/bin/env zsh

function markdown2mindmap_convert() {
  local input_file=$1
  local output_dir=$2
  cd $CLJ/markdown2mindmap
  clojure -M:run-m convert --with-puml --style resources/custom.css \
    --type svg $input_file $output_dir
}

function markdown2mindmap_github_actions_local() {
  cd $CLJ/markdown2mindmap
  nix-shell --packages act --command "act --secret GITHUB_TOKEN=$GITHUB_PAT_WORKFLOWS; return"
}
