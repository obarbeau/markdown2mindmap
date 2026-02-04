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
  [state ctype]
  (let [children (get-in state [ctype :children])
        [last? new-children] (if (number? children)
                               (last-simple-child? children)
                               (last-nested-child? children))]
    [last? new-children]))

(defn- now-outside?
  "Returns true if we are now completely outside of this (nested) element?"
  [state ctype]
  (let [[_last? new-children] (last-child? state ctype)]
    (or (nil? new-children)
        (and (number? new-children) (zero? new-children)))))

(defn- inside-and-last?
  "If we are we inside a `ctype` element, returns `last-child?` answer,
  otherwise nil."
  [state ctype]
  (when (get-in state [ctype :inside])
    (last-child? state ctype)))

;; ------------------------------------

(defn- exit
  "Set `inside` flag to false only if this was the last child of all
   (nested) elements. Returns new state."
  [state ctype now-outside?]
  (let [new-state (if now-outside?
                    (assoc-in state [ctype :inside] false)
                    state)]
    (info (t/color-str :purple (str "<<exit-" (name ctype)))
          (select-keys new-state m2mlog/log-keys))
    new-state))

(declare exit-ol-ul exit-li exit-p)

(defn- exit-if-required
  "If we are inside a `ctype` element, sets the new number of children.
   If it was the last child, execute the `exit-fn`.
   Returns new state."
  [state ctype exit-fn]
  (if-let [[last? new-children] (inside-and-last? state ctype)]
    (let [state' (assoc-in state [ctype :children] new-children)]
      (if last?
        (exit-fn state')
        state'))
    state))

(defn- exit-heading
  "Exit a heading element. Conj current buffer to puml text.
   Returns new state."
  [state]
  (let [text (-> (get-in state [:heading :level])
                 (repeat "*")
                 (concat " " (:buffer state))
                 (#(apply str %)))]
    (-> state
        (update :puml conj text)
        (assoc :buffer "")
        (exit :heading true))))

(defn- exit-ol-ul
  "Exit a list, ordered or not. Returns new state."
  [state]
  (exit state :ol-ul (now-outside? state :ol-ul)))

(defn- exit-li
  "Exit a list element.
   Use the level of the list added to the level of containing heading.
   Returns new state."
  [state]
  (let [level-heading (get-in state [:heading :level])
        level-ol-ul (count (get-in state [:ol-ul :children]))
        level-total (+ level-heading level-ol-ul)
        text (-> (repeat level-total "*")
                 (concat "_ " (:buffer state))
                 (#(apply str %)))]
    (-> state
        (update :puml conj text)
        (assoc :buffer "")
        (exit :li true)
        (exit-if-required :ol-ul exit-ol-ul))))

(defn- exit-p
  "Exit a paragraph. The buffer is deleted if it has not been used.
   Returns new state."
  [state]
  (-> state
      (exit :p true)
      (exit-if-required :li exit-li)
      (assoc :buffer "")))

(defn- apply-modifier
  "Applies the current modifier to the string.
   Returns [new-state modified-string]."
  [state s]
  (let [modifier (:modifier state)
        new-state (assoc state :modifier nil)
        modified-s (case modifier
                     :em (format "<i>%s</i>" s)
                     :strong (format "<b>%s</b>" s)
                     :s (format "<s>%s</s>" s)
                     s)]
    [new-state modified-s]))



;; ------------------------------------

(defn process-string
  "If the `ignore-string` flag is not true,
   adds this string to the buffer, applying eventual modifier.
   Then checks if this string was the last child of a heading or paragraph
   and exit them if required. Returns new state."
  [state s]
  (let [state' (if (:ignore-string state)
                 (do (info "ignore string: " s)
                     state)
                 (let [[state-after-mod modified-s] (apply-modifier state s)]
                   (info (t/color-str :green ">>process-string") s)
                   (update state-after-mod :buffer str modified-s)))
        state'' (-> state'
                    (assoc :ignore-string false)
                    (exit-if-required :heading exit-heading)
                    (exit-if-required :p exit-p))]
    (info (t/color-str :green "<<process-string") "\n "
          (select-keys state'' m2mlog/log-keys))
    state''))

(defn through-slb
  "Pass through a soft line break. Set the `ignore-string` flag to true.
   Then checks if this element was the last child of a paragraph
   and exit if required. Returns new state."
  [state]
  (-> state
      (as-> s (if (get-in s [:ol-ul :inside])
                (assoc s :ignore-string true)
                s))
      (exit-if-required :p exit-p)))
