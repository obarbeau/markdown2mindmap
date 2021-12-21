# Markdown2mindmap

This small library converts Markdown files to Mind maps.

Its main objective is the quick and easy production of PNG images.

It is written in Clojure and uses:

- [Cybermonday](https://github.com/kiranshila/cybermonday)
  to convert markdown text to a hiccup AST.
  That way the processing and filtering of elements is simplified.
- [PlantUML](https://plantuml.com/mindmap-diagram) to create the images.

## Installation

FIXME: explanation

Download from https://github.com/obarbeau/markdown2mindmap

## Usage

FIXME: explanation

Run the project directly, via `:main-opts` (`-m markdown2mindmap.core`):

```shell
clojure -M:run-m <input-file> <output-file>
# For example
clojure -M:run-m test-resources/input-07.md ./mindmap.png
```

Run the project's tests
(this will generate test images in the output directory):

```shell
clojure -T:build test
```

Run the project's CI pipeline and build an uberjar:

```shell
clojure -T:build ci
```

This will produce an updated `pom.xml` file with synchronized dependencies inside the `META-INF`
directory inside `target/classes` and the uberjar in `target`. You can update the version (and SCM tag)
information in generated `pom.xml` by updating `build.clj`.

If you don't want the `pom.xml` file in your project, you can remove it. The `ci` task will
still generate a minimal `pom.xml` as part of the `uber` task, unless you remove `version`
from `build.clj`.

Run that uberjar:

    $ java -jar target/markdown2mindmap-0.1.0-SNAPSHOT.jar

If you remove `version` from `build.clj`, the uberjar will become `target/markdown2mindmap-standalone.jar`.

## Options

FIXME: listing of options this app accepts.

## Examples

...

## License

Copyright © 2021 Olivier

_EPLv1.0 is just the default for projects generated by `deps-new`: you are not_
_required to open source this project, nor are you required to use EPLv1.0!_
_Feel free to remove or change the `LICENSE` file and remove or update this_
_section of the `README.md` file!_

Distributed under the Eclipse Public License version 1.0.
