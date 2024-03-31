# Todo

- _Front matter_:
  If necessary, manage the file options in the Front matter, why not in TOML

- _progress bar on the CLI_:
  using `Intervox/clj-progress` or `weavejester/progrock`
  or `lispyclouds/bblgum` (spinner)

- _node contents_:
  leave the option, when creating the nodes,
  to keep only the paragraph and list headings,
  or on the contrary to use the whole text
  and then the content of the node is this text.

- _mkdocs_:
  mkdocs plugin <https://www.mkdocs.org/dev-guide/plugins>
  to automatically generate mindmaps (`.mm.md`).
  `$HOME/.local/lib/python3.8/site-packages`

- _hiccup_:
  add an option to generate the intermediate hiccup file

- _badges_:
  add badges to the readme: CI ok, version, tests ok, etc

- _Templates_:

  - apply template transformations (style/color) on the generated hiccup,
    with `cgrand` lib for example.
  - And/or with `noprompt/garden`?
  - see <https://www.planttext.com/> for examples.

- _Colors and themes_:

  Use colors for nodes/edges:
  rgb(214, 39, 40) rgb(44, 160, 44) rgb(148, 103, 189)
  rgb(140, 86, 75) rgb(227, 119, 194) rgb(127, 127, 127)
  rgb(188, 189, 34) rgb(23, 190, 207) rgb(31, 119, 180) rgb(255, 127, 14)

  Use PlantUML themes: bluegray/cerulean/minty/sandstone/silver

- _automatic left/right distribution of nodes_.

- <https://github.com/d4span/evermind>: a été archivé.
