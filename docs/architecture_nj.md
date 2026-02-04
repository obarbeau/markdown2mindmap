# Architecture de la conversion Markdown vers Mindmap (nextjournal/markdown)

Ce document décrit l'implémentation utilisant la bibliothèque `nextjournal/markdown`.
Pour l'ancienne implémentation avec Cybermonday, voir `architecture.md`.

## Vue d'ensemble

La nouvelle implémentation est significativement plus simple grâce à l'AST structuré
fourni par nextjournal/markdown. Elle tient dans un seul fichier (`transform_nj.clj`)
au lieu de trois.

```text
┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   Fichier    │     │  Préprocess  │     │     AST      │     │   PlantUML   │
│   Markdown   │────▶│  HTML → MD   │────▶│   Structuré  │────▶│    Mindmap   │
│    (.3md)    │     │              │     │              │     │              │
└──────────────┘     └──────────────┘     └──────────────┘     └──────────────┘
                                                │
                                          md/parse
```

## Pipeline de transformation

```text
         Markdown String
              │
              ▼
    ┌─────────────────────┐
    │ preprocess-markdown │   Convertit <s> → ~~, <b> → **, <i> → *
    └─────────────────────┘
              │
              ▼
    ┌─────────────────────┐
    │      md/parse       │   nextjournal.markdown/parse
    └─────────────────────┘
              │
              ▼
    ┌─────────────────────┐
    │    AST Document     │   {:type :doc, :content [...], :toc {...}}
    └─────────────────────┘
              │
              ▼
    ┌─────────────────────┐
    │     ast->puml       │   Traversée récursive avec contexte
    └─────────────────────┘
              │
              ▼
    ┌─────────────────────┐
    │   PlantUML Lines    │   ["* Titre", "**_ item", ...]
    └─────────────────────┘
              │
              ▼
    ┌─────────────────────┐
    │    ->puml-wrapped   │   Ajoute @startmindmap/@endmindmap + styles
    └─────────────────────┘
              │
              ▼
         Image SVG/PNG
```

## Structure de l'AST nextjournal/markdown

Chaque noeud possède un `:type` explicite, ce qui facilite le pattern matching:

```clojure
{:type :doc
 :content [{:type :heading
            :heading-level 1
            :content [{:type :text, :text "Titre"}]}
           {:type :bullet-list
            :content [{:type :list-item
                       :content [{:type :plain
                                  :content [{:type :text, :text "Item 1"}]}
                                 {:type :bullet-list
                                  :content [...]}]}]}]
 :toc {...}
 :footnotes []}
```

### Types de noeuds principaux

| Type             | Description                 | Contenu                                    |
| ---------------- | --------------------------- | ------------------------------------------ |
| `:doc`           | Document racine             | `:content`, `:toc`, `:footnotes`           |
| `:heading`       | Titre H1-H6                 | `:heading-level`, `:content`, `:attrs`     |
| `:paragraph`     | Paragraphe                  | `:content`                                 |
| `:bullet-list`   | Liste à puces               | `:content` (list-items)                    |
| `:numbered-list` | Liste numérotée             | `:content` (list-items)                    |
| `:list-item`     | Élément de liste            | `:content` (plain/paragraph + sous-listes) |
| `:plain`         | Texte simple (liste serrée) | `:content` (text nodes)                    |
| `:text`          | Texte brut                  | `:text`                                    |
| `:strong`        | Gras                        | `:content`                                 |
| `:em`            | Italique                    | `:content`                                 |
| `:strikethrough` | Barré                       | `:content`                                 |

## Algorithme de traversée

La traversée utilise une récursion simple avec un contexte immutable:

```text
                    ast->puml-lines
                          │
        ┌─────────────────┼─────────────────┐
        │                 │                 │
    :heading         :bullet-list      :list-item
        │                 │                 │
  process-heading   process-list    process-list-item
        │                 │                 │
        │                 │          ┌──────┴──────┐
        │                 │          │             │
        │                 │     :plain/:para   :bullet-list
        │                 │          │             │
        │                 │     node->text    (récursion)
        │                 │          │
        └─────────────────┴──────────┴─────────────┘
                          │
                    PlantUML lines
```

### Contexte de traversée

```clojure
{:heading-level 2    ; Niveau du dernier heading rencontré (1-6)
 :list-depth 3}      ; Profondeur actuelle dans les listes imbriquées
```

Le niveau total d'un item de liste = `heading-level + list-depth`

## Gestion des listes imbriquées

C'est ici que la nouvelle implémentation corrige le bug de l'ancienne.

```text
Markdown:                          AST:
─────────────────────────────────────────────────────────────
# A                                :heading (level 1)
                                   │
- B                                :bullet-list ─────────────┐
  - C                                │                       │
  - H                                :list-item (B)          │
    - I                                │                     │
    - J                                ├─ :plain "B"     depth=1
                                       │                     │
                                       └─ :bullet-list ──────┤
                                            │                │
                                            :list-item (C)   │
                                            │            depth=2
                                            :list-item (H)   │
                                              │              │
                                              ├─ :plain "H"  │
                                              │              │
                                              └─ :bullet-list┤
                                                   │         │
                                                   :list-item│
                                                   (I)   depth=3
                                                   │         │
                                                   :list-item│
                                                   (J)   depth=3
```

Résultat PlantUML:

```text
* A              (heading level 1)
**_ B            (1 + 1 = 2)
***_ C           (1 + 2 = 3)
***_ H           (1 + 2 = 3)
****_ I          (1 + 3 = 4)  ← Correct!
****_ J          (1 + 3 = 4)  ← Correct!
```

## Comparaison avec l'ancienne implémentation

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                        CYBERMONDAY (legacy)                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   transform.clj ◄───► enter.clj ◄───► exit.clj                              │
│        │                  │               │                                 │
│    walk-node          enter-*          exit-*                               │
│    walk-vector        now-inside       exit-if-required                     │
│    walk-children      log-enter        process-string                       │
│        │                  │               │                                 │
│        └──────────────────┴───────────────┘                                 │
│                           │                                                 │
│                    État via contexte                                        │
│                    {:heading {:level 1, :inside true, :children 2}          │
│                     :ol-ul {:inside true, :children (2 3)}  ← pile          │
│                     :buffer "..."                                           │
│                     :puml (...)}                                            │
│                                                                             │
│   Pattern: Machine à états enter/exit avec compteurs d'enfants              │
│   Complexité: ~400 LOC sur 3 fichiers                                       │
│   Bug: Compteur de profondeur incorrect pour listes imbriquées              │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                     NEXTJOURNAL/MARKDOWN (nouveau)                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   transform_nj.clj (tout-en-un)                                             │
│        │                                                                    │
│    md->ast ──► ast->puml ──► ->puml-wrapped                                 │
│                    │                                                        │
│              ┌─────┴─────┐                                                  │
│              │           │                                                  │
│        process-heading  process-list                                        │
│              │           │                                                  │
│              │     process-list-item                                        │
│              │           │                                                  │
│              └─────┬─────┘                                                  │
│                    │                                                        │
│              node->text (inline modifiers)                                  │
│                                                                             │
│   Contexte simple: {:heading-level 1, :list-depth 2}                        │
│                                                                             │
│   Pattern: Récursion simple avec contexte passé en paramètre                │
│   Complexité: ~150 LOC dans 1 fichier                                       │
│   Bug: Corrigé (l'AST reflète fidèlement l'imbrication)                     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Fonctions principales

### Prétraitement

```clojure
(preprocess-markdown md-str)
```

Convertit les balises HTML en syntaxe Markdown standard:

- `<s>text</s>` → `~~text~~`
- `<b>text</b>` → `**text**`
- `<i>text</i>` → `*text*`

Nécessaire car nextjournal/markdown ne supporte pas le HTML inline.

### Parsing

```clojure
(md->ast markdown-str)
```

Appelle `nextjournal.markdown/parse` après prétraitement.
Retourne l'AST complet avec table des matières et footnotes.

### Traversée

```clojure
(ast->puml ast)
```

Parcourt l'AST et génère les lignes PlantUML.
Maintient le contexte `{:heading-level :list-depth}` à travers la récursion.

### Extraction de texte

```clojure
(node->text node)
```

Extrait le texte d'un noeud en appliquant les modifiers inline:

- `:strong` → `<b>...</b>`
- `:em` → `<i>...</i>`
- `:strikethrough` → `<s>...</s>`

## Commandes CLI

```bash
# Nouvelle implémentation (recommandée)
clojure -M:run-m convert [options] <fichier.3md>

# Ancienne implémentation (pour comparaison)
clojure -M:run-m convert-legacy [options] <fichier.3md>

# Options communes
--style FICHIER.css     Applique un style personnalisé
--with-puml             Génère le fichier .puml intermédiaire
--puml-output-dir DIR   Répertoire de sortie pour .puml
--with-svg              Génère le fichier SVG
--svg-output-dir DIR    Répertoire de sortie pour SVG
-t png                  Format PNG au lieu de SVG
```

## Points d'extension

Pour ajouter le support d'un nouveau type de noeud:

```clojure
;; Dans ast->puml-lines, ajouter un case:
:nouveau-type
(process-nouveau-type node context)

;; Implémenter la fonction:
(defn- process-nouveau-type [node context]
  ;; Retourner une séquence de lignes PlantUML
  [...])
```

Pour modifier le rendu d'un type inline dans `node->text`:

```clojure
:mon-type (str "<tag>" (apply str (map node->text (:content node))) "</tag>")
```

## Avantages de cette implémentation

1. **Simplicité**: Un seul fichier, récursion naturelle
2. **Lisibilité**: Le flux de données est linéaire et explicite
3. **Testabilité**: Fonctions pures, pas d'état mutable
4. **Correction**: L'AST structuré évite les bugs de comptage
5. **Extensibilité**: Ajouter un type = ajouter un case + une fonction
