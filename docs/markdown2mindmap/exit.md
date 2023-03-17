# markdown2mindmap.exit



??? tip  "(`ns`)"

    ```clojure
    (ns markdown2mindmap.exit
      (:require [markdown2mindmap.log :as m2mlog]
                [taoensso.timbre :as t :refer [info]]))
    ```

## `last-simple-child?`

Returns a vector with 2 values:
   1. True if it is the last child of a non-nestable element.
   2. new number of children for this non-nestable element or zero.



??? tip  "(`defn-`)"

    ```clojure
    (defn- last-simple-child?
      [children]
      (let [new-children (dec children)]
        [(zero? new-children) new-children]))
    ```

## `last-nested-child?`

Returns a vector with 2 values:
   1. is it the last child of a nestable element (like ol/ul)?
   2. rest of children or nil.



??? tip  "(`defn-`)"

    ```clojure
    (defn- last-nested-child?
      [children]
      (let [[child1 & rest] children
            child1dec (dec (or child1 1))]
        [(zero? child1dec) (if (zero? child1dec)
                             rest
                             (cons child1dec rest))]))
    ```

## `last-child?`

Returns a vector with 2 values:
   1. is it the last child of the element of type `ctype`.
   2. the new children number/list or nil.



??? tip  "(`defn-`)"

    ```clojure
    (defn- last-child?
      [result ctype]
      (let [children (get-in @result [ctype :children])
            [last? new-children] (if (number? children)
                                   (last-simple-child? children)
                                   (last-nested-child? children))]
        [last? new-children]))
    ```

## `now-outside?`

Returns true if we are now completely outside of this (nested) element?



??? tip  "(`defn-`)"

    ```clojure
    (defn- now-outside?
      [result ctype]
      (let [[_last? new-children] (last-child? result ctype)]
        (or (nil? new-children)
            (and (number? new-children) (zero? new-children)))))
    ```

## `inside-and-last?`

If we are we inside a `ctype` element, returns `last-child?` answer,
  otherwise nil.



??? tip  "(`defn-`)"

    ```clojure
    (defn- inside-and-last?
      [result ctype]
      (when (get-in @result [ctype :inside])
        (last-child? result ctype)))
    ```

------------------------------------

## `exit`

Set `inside` flag to false
   only if this was the last child of all (nested) elements.



??? tip  "(`defn-`)"

    ```clojure
    (defn- exit
      [result ctype now-outside?]
      (when now-outside?
        (swap! result assoc-in [ctype :inside] false))
      (info (t/color-str :purple (str "<<exit-" (name ctype)))
            (select-keys @result m2mlog/log-keys)))
    ```

## `exit-if-required`

If we are we inside a `ctype` element, sets the new number of children.
  If it wad the last child, execute the `exit-fn`.



??? tip  "(`defn-`)"

    ```clojure
    (defn- exit-if-required
      [result ctype exit-fn]
      (when-let [[last? new-children] (inside-and-last? result ctype)]
        (swap! result assoc-in [ctype :children] new-children)
        (when last?
          (exit-fn result))))
    ```

## `exit-heading`

Exit a heading element. Conj current buffer to puml text.



??? tip  "(`defn-`)"

    ```clojure
    (defn- exit-heading
      [result]
      (let [text (-> (get-in @result [:heading :level])
                     (repeat "*")
                     (concat " " (:buffer @result))
                     (#(apply str %)))]
        (swap! result update :puml conj text)
        (swap! result assoc :buffer ""))
      (exit result :heading true))
    ```

## `exit-ol-ul`

Exit a list, ordered or not.



??? tip  "(`defn-`)"

    ```clojure
    (defn- exit-ol-ul
      [result]
      (exit result :ol-ul (now-outside? result :ol-ul)))
    ```

## `exit-li`

Exit a list element.
  Use the level of the list added to the level of containing heading.



??? tip  "(`defn-`)"

    ```clojure
    (defn- exit-li
      [result]
      (let [level-heading (get-in @result [:heading :level])
            level-ol-ul (count (get-in @result [:ol-ul :children]))
            level-total (+ level-heading level-ol-ul)
            text (-> (repeat level-total  "*")
                     (concat  "_ " (:buffer @result))
                     (#(apply str %)))]
        (swap! result update :puml conj text)
        (swap! result assoc :buffer "")
        (exit result :li true)
        (exit-if-required result :ol-ul exit-ol-ul)))
    ```

## `exit-p`

Exit a paragraph. The buffer is deleted if it has not been used.



??? tip  "(`defn-`)"

    ```clojure
    (defn- exit-p
      [result]
      (exit result :p true)
      (exit-if-required result :li exit-li)
      (swap! result assoc :buffer ""))
    ```

## `apply-modifier`

Applies the current modifier to the string.



??? tip  "(`defn-`)"

    ```clojure
    (defn- apply-modifier
      [result s]
      (let [modifier (:modifier @result)]
        (swap! result assoc :modifier nil)
        (case modifier
          :em (format "<i>%s</i>" s)
          :strong (format "<b>%s</b>" s)
          :s (format "<s>%s</s>" s)
          s)))
    ```

------------------------------------

## `process-string`

If the `ignore-string` flag is not true,
  adds this string to the buffer, applying eventual modifier.
  Then checks if this string was the last child of a heading or paragraph
  and exit them if required.

```clojure
(process-string result s)
```

??? tip  "(`defn`)"

    ```clojure
    (defn process-string
      [result s]
      (if (:ignore-string @result)
        (info "ignore string: " s)
        (do
          (swap! result update-in [:buffer] str (apply-modifier result s))
          (info (t/color-str :green ">>process-string") s)))
      (swap! result assoc :ignore-string false)
      (exit-if-required result :heading exit-heading)
      (exit-if-required result :p exit-p)
      (info (t/color-str :green "<<process-string") "\n "
            (select-keys @result m2mlog/log-keys))
      s)
    ```

## `through-slb`

Pass through a soft line break. Set the `ignore-string` flag to true.
  Then checks if this element was the last child of a paragraph
  and exit if required.

```clojure
(through-slb result x)
```

??? tip  "(`defn`)"

    ```clojure
    (defn through-slb
      [result x]
      (when (get-in @result [:ol-ul :inside])
        (swap! result assoc :ignore-string true))
      (exit-if-required result :p exit-p)
      x)
    ```

