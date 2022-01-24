
(ns markdown2mindmap.exit
  (:require [markdown2mindmap.log :as m2mlog]
            [taoensso.timbre :as t :refer [info]]))

(defn- last-simple-child?
  "Returns a vector with 2 values:
   1. True if it is the last child of a non-nestable element.
   2. new number of children for this non-nestable element or zero."
  [children]
  (let [new-children (dec children)]
    [(zero? new-children) new-children]))

(defn- last-nested-child?
  "Returns a vector with 2 values:
   1. is it the last child of a nestable element (like ol/ul)?
   2. rest of children or nil."
  [children]
  (let [[child1 & rest] children
        child1dec (dec (or child1 1))]
    [(zero? child1dec) (if (zero? child1dec)
                         rest
                         (cons child1dec rest))]))

(defn- last-child?
  "Returns a vector with 2 values:
   1. is it the last child of the element of type `ctype`.
   2. the new children number/list or nil."
  [result ctype]
  (let [children (get-in @result [ctype :children])
        [last? new-children] (if (number? children)
                               (last-simple-child? children)
                               (last-nested-child? children))]
    [last? new-children]))

(defn- now-outside?
  "Returns true if we are now completely outside of this (nested) element?"
  [result ctype]
  (let [[_last? new-children] (last-child? result ctype)]
    (or (nil? new-children)
        (and (number? new-children) (zero? new-children)))))

(defn- inside-and-last?
  "If we are we inside a `ctype` element, returns `last-child?` answer,
  otherwise nil."
  [result ctype]
  (when (get-in @result [ctype :inside])
    (last-child? result ctype)))

;; ------------------------------------

(defn- exit
  "Set `inside` flag to false
   only if this was the last child of all (nested) elements."
  [result ctype now-outside?]
  (when now-outside?
    (swap! result assoc-in [ctype :inside] false))
  (info (t/color-str :purple (str "<<exit-" (name ctype)))
        (select-keys @result m2mlog/log-keys)))

(defn- exit-if-required
  "If we are we inside a `ctype` element, sets the new number of children.
  If it wad the last child, execute the `exit-fn`."
  [result ctype exit-fn]
  (when-let [[last? new-children] (inside-and-last? result ctype)]
    (swap! result assoc-in [ctype :children] new-children)
    (when last?
      (exit-fn result))))

(defn- exit-heading
  "Exit a heading element. Conj current buffer to puml text."
  [result]
  (let [text (-> (get-in @result [:heading :level])
                 (repeat "*")
                 (concat " " (:buffer @result))
                 (#(apply str %)))]
    (swap! result update :puml conj text)
    (swap! result assoc :buffer ""))
  (exit result :heading true))

(defn- exit-ol-ul
  "Exit a list, ordered or not."
  [result]
  (exit result :ol-ul (now-outside? result :ol-ul)))

(defn- exit-li
  "Exit a list element.
  Use the level of the list added to the level of containing heading."
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

(defn- exit-p
  "Exit a paragraph. The buffer is deleted if it has not been used."
  [result]
  (exit result :p true)

  (exit-if-required result :li exit-li)

  (swap! result assoc :buffer ""))

(defn- apply-modifier
  "Applies the current modifier to the string."
  [result s]
  (let [modifier (:modifier @result)]
    (swap! result assoc :modifier nil)
    (case modifier
      :em (format "<i>%s</i>" s)
      :strong (format "<b>%s</b>" s)
      :s (format "<s>%s</s>" s)
      s)))

;; ------------------------------------

(defn process-string
  "If the `ignore-string` flag is not true,
  adds this string to the buffer, applying eventual modifier.
  Then checks if this string was the last child of a heading or paragraph
  and exit them if required."
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

(defn through-slb
  "Pass through a soft line break. Set the `ignore-string` flag to true.
  Then checks if this element was the last child of a paragraph
  and exit if required."
  [result x]
  (when (get-in @result [:ol-ul :inside])
    (swap! result assoc :ignore-string true))
  (exit-if-required result :p exit-p)
  x)