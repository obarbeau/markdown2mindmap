# Markdown2mindmap

This small application converts Markdown files to Mind maps.

It is written in Clojure and uses:

- [Cybermonday](https://github.com/kiranshila/cybermonday)
  to convert markdown text to a hiccup AST.
- [PlantUML](https://plantuml.com/mindmap-diagram) to create the images.

The two main objectives for this app are:

- quick and easy production of PNG images;
- use any independant markdown document as source.
  The hiccup AST provided by Cybermonday
  enables the processing and filtering of markdown elements.

## Installation

FIXME: explanation

Download from https://github.com/obarbeau/markdown2mindmap

## Usage

FIXME: explanation

Run the application: see [usage](src/markdown2mindmap/core.clj#15)

Run the project's tests
(this will generate test images in the output directory):

```shell
clojure -T:dev:build test
```

Run the project's CI pipeline and build an uberjar:

```shell
clojure -T:dev:build ci
```

This will produce an updated `pom.xml` file with synchronized dependencies inside the `META-INF`
directory inside `target/classes` and the uberjar in `target`. You can update the version (and SCM tag)
information in generated `pom.xml` by updating `build.clj`.

If you don't want the `pom.xml` file in your project, you can remove it. The `ci` task will
still generate a minimal `pom.xml` as part of the `uber` task, unless you remove `version`
from `build.clj`.

Run that uberjar:

```shell
java -jar target/markdown2mindmap-0.1.0-SNAPSHOT.jar
```

If you remove `version` from `build.clj`, the uberjar will become `target/markdown2mindmap-standalone.jar`.

## Options

FIXME: listing of options this app accepts.

## Examples

...

## Todo

- allows nested text modifiers

## License

Copyright © 2021 Olivier Barbeau

Distributed under the Eclipse Public License either version 1.0
or (at your option) any later version.
