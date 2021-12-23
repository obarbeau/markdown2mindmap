(ns markdown2mindmap.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :refer [prewalk]]
            [cybermonday.ir]
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

(defn- enter-heading
  "Enters a heading element. Level is given in attributes."
  [result [_elt-name attributes & children :as x]]
  (let [level-heading (:level attributes)]
    (swap! result assoc-in [:heading :level] level-heading)
    (swap! result assoc-in [:heading :inside] true)
    (swap! result assoc-in [:heading :children] (count children)))
  (info (t/color-str :blue ">>enter-heading") @result)
  x)

(defn- enter-ol-ul
  "Enters a list, ordered or not."
  [result [_elt-name _attributes & children :as x]]
  (swap! result assoc-in [:ul :inside] true)
  (swap! result update-in [:ul :children] #(cons (count children) %))
  (info (t/color-str :blue ">>enter-ol-ul") @result)
  x)

(defn- enter-li
  "Enters a list element. Cheat on the number of children; force to one."
  [result [:as x]]
  (swap! result assoc-in [:li :inside] true)
  (swap! result assoc-in [:li :children] 1)
  (info (t/color-str :blue ">>enter-li") @result)
  x)

(defn- enter-p
  "Enters a paragraph."
  [result [_elt-name _attributes & children :as x]]
  (swap! result assoc-in [:p :inside] true)
  (swap! result assoc-in [:p :children] (count children))
  (info (t/color-str :blue ">>enter-p") @result)
  x)

(defn- enter-em
  "Enters an 'emphasize' modifier."
  [result [:as x]]
  (swap! result assoc :modifier :em)
  (info (t/color-str :blue ">>enter-em") @result)
  x)

(defn- enter-s
  "Enters a 'strike' modifier."
  [result [:as x]]
  (swap! result assoc :modifier :s)
  (info (t/color-str :blue ">>enter-s") @result)
  x)

(defn- enter-strong
  "Enters a 'strong' modifier."
  [result [:as x]]
  (swap! result assoc :modifier :strong)
  (info (t/color-str :blue ">>enter-strong") @result)
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

(defn- exit-heading
  "Exit a heading element. Conj current buffer to plantuml text."
  [result]
  (swap! result assoc-in [:heading :inside] false)
  (let [text (-> (get-in @result [:heading :level])
                 (repeat "*")
                 (concat " " (:buffer @result))
                 (#(apply str %)))]
    (swap! result update :plantuml conj text)
    (swap! result assoc :buffer ""))
  (info (t/color-str :purple "<<exit-heading") @result))


(defn- last-simple-child?
  "True if it is the last child of a standard element."
  [result ctype dec-children]
  (swap! result assoc-in [ctype :children] dec-children)
  (zero? dec-children))

(defn- last-nested-child?
  "True if it is the last child of a nestable element (like ol/ul).
  Set `inside` flag to false
  only if this was the last child of all nested elements."
  [result ctype children]
  (let [[child1 & rest] children
        child1dec (dec (or child1 1))]
    (if (zero? child1dec)
      (do
        (swap! result assoc-in [ctype :children] rest)
        (when (nil? rest)
          (swap! result assoc-in [ctype :inside] false))
        true)
      (do (swap! result assoc-in [ctype :children] (cons child1dec rest))
          false))))

(defn- last-child?
  "true if it is the last child of the element of type `ctype`"
  [result ctype]
  (let [children (get-in @result [ctype :children])]
    (if (number? children)
      (last-simple-child? result ctype (dec children))
      (last-nested-child? result ctype children))))

(defn- exit-ol-ul
  "Exit a list, ordered or not."
  [result]
  (info (t/color-str :purple "<<exit-ol-ul") @result))

(defn- exit-li
  "Exit a list element.
  Use the level of the list added to the level of containing heading."
  [result]
  (swap! result assoc-in [:li :inside] false)
  (let [level-heading (get-in @result [:heading :level])
        level-ol-ul (count (get-in @result [:ul :children]))
        level-total (+ level-heading level-ol-ul)
        text (-> (repeat level-total  "*")
                 (concat  "_ " (:buffer @result))
                 (#(apply str %)))]
    (swap! result update :plantuml conj text)
    (swap! result assoc :buffer "")
    (info (t/color-str :purple "<<exit-li") @result)
    (when (and (get-in @result [:ul :inside])
               (last-child? result :ul))
      (exit-ol-ul result))))

(defn- exit-p
  "Exit a paragraph. The buffer is deleted if it has not been used."
  [result]
  (swap! result assoc-in [:p :inside] false)
  (info (t/color-str :purple "<<exit-p") @result)

  (when (and (get-in @result [:li :inside])
             (last-child? result :li))
    (exit-li result))
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
      (info (t/color-str :blue ">>process-string") s @result)))

  (swap! result assoc :ignore-string false)

  (when (and (get-in @result [:heading :inside])
             (last-child? result :heading))
    (exit-heading result))

  (when (and (get-in @result [:p :inside])
             (last-child? result :p))
    (exit-p result))
  s)

(defn- through-slb
  "Pass through a soft line break. Set the `ignore-string` flag to true.
  Then checks if this element was the last child of a paragraph
  and exit if required."
  [result x]
  (when (get-in @result [:ul :inside])
    (swap! result assoc :ignore-string true))
  (when (and (get-in @result [:p :inside])
             (last-child? result :p))
    (exit-p result))
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

(defn hiccup->map
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

(defn md->png
  "Generates an image from a markdown file."
  [input-file output-file]
  (->> (slurp input-file)
       md->hiccup
       hiccup->map
       (create-image! output-file)))

(defn -main [input-file output-file]
  (md->png input-file output-file)
  (System/exit 0))