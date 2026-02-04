(ns markdown2mindmap.core
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [markdown2mindmap.transform :as m2mtransform]
            [markdown2mindmap.transform-nj :as m2mtransform-nj])
  (:gen-class))

(def cli-options
  [["-t" "--type OUTPUT-TYPE"
    "Either 'svg' (default) or 'png'."
    :default "svg"
    :parse-fn #(str/lower-case %)
    :validate [#(#{"svg" "png"} %) "Either 'svg' or 'png' format."]]
   [nil "--style STYLE-FILE" "Apply custom style to mindmap"]
   [nil "--with-puml" "Generate intermediate puml file"]
   [nil "--with-svg" "Generate SVG file"]
   [nil "--svg-output-dir SVG-OUTPUT-DIR" "Output directory for SVG. Implies --with-svg. Defaults to same dir as every input file"]
   [nil "--puml-output-dir PUML-OUTPUT-DIR" "Output directory for PUML. Implies --with-puml. Defaults to same dir as every input file"]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Converts Markdown files to Mind maps."
        ""
        "Usage: markdown2mindmap [options] convert <input-file>"
        "       markdown2mindmap [options] convert <input-dir>"
        "       markdown2mindmap [options] convert-legacy <input-file>"
        "       markdown2mindmap list-all-fonts [output-file]"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  convert         Converts markdown file to mindmap (using nextjournal/markdown)."
        "  convert-legacy  Converts markdown file to mindmap (using cybermonday - legacy)."
        "  list-all-fonts  Creates an SVG image listing all fonts available on the system."
        "                  output-file defaults to ./all-fonts.svg"
        ""
        "Examples: clojure -M:run-m convert --style resources/custom.css test-resources/input-07.md ."
        "          clojure -M:run-m convert-legacy --style resources/custom.css test-resources/input-07.md ."
        "          clojure -M:run-m list-all-fonts"]
       (str/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn- format-error
  "Format an error map for user display."
  [{:keys [file type message]}]
  (str "  - " (when file (str file ": "))
       (name type)
       (when message (str " - " message))))

(defn- report-results
  "Report processing results to user and return appropriate exit code.
   Returns 0 on success, 1 if any errors occurred."
  [result]
  (cond
    ;; Single error result (from list-all-fonts)
    (:error result)
    (do
      (println "Error:" (:message result))
      1)

    ;; Processing results with counts
    (:processed result)
    (let [{:keys [processed errors warning]} result]
      (when warning
        (println "Warning:" warning))
      (println (str "Processed " processed " file(s)."))
      (if (seq errors)
        (do
          (println (str "Errors (" (count errors) "):"))
          (doseq [err errors]
            (println (format-error err)))
          1)
        0))

    ;; Success result (from list-all-fonts)
    (:ok result)
    (do
      (println "Success:" (:ok result))
      0)

    ;; Unknown result format
    :else
    (do
      (println "Unknown result:" result)
      0)))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with a error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)
        [action & restargs] arguments
        nbargs (count restargs)]
    (cond
      ;; help => exit OK with usage summary
      (:help options)
      {:exit-message (usage summary) :ok? true}

      ;; errors => exit with description of errors
      errors
      {:exit-message (error-msg errors)}

      ;; custom validation on arguments
      (or
       (and (<= nbargs 1)
            (#{"list-all-fonts"} action))
       (and (= nbargs 1)
            (#{"convert" "convert-legacy"} action)))
      {:action action :arguments restargs :options options}

      ;; failed custom validation => exit with usage summary
      :else
      {:exit-message (usage summary)})))

(defn -main [& args]
  (let [{:keys [action options arguments exit-message ok?]} (validate-args args)
        [input-file-or-dir] arguments
        [output-file] arguments]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (let [result
            (case action
              "convert"        (m2mtransform-nj/md->mindmap input-file-or-dir
                                                            options)
              "convert-legacy" (m2mtransform/md->mindmap input-file-or-dir
                                                         options)
              "list-all-fonts" (m2mtransform-nj/list-all-fonts
                                (or output-file "./all-fonts.svg")))
            exit-code (report-results result)]
        (exit exit-code (if (zero? exit-code) ":ok" ":error"))))))
