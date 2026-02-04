(ns markdown2mindmap.transform
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [cybermonday.ir]
            [markdown2mindmap.enter :as m2menter]
            [markdown2mindmap.exit :as m2mexit]
            [puget.printer :as puget]
            [taoensso.timbre :refer [info infof]])
  (:import (java.io FileOutputStream)
           (net.sourceforge.plantuml SourceStringReader
                                     FileFormatOption
                                     FileFormat)))

(declare walk-node)

(defn- walk-children
  "Walk all children of a node, threading state through each.
   Returns final state after processing all children."
  [state children]
  (reduce walk-node state children))

(defn- walk-vector
  "Process a vector (hiccup element), calling appropriate enter function
   then walking children. Returns new state."
  [state x]
  (let [[elt-name maybe-attrs & rest-elts] x
        ;; Some elements have attrs map, some don't
        has-attrs? (map? maybe-attrs)
        children (if has-attrs? rest-elts (cons maybe-attrs rest-elts))]
    (infof "\nvector with %d children=%s" (count children) x)
    (case elt-name
      :div ;; root div
      (walk-children state children)

      :markdown/heading
      (-> state
          (m2menter/enter-heading x)
          (walk-children children))

      :ul
      (-> state
          (m2menter/enter-ol-ul x)
          (walk-children children))

      :ol
      (-> state
          (m2menter/enter-ol-ul x)
          (walk-children children))

      :markdown/bullet-list-item
      (-> state
          m2menter/enter-li
          (walk-children children))

      :markdown/ordered-list-item
      (-> state
          m2menter/enter-li
          (walk-children children))

      :markdown/soft-line-break
      (m2mexit/through-slb state)

      :p
      (-> state
          (m2menter/enter-p x)
          (walk-children children))

      :em
      (-> state
          m2menter/enter-em
          ;; Don't wrap in walk-children - process inline modifier's children
          ;; as if they were children of the parent element
          (walk-children children))

      :s
      (-> state
          m2menter/enter-s
          (walk-children children))

      :strong
      (-> state
          m2menter/enter-strong
          (walk-children children))

      ;; else - unhandled element type
      (do
        (info "Not yet processed: " x)
        (walk-children state children)))))

(defn- walk-node
  "Walk a single node in the hiccup tree. Returns new state."
  [state x]
  (cond
    (vector? x) (walk-vector state x)
    (string? x) (m2mexit/process-string state x)
    :else state))

(defn- walk-hiccup
  "Walk function to process hiccup data. Returns final state."
  [hiccup-data]
  (walk-node {} hiccup-data))

(defn ->puml2
  "Wraps puml text to puml syntax."
  [styles puml]
  (str/join
   \newline
   (list
    "@startmindmap"
    styles
    puml
    "@endmindmap")))

(defn- create-image!
  "Generates an image from puml text."
  [output-file type puml2]
  (let [out (FileOutputStream. (io/file output-file))
        format (->> type
                    str/upper-case
                    (.getField FileFormat)
                    ;; The nil is there because you are getting a static field,
                    ;; rather than a member field of a particular object.
                    (#(.get ^java.lang.reflect.Field % nil))
                    FileFormatOption.)]
    (-> (SourceStringReader. puml2)
        (.outputImage out format))
    (.close out)
    (printf "generated %s\n" output-file)))

;; ------------------------------------

(defn md->hiccup
  "Converts markdown data to hiccup AST with cybermonday."
  [data]
  (cybermonday.ir/md-to-ir data))

(defn md->hiccup-file
  "Generates a hiccup (edn) file from a markdown file."
  [input-file output-file]
  (->> input-file
       slurp
       md->hiccup
       puget/pprint-str
       (spit output-file)))

(defn hiccup->puml
  "Convert hiccup data to puml text."
  [hiccup-data]
  (->> hiccup-data
       walk-hiccup
       :puml
       reverse
       (str/join "\n")))

(defn md->mindmap
  "Generates an mindmap image (with the `type` format) from a markdown file or dir and/or a puml file."
  [input-file-or-dir {:keys [type style with-svg svg-output-dir with-puml puml-output-dir]}]
  (doseq [input-file (file-seq (io/file input-file-or-dir))
          :when (str/ends-with? input-file ".3md")
          :let [svg-output-directory (or svg-output-dir (.getParent input-file))
                puml-output-directory (or puml-output-dir (.getParent input-file))]]
    (let [;; keeps only filename without '3md' extension
          output-name (str/replace (.getName input-file) #"(?i)\.3md" "")
          output-img (-> output-name
                         ;; adds selected extension
                         (str "." type)
                         (#(io/file svg-output-directory %)))
          output-puml (-> output-name
                          (str ".puml")
                          (#(io/file puml-output-directory %)))
          styles (when style (slurp style))
          puml (->> input-file
                    slurp
                    md->hiccup
                    hiccup->puml
                    (->puml2 styles))
          previous-content (and (.exists (io/as-file output-puml))
                                (slurp output-puml))]
      (io/make-parents output-img)
      (io/make-parents output-puml)
      (when (or with-puml puml-output-dir)
        (if (= puml previous-content)
          (printf "unchanged file %s\n" output-puml)
          (do
            (spit output-puml puml)
            (printf "generated %s\n" output-puml))))
      (when (or with-svg svg-output-dir)
        (create-image! output-img type puml)))))

(defn list-all-fonts
  "Creates an SVG image listing all fonts available on the system."
  [output-file]
  (create-image! output-file "svg" (str/join
                                    \newline ["@startuml"
                                              "listfonts"
                                              "@enduml"])))
