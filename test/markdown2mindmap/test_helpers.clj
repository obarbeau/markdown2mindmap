(ns markdown2mindmap.test-helpers
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.edn :as edn]
            [markdown2mindmap.log :as m2mlog]
            [markdown2mindmap.transform :as m2mtransform]))

(defn pre-report
  "Kaocha: beep after test execution."
  [event]
  (let [atype (:type event)
        fail  (:fail event)
        error (:error event)
        total (+ (or fail 0) (or error 0))]
    (when-not (System/getenv "GITHUB_ACTIONS")
      (when (= :summary atype)
        (shell/sh "/usr/bin/paplay"
                  (str "./test-resources/"
                       (if (zero? total)
                         "system-ready"
                         "dialog-error")
                       ".ogg")))))
  event)

(defn delete-log []
  (io/delete-file m2mlog/log-file-name true))

(defn delete-output-dir []
  (doseq [ff (.listFiles (io/file "./output"))]
    (io/delete-file ff)))

(defn format-it [what number extension]
  (format "test-resources/%s-%02d.%s" what number extension))

(defn slurp-it [what number extension]
  (slurp (format-it what number extension)))

(defn slurp-hiccup [number]
  (slurp-it "hiccup" number "edn"))

(defn slurp-input [number]
  (slurp-it "input" number "3md"))

(defn slurp-puml [number]
  (slurp-it "map" number "puml"))

(defn md-file->hiccup
  "Markdown file to hiccup file"
  [number]
  (:ok (m2mtransform/md->hiccup (slurp-input number))))

(defn read-hiccup
  "Read edn from hiccup file"
  [n]
  (edn/read-string (slurp-hiccup n)))

(defn hiccup-file->puml [number]
  (->> (read-hiccup number)
       m2mtransform/hiccup->puml
       (m2mtransform/->puml2 "")))