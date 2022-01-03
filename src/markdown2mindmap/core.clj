(ns markdown2mindmap.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :refer [prewalk]]
            [cybermonday.ir]
            [clojure.edn :as edn]
            [puget.printer :as puget]
            [taoensso.encore :as enc]
            [taoensso.timbre :as t :refer [info infof]]
            [taoensso.timbre.appenders.core :as appenders])
  (:import (java.io FileOutputStream)
           (net.sourceforge.plantuml SourceStringReader))
  (:gen-class))

(def log-file-name "./output/markdown2mindmap.log")

(t/merge-config!
 {:timestamp-opts  {:pattern ""}
  :output-fn
  (fn  ([data]
        (let [{:keys [level ?err #_vargs msg_ ?ns-str ?file hostname_
                      timestamp_ ?line]} data]
          (str
           ;; nothing else than message!
           ;;(when-let [ts (force timestamp_)] (str ts " "))
           ;;(force hostname_)  " "
           ;;(str/upper-case (name level))  " "
           ;;"[" (or ?ns-str ?file "?") ":" (or ?line "?") "] - "
           (force msg_)
           (when-not false
             (when-let [err ?err]
               (str enc/system-newline (t/stacktrace err))))))))
  :appenders {:spit (appenders/spit-appender {:fname log-file-name})
              :println {:enabled? false}}})

;;(info t/*config*)

;; ------------------------------------

(defn- enter
  [result ctype]
  (info (t/color-str :blue (str ">>enter-" (name ctype))) (dissoc @result :plantuml)))

(defn- now-inside
  "We are inside a `ctype` element. Sets the `inside` flag to true
   and the children."
  [result ctype children]
  (swap! result assoc-in [ctype :inside] true)
  (if (number? children)
    (swap! result assoc-in [ctype :children] children)
    (swap! result update-in [ctype :children] children))
  (enter result ctype))

(defn- enter-heading
  "Enters a heading element. Level is given in attributes."
  [result [_elt-name attributes & children :as x]]
  (swap! result assoc-in [:heading :level] (:level attributes))
  (now-inside result :heading (count children))
  x)

(defn- enter-ol-ul
  "Enters a list, ordered or not."
  [result [_elt-name _attributes & children :as x]]
  (now-inside result :ol-ul #(cons (count children) %))
  x)

(defn- enter-li
  "Enters a list element. Cheat on the number of children; force to one."
  ;; @todo why cheat????
  [result [:as x]]
  (now-inside result :li 1)
  x)

(defn- enter-p
  "Enters a paragraph."
  [result [_elt-name _attributes & children :as x]]
  (now-inside result :p (count children))
  x)

(defn- enter-em
  "Enters an 'emphasize' modifier."
  [result [:as x]]
  (swap! result assoc :modifier :em)
  (enter result :em)
  x)

(defn- enter-s
  "Enters a 'strike' modifier."
  [result [:as x]]
  (swap! result assoc :modifier :s)
  (enter result :s)
  x)

(defn- enter-strong
  "Enters a 'strong' modifier."
  [result [:as x]]
  (swap! result assoc :modifier :strong)
  (enter result :strong)
  x)

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
  (info (t/color-str :purple (str "<<exit-" (name ctype))) (dissoc @result :plantuml)))

(defn- exit-if-required
  "If we are we inside a `ctype` element, sets the new number of children.
  If it wad the last child, execute the `exit-fn`."
  [result ctype exit-fn]
  (let [[last? new-children] (inside-and-last? result ctype)]
    (swap! result assoc-in [ctype :children] new-children)
    (when last?
      (exit-fn result))))

(defn- exit-heading
  "Exit a heading element. Conj current buffer to plantuml text."
  [result]
  (let [text (-> (get-in @result [:heading :level])
                 (repeat "*")
                 (concat " " (:buffer @result))
                 (#(apply str %)))]
    (swap! result update :plantuml conj text)
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
    (swap! result update :plantuml conj text)
    (swap! result assoc :buffer "")
    (exit result :li true)
    (exit-if-required result :ol-ul exit-ol-ul)))

(defn- exit-p
  "Exit a paragraph. The buffer is deleted if it has not been used."
  [result]
  (exit result :p true)

  (exit-if-required result :li exit-li)

  (swap! result assoc :buffer ""))

(defn- process-string
  "If the `ignore-string` flag is not true,
  adds this string to the buffer, applying eventual modifier.
  Then checks if this string was the last child of a heading or paragraph
  and exit them if required."
  [result s]
  (if (:ignore-string @result)
    (info "ignore string: " s)
    (do
      (swap! result update-in [:buffer] str (apply-modifier result s))
      (info (t/color-str :blue ">>process-string") s (dissoc @result :plantuml))))

  (swap! result assoc :ignore-string false)

  (exit-if-required result :heading exit-heading)

  (exit-if-required result :p exit-p)
  s)

(defn- through-slb
  "Pass through a soft line break. Set the `ignore-string` flag to true.
  Then checks if this element was the last child of a paragraph
  and exit if required."
  [result x]
  (when (get-in @result [:ol-ul :inside])
    (swap! result assoc :ignore-string true))
  (exit-if-required result :p exit-p)
  x)

;; ------------------------------------

(defn walk-fn
  [result x]
  (cond
    (vector? x)
    (let [[elt-name _attributes & children] x]
      (infof "\nvector with %d children=%s" (count children) x)
      (case elt-name
        :div ;;root div
        x

        :markdown/heading
        (enter-heading result x)

        :ul
        (enter-ol-ul result x)

        :ol
        (enter-ol-ul result x)

        :markdown/bullet-list-item
        (enter-li result x)

        :markdown/ordered-list-item
        (enter-li result x)

        :markdown/soft-line-break
        (through-slb result x)

        :p
        (enter-p result x)

        :em
        (enter-em result x)

        :s
        (enter-s result x)

        :strong
        (enter-strong result x)

        ;; else
        (do
          (info "Not yet processed: " x)
          x)))

    (string? x)
    (process-string result x)

    #_#_:else
      (infof "What is it? %s" x)))

(defn md->hiccup
  "Converts markdown data to hiccup AST with cybermonday."
  [data]
  (cybermonday.ir/md-to-ir data))

(defn walk-hiccup
  "Walk function to process hiccup data."
  [hiccup-data]
  (let [result (atom {})]
    (prewalk (partial walk-fn result) hiccup-data)
    @result))

(defn hiccup->puml
  "Convert hiccup data to plantuml text."
  [hiccup-data]
  (->> hiccup-data
       walk-hiccup
       :plantuml
       reverse
       (str/join "\n")))

(defn ->plantuml2
  "Wraps plantuml text to plantuml syntax."
  [plantuml]
  (clojure.string/join
   "\n"
   (list
    "@startmindmap"
    plantuml
    "@endmindmap")))

(defn create-image!
  "Generates an image from plantuml text."
  [output-file plantuml-text]
  (let [uml (->plantuml2 plantuml-text)
        out (FileOutputStream. (io/file output-file))]
    (-> (SourceStringReader. uml)
        (.generateImage out))
    (.close out)))

(defn md->hiccup-file
  "Generates a hiccup (edn) file from a markdown file."
  [input-file output-file]
  (->> input-file
       slurp
       md->hiccup
       puget/pprint-str
       (spit output-file)))

(defn hiccup->puml-file
  "Generates a plantuml file from an hiccup file."
  [input-file output-file]
  (->> input-file
       slurp
       edn/read-string
       hiccup->puml
       (spit output-file)))

(defn md->png
  "Generates an image from a markdown file."
  [input-file output-file]
  (->> input-file
       slurp
       md->hiccup
       hiccup->puml
       (create-image! output-file)))

(defn -main [input-file output-file]
  (md->png input-file output-file)
  (System/exit 0))