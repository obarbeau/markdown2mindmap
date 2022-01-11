(ns markdown2mindmap.core
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [markdown2mindmap.transform :as m2mtransform])
  (:gen-class))

(def cli-options
  [["-t" "--type OUTPUT-TYPE"
    "Either 'svg' (default) or 'png'."
    :default "svg"
    :parse-fn #(str/lower-case %)
    :validate [#(#{"svg" "png"} %) "Either 'svg' or 'png' format."]]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Converts Markdown files to Mind maps."
        ""
        "Usage: markdown2mindmap [options] <action> <input-file> <output-dir>"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  convert    Standard convertion."
        ""
        "Example: clojure -M:run-m convert test-resources/input-07.md ."]
       (str/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

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
      ;; help => exit OK with usage summary
      (:help options)
      {:exit-message (usage summary) :ok? true}

      ;; errors => exit with description of errors
      errors
      {:exit-message (error-msg errors)}

      ;; custom validation on arguments
      (and (= 3 (count arguments))
           (#{"convert"} (first arguments)))
      {:action (first arguments) :arguments (rest arguments) :options options}

      ;; failed custom validation => exit with usage summary
      :else
      {:exit-message (usage summary)})))

(defn -main [& args]
  (let [{:keys [action options arguments exit-message ok?]} (validate-args args)
        [input-file output-directory] arguments]
    (prn input-file output-directory (:type options))
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (case action
        "convert" (m2mtransform/md->mindmap input-file
                                            output-directory
                                            (:type options)))))
  (exit 0 ":ok"))