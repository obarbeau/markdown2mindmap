(ns markdown2mindmap.core
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [markdown2mindmap.transform :as m2mtransform])
  (:gen-class))

(defn usage [options-summary]
  (->> ["Converts Markdown files to Mind maps."
        ""
        "Usage: clojure -M:run-m [options] <action> <input-file> <output-file>"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  convert    Standard convertion."]
       (str/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(def cli-options
  [["-t" "--type OUTPUT-TYPE"
    "By default the format will be an svg file."
    :default "svg"
    :parse-fn #(str/lower-case %)
    :validate [#(#{"svg" "png"} %) "Either 'svg' or 'png' format."]]
   ["-h" "--help"]])

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with a error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage summary) :ok? true}

      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors)}

      ; custom validation on arguments
      (and (= 3 (count arguments))
           (#{"convert"} (first arguments)))
      {:action (first arguments) :arguments (rest arguments) :options options}

      :else ; failed custom validation => exit with usage summary
      {:exit-message (usage summary)})))

;; (defn -main [input-file output-file]
;;   (m2mtransform/md->png input-file output-file)
;;   (System/exit 0))

(defn -main [& args]
  (let [{:keys [action options arguments exit-message ok?]} (validate-args args)]
    (prn action options arguments)
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (case action
        "clean" (m2mtransform/md->png (first arguments) (second arguments)))))
  (exit 0 ":ok"))