(ns markdown2mindmap.enter
  (:require [markdown2mindmap.log :as m2mlog]
            [taoensso.timbre :as t :refer [info]]))

(defn- log-enter
  [state ctype]
  (info (t/color-str :blue (str ">>enter-" (name ctype)))
        "\n " (select-keys state m2mlog/log-keys))
  state)

(defn- now-inside
  "We are inside a `ctype` element. Sets the `inside` flag to true
   and the children. Returns new state."
  [state ctype children]
  (-> state
      (assoc-in [ctype :inside] true)
      (as-> s
            (if (number? children)
              (assoc-in s [ctype :children] children)
              (update-in s [ctype :children] children)))
      (log-enter ctype)))

(defn- extract-children
  "Extract children from hiccup element, handling both forms:
   [:tag {:attrs} children...] and [:tag children...]"
  [x]
  (let [[_elt-name maybe-attrs & rest-elts] x]
    (if (map? maybe-attrs)
      rest-elts
      (cons maybe-attrs rest-elts))))

;; ------------------------------------

(defn enter-heading
  "Enters a heading element. Level is given in attributes.
   Returns new state."
  [state x]
  (let [[_elt-name attributes] x
        children (extract-children x)]
    (-> state
        (assoc-in [:heading :level] (:level attributes))
        (now-inside :heading (count children)))))

(defn enter-ol-ul
  "Enters a list, ordered or not. Returns new state."
  [state x]
  (let [children (extract-children x)]
    (now-inside state :ol-ul #(cons (count children) %))))

(defn enter-p
  "Enters a paragraph. Returns new state."
  [state x]
  (let [children (extract-children x)]
    (now-inside state :p (count children))))

(defn enter-li
  "Enters a list element. The number of children is forced to one.
   Nesting of ol/ul is not managed by li element. Returns new state."
  [state]
  (now-inside state :li 1))

(defn enter-em
  "Enters an 'emphasize' modifier. Returns new state."
  [state]
  (-> state
      (assoc :modifier :em)
      (log-enter :em)))

(defn enter-s
  "Enters a 'strike' modifier. Returns new state."
  [state]
  (-> state
      (assoc :modifier :s)
      (log-enter :s)))

(defn enter-strong
  "Enters a 'strong' modifier. Returns new state."
  [state]
  (-> state
      (assoc :modifier :strong)
      (log-enter :strong)))
