function markdown2mindmap_test() {
  cd $CLJ/markdown2mindmap
  clojure -M:test/kaocha --watch
}

function markdown2mindmap_repl() {
  cd $CLJ/markdown2mindmap
  clojure -M:repl/rebel
}
