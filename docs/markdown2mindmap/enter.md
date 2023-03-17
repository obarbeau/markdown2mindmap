# markdown2mindmap.enter



??? tip  "(`ns`)"

    ```clojure
    (ns markdown2mindmap.enter
      (:require [cybermonday.ir]
                [markdown2mindmap.log :as m2mlog]
                [taoensso.timbre :as t :refer [info]]))
    ```

## `log-enter`



??? tip  "(`defn-`)"

    ```clojure
    (defn- log-enter
      [result ctype]
      (info (t/color-str :blue (str ">>enter-" (name ctype)))
            "\n " (select-keys @result m2mlog/log-keys)))
    ```

## `now-inside`

We are inside a `ctype` element. Sets the `inside` flag to true
   and the children.



??? tip  "(`defn-`)"

    ```clojure
    (defn- now-inside
      [result ctype children]
      (swap! result assoc-in [ctype :inside] true)
      (if (number? children)
        (swap! result assoc-in [ctype :children] children)
        (swap! result update-in [ctype :children] children))
      (log-enter result ctype))
    ```

------------------------------------

## `enter-heading`

Enters a heading element. Level is given in attributes.

```clojure
(enter-heading result [_elt-name attributes & children :as x])
```

??? tip  "(`defn`)"

    ```clojure
    (defn enter-heading
      [result [_elt-name attributes & children :as x]]
      (swap! result assoc-in [:heading :level] (:level attributes))
      (now-inside result :heading (count children))
      x)
    ```

## `enter-ol-ul`

Enters a list, ordered or not.

```clojure
(enter-ol-ul result [_elt-name _attributes & children :as x])
```

??? tip  "(`defn`)"

    ```clojure
    (defn enter-ol-ul
      [result [_elt-name _attributes & children :as x]]
      (now-inside result :ol-ul #(cons (count children) %))
      x)
    ```

## `enter-li`

Enters a list element. The number of children is forced to one.
   Nesting of ol/ul is not managed by li element.

```clojure
(enter-li result [:as x])
```

??? tip  "(`defn`)"

    ```clojure
    (defn enter-li
      [result [:as x]]
      (now-inside result :li 1)
      x)
    ```

## `enter-p`

Enters a paragraph.

```clojure
(enter-p result [_elt-name _attributes & children :as x])
```

??? tip  "(`defn`)"

    ```clojure
    (defn enter-p
      [result [_elt-name _attributes & children :as x]]
      (now-inside result :p (count children))
      x)
    ```

## `enter-em`

Enters an 'emphasize' modifier.

```clojure
(enter-em result [:as x])
```

??? tip  "(`defn`)"

    ```clojure
    (defn enter-em
      [result [:as x]]
      (swap! result assoc :modifier :em)
      (log-enter result :em)
      x)
    ```

## `enter-s`

Enters a 'strike' modifier.

```clojure
(enter-s result [:as x])
```

??? tip  "(`defn`)"

    ```clojure
    (defn enter-s
      [result [:as x]]
      (swap! result assoc :modifier :s)
      (log-enter result :s)
      x)
    ```

## `enter-strong`

Enters a 'strong' modifier.

```clojure
(enter-strong result [:as x])
```

??? tip  "(`defn`)"

    ```clojure
    (defn enter-strong
      [result [:as x]]
      (swap! result assoc :modifier :strong)
      (log-enter result :strong)
      x)
    ```

