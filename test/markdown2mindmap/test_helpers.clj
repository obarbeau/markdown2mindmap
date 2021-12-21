(ns markdown2mindmap.test-helpers
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.edn :as edn]
            [markdown2mindmap.core :as m2mcore]))

(defn pre-report
  "Kaocha: beep after test execution."
  [event]
  (let [atype (:type event)
        fail  (:fail event)
        error (:error event)
        total (+ (or fail 0) (or error 0))]
    (when (= :summary atype)
      (shell/sh "/usr/bin/paplay"
                (str "/test-resources/"
                     (if (zero? total)
                       "system-ready"
                       "dialog-error")
                     ".ogg"))))
  event)

(defn delete-log []
  (io/delete-file m2mcore/log-file-name true))

(defn format-it [what number extension]
  (format "test-resources/%s-%02d.%s" what number extension))

(defn slurp-it [what number extension]
  (slurp (format-it what number extension)))

(defn slurp-hiccup [number]
  (slurp-it "hiccup" number "edn"))

(defn slurp-input [number]
  (slurp-it "input" number "md"))

(defn slurp-plantuml [number]
  (slurp-it "map" number "plantuml"))

(defn md-file->hiccup
  "Markdown file to hiccup file"
  [number]
  (m2mcore/md->hiccup (slurp-input number)))

(defn read-hiccup
  "Read edn from hiccup file"
  [n]
  (edn/read-string (slurp-hiccup n)))

(defn output-img [number]
  (format "output/output-%02d.png" number))

(defn hiccup-file->map [number]
  (->> (read-hiccup number)
       m2mcore/hiccup->map))

;; 