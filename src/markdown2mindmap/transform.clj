(ns markdown2mindmap.transform
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :refer [prewalk]]
            [cybermonday.ir]
            [markdown2mindmap.enter :as m2menter]
            [markdown2mindmap.exit :as m2mexit]
            [puget.printer :as puget]
            [taoensso.timbre :as t :refer [info infof]])
  (:import (java.io FileOutputStream)
           (net.sourceforge.plantuml SourceStringReader
                                     FileFormatOption
                                     FileFormat)))

;; TODO cf https://github.com/jimmyhmiller/PlayGround/blob/master/markdown-to-blog/src/markdown_to_blog/core.clj

(defn- walk-fn
  [result x]
  (cond
    (vector? x)
    (let [[elt-name _attributes & children] x]
      (infof "\nvector with %d children=%s" (count children) x)
      (case elt-name
        :div ;;root div
        x

        :markdown/heading
        (m2menter/enter-heading result x)

        :ul
        (m2menter/enter-ol-ul result x)

        :ol
        (m2menter/enter-ol-ul result x)

        :markdown/bullet-list-item
        (m2menter/enter-li result x)

        :markdown/ordered-list-item
        (m2menter/enter-li result x)

        :markdown/soft-line-break
        (m2mexit/through-slb result x)

        :p
        (m2menter/enter-p result x)

        :em
        (m2menter/enter-em result x)

        :s
        (m2menter/enter-s result x)

        :strong
        (m2menter/enter-strong result x)

        ;; else
        (do
          (info "Not yet processed: " x)
          x)))

    (string? x)
    (m2mexit/process-string result x)

    #_#_:else
      (infof "What is it? %s" x)))

(defn- walk-hiccup
  "Walk function to process hiccup data."
  [hiccup-data]
  (let [result (atom {})]
    (prewalk (partial walk-fn result) hiccup-data)
    @result))

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
                    ;;The nil is there because you are getting a static field,
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

#_(defn hiccup->puml-file
    "Generates a puml file from an hiccup file."
    [input-file output-file]
    (->> input-file
         slurp
         edn/read-string
         hiccup->puml
         (spit output-file)))

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
