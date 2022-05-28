#!/usr/bin/env zsh

function markdown2mindmap_test() {
  cd $CLJ/markdown2mindmap
  clojure -T:dev:build test-watch
}

function markdown2mindmap_repl() {
  cd $CLJ/markdown2mindmap
  clojure -M:dev:repl/rebel
}

function markdown2mindmap_convert() {
  local input_file=$1
  local output_dir=$2
  cd $CLJ/markdown2mindmap
  clojure -M:run-m convert --style resources/custom.css \
    --type svg $input_file $output_dir
}
