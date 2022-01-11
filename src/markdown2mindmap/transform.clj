(ns markdown2mindmap.transform
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :refer [prewalk]]
            [cybermonday.ir]
            [clojure.edn :as edn]
            [markdown2mindmap.enter :as m2menter]
            [markdown2mindmap.exit :as m2mexit]
            [puget.printer :as puget]
            [taoensso.timbre :as t :refer [info infof]])
  (:import (java.io FileOutputStream)
           (net.sourceforge.plantuml SourceStringReader
                                     FileFormatOption
                                     FileFormat)))

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

(defn- ->plantuml2
  "Wraps plantuml text to plantuml syntax."
  [plantuml]
  (clojure.string/join
   "\n"
   (list
    "@startmindmap"
    plantuml
    "@endmindmap")))

(defn- create-image!
  "Generates an image from plantuml text."
  [output-file type plantuml-text]
  (let [uml (->plantuml2 plantuml-text)
        out (FileOutputStream. (io/file output-file))
        format (->> type
                    str/upper-case
                    (.getField FileFormat)
                    ;;The nil is there because you are getting a static field,
                    ;; rather than a member field of a particular object.
                    (#(.get % nil))
                    FileFormatOption.)]
    (-> (SourceStringReader. uml)
        (.generateImage out format))
    (.close out)))

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
  "Convert hiccup data to plantuml text."
  [hiccup-data]
  (->> hiccup-data
       walk-hiccup
       :plantuml
       reverse
       (str/join "\n")))

(defn hiccup->puml-file
  "Generates a plantuml file from an hiccup file."
  [input-file output-file]
  (->> input-file
       slurp
       edn/read-string
       hiccup->puml
       (spit output-file)))

(defn md->mindmap
  "Generates an mindmap image (with the `type` format) from a markdown file."
  [input-file output-directory type]
  (let [output-file (-> input-file
                        (str/replace #"(?i)\.md" "")
                        (str/replace #".*/" "")
                        (str "." type)
                        (#(io/file output-directory %)))]
    (->> input-file
         slurp
         md->hiccup
         hiccup->puml
         (create-image! output-file type))))