# Todo

- _bug_:
  Hiccup isn't always strict about nesting elements.
  This may be the reason for the bug in `input-08.md`.
  One solution might be to bypass Hiccup and use an AST
  to transform markdown into puml.
  Cf Library `nextjournal/markdown`.

- investigate Coogle

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
  to automatically generate mindmaps (`.3md`).
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

- cf <https://github.com/jimmyhmiller/PlayGround/blob/master/markdown-to-blog/src/markdown_to_blog/core.clj>

pistes d'amélioration:

1. Absence de gestion d'erreurs
   Aucun `try/catch` dans le code. Les fichiers invalides, erreurs PlantUML ou problèmes I/O ne sont pas gérés. Un fichier `.3md` malformé provoquera un crash sans message explicite.

2. État mutable via atom
   Le pattern `result` atom traversant `enter-*`/`exit-*` fonctionne mais rend le code difficile à tester unitairement et à raisonner. Une alternative serait un reduce avec état immutable ou un state monad.

3. Code commenté
   `hiccup->puml-file` (transform.clj:127) est commenté. À supprimer ou réactiver.

4. Chemins hardcodés
   `./output/markdown2mindmap.log` dans `log.clj` devrait être configurable.

5. Bug connu non résolu
   Le `todo.md` mentionne un bug de nesting hiccup dans `input-08.md` depuis longtemps. La piste `nextjournal/markdown` est suggérée mais non explorée.

6. README incomplet
   Plusieurs sections "FIXME" dans le README (Installation, Options, Examples).

7. Potentiel de simplification dans exit.clj
   Les fonctions `last-simple-child?`/`last-nested-child?`/`last-child?` avec leurs tuples de retour `[bool value]` sont un peu verbeuses. Un protocole ou multimethod pourrait clarifier.

8. Logs verbeux en production
   Le niveau `:debug` est actif par défaut dans `log.clj`. Devrait être configurable ou `:info` par défaut.

Le plus impactant serait probablement d'ajouter la gestion d'erreurs et de résoudre le bug hiccup. Veux-tu que j'approfondisse un point en particulier?
