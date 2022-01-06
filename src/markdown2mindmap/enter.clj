(ns markdown2mindmap.enter
  (:require [cybermonday.ir]
            [markdown2mindmap.log :as m2mlog]
            [taoensso.timbre :as t :refer [info]]))

(defn- log-enter
  [result ctype]
  (info (t/color-str :blue (str ">>enter-" (name ctype)))
        "\n " (select-keys @result m2mlog/log-keys)))

(defn- now-inside
  "We are inside a `ctype` element. Sets the `inside` flag to true
   and the children."
  [result ctype children]
  (swap! result assoc-in [ctype :inside] true)
  (if (number? children)
    (swap! result assoc-in [ctype :children] children)
    (swap! result update-in [ctype :children] children))
  (log-enter result ctype))

;; ------------------------------------

(defn enter-heading
  "Enters a heading element. Level is given in attributes."
  [result [_elt-name attributes & children :as x]]
  (swap! result assoc-in [:heading :level] (:level attributes))
  (now-inside result :heading (count children))
  x)

(defn enter-ol-ul
  "Enters a list, ordered or not."
  [result [_elt-name _attributes & children :as x]]
  (now-inside result :ol-ul #(cons (count children) %))
  x)

(defn enter-li
  "Enters a list element. The number of children is forced to one.
   Nesting of ol/ul is not managed by li element."
  [result [:as x]]
  (now-inside result :li 1)
  x)

(defn enter-p
  "Enters a paragraph."
  [result [_elt-name _attributes & children :as x]]
  (now-inside result :p (count children))
  x)

(defn enter-em
  "Enters an 'emphasize' modifier."
  [result [:as x]]
  (swap! result assoc :modifier :em)
  (log-enter result :em)
  x)

(defn enter-s
  "Enters a 'strike' modifier."
  [result [:as x]]
  (swap! result assoc :modifier :s)
  (log-enter result :s)
  x)

(defn enter-strong
  "Enters a 'strong' modifier."
  [result [:as x]]
  (swap! result assoc :modifier :strong)
  (log-enter result :strong)
  x)

