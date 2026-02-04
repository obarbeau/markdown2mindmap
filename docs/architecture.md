# Architecture de la conversion Markdown vers Mindmap

Ce document décrit le fonctionnement interne de markdown2mindmap,
un outil qui transforme des fichiers Markdown en images de cartes mentales.

## Vue d'ensemble du pipeline

La conversion s'effectue en trois étapes principales:

```
┌──────────────┐      ┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│   Fichier    │      │   Hiccup     │      │    PlantUML  │      │    Image     │
│   Markdown   │─────▶│     AST      │─────▶│     Text     │─────▶│   SVG/PNG    │
│    (.3md)    │      │              │      │    (.puml)   │      │              │
└──────────────┘      └──────────────┘      └──────────────┘      └──────────────┘
                           │                      │                      │
                      cybermonday            walk-hiccup            PlantUML
                       md-to-ir               + reduce            SourceReader
```

Chaque étape est gérée par une fonction distincte dans `transform.clj`:

| Étape | Fonction        | Entrée          | Sortie                                |
| ----- | --------------- | --------------- | ------------------------------------- |
| 1     | `md->hiccup`    | String markdown | Structure hiccup (vecteurs imbriqués) |
| 2     | `hiccup->puml`  | Hiccup AST      | String PlantUML mindmap               |
| 3     | `create-image!` | String PlantUML | Fichier image SVG ou PNG              |

## Structure Hiccup

La bibliothèque Cybermonday parse le Markdown en une structure hiccup.
Le format hiccup représente du HTML/XML sous forme de vecteurs Clojure:

```text
[:tag {:attributs} enfant1 enfant2 ...]
```

Exemple de transformation Markdown vers Hiccup:

```markdown
# Titre

## Sous-titre

- item1
- item2
```

devient:

```clojure
[:div {}
 [:markdown/heading {:level 1} "Titre"]
 [:markdown/heading {:level 2} "Sous-titre"]
 [:ul {}
  [:markdown/bullet-list-item {} [:p {} "item1"]]
  [:markdown/bullet-list-item {} [:p {} "item2"]]]]
```

## Traversée de l'arbre Hiccup

La conversion hiccup vers PlantUML utilise une traversée récursive
avec un état immutable threaded via `reduce`.

```text
                        walk-node
                            │
              ┌─────────────┴─────────────┐
              │                           │
          vector?                      string?
              │                           │
        walk-vector               process-string
              │                           │
    ┌─────────┼─────────┐                 │
    │         │         │                 │
 :heading   :ul/:ol    :p              buffer
    │         │         │              update
 enter-    enter-    enter-              │
 heading   ol-ul       p                 │
    │         │         │                 │
    └─────────┴─────────┘                 │
              │                           │
        walk-children ◄───────────────────┘
         (reduce)
```

La fonction `walk-children` utilise `reduce` pour appliquer `walk-node`
à chaque enfant en passant l'état de l'un à l'autre:

```clojure
(defn- walk-children [state children]
  (reduce walk-node state children))
```

## Machine à états

L'état maintenu pendant la traversée contient:

```clojure
{:heading {:inside true/false    ; sommes-nous dans un heading?
           :level 1-6            ; niveau du heading (# à ######)
           :children n}          ; nombre d'enfants restants

 :ol-ul   {:inside true/false    ; dans une liste?
           :children (n m ...)}  ; pile des compteurs (listes imbriquées)

 :li      {:inside true/false    ; dans un item de liste?
           :children 1}          ; toujours 1

 :p       {:inside true/false    ; dans un paragraphe?
           :children n}          ; nombre d'enfants restants

 :modifier :em/:strong/:s/nil    ; modificateur de style actif

 :buffer ""                      ; texte accumulé pour le noeud courant

 :puml ()                        ; liste des lignes PlantUML générées

 :ignore-string true/false}      ; ignorer la prochaine string?
```

## Pattern Enter/Exit

Chaque type d'élément suit un pattern enter/exit:

```text
         ┌─────────────────────────────────────────────┐
         │              HEADING                        │
         │  enter: set level, count children          │
         │                                             │
         │    ┌─────────────────────────────────┐     │
         │    │  Enfants (strings, :em, :s...)  │     │
         │    │  → accumulent dans :buffer      │     │
         │    └─────────────────────────────────┘     │
         │                                             │
         │  exit: "** " + buffer → :puml              │
         └─────────────────────────────────────────────┘
```

Le compteur `children` décrémente à chaque string traitée.
Quand il atteint zéro, la fonction exit est déclenchée.

## Génération PlantUML

Le texte PlantUML est construit ligne par ligne dans `:puml` (en ordre inverse).
Le niveau d'indentation est représenté par des astérisques:

```text
Markdown                          PlantUML
─────────────────────────────────────────────────────
# Titre                     →     * Titre
## Sous-titre               →     ** Sous-titre
### Niveau 3                →     *** Niveau 3
- item dans ## avec liste   →     ***_ item
  - sous-item               →     ****_ sous-item
```

Les modificateurs de style sont convertis en balises HTML:

| Markdown     | PlantUML        |
| ------------ | --------------- |
| `*italic*`   | `<i>italic</i>` |
| `**bold**`   | `<b>bold</b>`   |
| `~~strike~~` | `<s>strike</s>` |

## Flux de données détaillé

Exemple avec `# Hello *world*`:

```text
Hiccup: [:markdown/heading {:level 1} "Hello " [:em {} "world"]]

┌─────────────────────────────────────────────────────────────────┐
│ État initial: {}                                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. enter-heading                                               │
│     state: {:heading {:level 1, :inside true, :children 2}}     │
│                                                                 │
│  2. walk-children ["Hello " [:em {} "world"]]                   │
│     │                                                           │
│     ├─ process-string "Hello "                                  │
│     │  state: {..., :buffer "Hello ", :children 1}              │
│     │                                                           │
│     └─ walk-vector [:em {} "world"]                             │
│        │                                                        │
│        ├─ enter-em                                              │
│        │  state: {..., :modifier :em}                           │
│        │                                                        │
│        └─ process-string "world"                                │
│           • apply-modifier → "<i>world</i>"                     │
│           • buffer: "Hello <i>world</i>"                        │
│           • children: 0 → exit-heading!                         │
│                                                                 │
│  3. exit-heading                                                │
│     • text = "* Hello <i>world</i>"                             │
│     • puml = ("* Hello <i>world</i>")                           │
│     • buffer = ""                                               │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│ État final: {:puml ("* Hello <i>world</i>"), ...}               │
└─────────────────────────────────────────────────────────────────┘
```

## Gestion des listes imbriquées

Les listes utilisent une pile pour tracker la profondeur:

```text
Markdown:
## Section
- item1
  - item1.1
  - item1.2
- item2

Hiccup simplifié:
[:heading {:level 2} "Section"]
[:ul
  [:li [:p "item1"]
       [:ul [:li [:p "item1.1"]]
            [:li [:p "item1.2"]]]]
  [:li [:p "item2"]]]

Évolution de :ol-ul :children:
─────────────────────────────────────────
enter ul (2 items)      → (2)
  enter li item1        → (2)
    enter ul (2 items)  → (2 2)      ; push
      process item1.1   → (1 2)
      process item1.2   → (nil 2)    ; pop inner
    exit ul             → (2)
  exit li               → (1)
  enter li item2        → (1)
    process item2       → (nil)      ; pop outer
  exit ul
─────────────────────────────────────────

PlantUML généré:
** Section
***_ item1
****_ item1.1
****_ item1.2
***_ item2
```

Le niveau total d'un item de liste = level du heading parent + profondeur dans la pile.

## Diagramme des namespaces

```text
                    ┌─────────────────┐
                    │    core.clj     │
                    │   (CLI, -main)  │
                    └────────┬────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │  transform.clj  │
                    │  (orchestration)│
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
              ▼              ▼              ▼
      ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
      │  enter.clj  │ │  exit.clj   │ │   log.clj   │
      │  (enter-*)  │ │  (exit-*,   │ │  (timbre    │
      │             │ │  process-*) │ │   config)   │
      └─────────────┘ └─────────────┘ └─────────────┘

Dépendances externes:
  • cybermonday  → parsing Markdown
  • plantuml     → génération images
  • timbre       → logging
  • tools.cli    → arguments CLI
```

## Points d'extension

Pour ajouter le support d'un nouvel élément Markdown:

1. Ajouter un case dans `walk-vector` (transform.clj)
2. Créer `enter-xxx` dans enter.clj si contexte nécessaire
3. Gérer la sortie dans exit.clj si génération PlantUML requise

Exemple pour ajouter le support des liens:

```clojure
;; Dans walk-vector:
:a
(-> state
    (m2menter/enter-link x)
    (walk-children children))

;; Dans enter.clj:
(defn enter-link [state x]
  (let [href (get-in x [1 :href])]
    (assoc state :link-href href)))
```
