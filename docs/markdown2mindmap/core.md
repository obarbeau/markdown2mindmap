# markdown2mindmap.core



??? tip  "(`ns`)"

    ```clojure
    (ns markdown2mindmap.core
      (:require [clojure.string :as str]
                [clojure.tools.cli :refer [parse-opts]]
                [markdown2mindmap.transform :as m2mtransform])
      (:gen-class))
    ```

## `cli-options`



??? tip  "(`def`)"

    ```clojure
    (def cli-options
      [["-t" "--type OUTPUT-TYPE"
        "Either 'svg' (default) or 'png'."
        :default "svg"
        :parse-fn #(str/lower-case %)
        :validate [#(#{"svg" "png"} %) "Either 'svg' or 'png' format."]]
       [nil "--style STYLE-FILE" "Apply custom style to mindmap"]
       [nil "--with-puml" "Generate intermediate puml file"]
       ["-h" "--help"]])
    ```

## `usage`

```clojure
(usage options-summary)
```

??? tip  "(`defn`)"

    ```clojure
    (defn usage [options-summary]
      (->> ["Converts Markdown files to Mind maps."
            "Usage: markdown2mindmap [options] convert <input-file> <output-dir>"
            "       markdown2mindmap list-all-fonts [output-file]"
            "Options:"
            options-summary
            "Actions:"
            "  convert         Converts markdown file to mindmap."
            "  list-all-fonts  Creates an SVG image listing all fonts available on the system."
            "                  output-file defaults to ./all-fonts.svg"
            "Examples: clojure -M:run-m convert --style resources/custom.css test-resources/input-07.md ."
            "          clojure -M:run-m list-all-fonts"]
           (str/join \newline)))
    ```

## `error-msg`

```clojure
(error-msg errors)
```

??? tip  "(`defn`)"

    ```clojure
    (defn error-msg [errors]
      (str "The following errors occurred while parsing your command:\n\n"
           (str/join \newline errors)))
    ```

## `exit`

```clojure
(exit status msg)
```

??? tip  "(`defn`)"

    ```clojure
    (defn exit [status msg]
      (println msg)
      (System/exit status))
    ```

## `validate-args`

Validate command line arguments. Either return a map indicating the program
  should exit (with a error message, and optional ok status), or a map
  indicating the action the program should take and the options provided.

```clojure
(validate-args args)
```

??? tip  "(`defn`)"

    ```clojure
    (defn validate-args
      [args]
      (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)
            nbargs (count arguments)
            [action & restargs] arguments]
        (cond
          ;; help => exit OK with usage summary
          (:help options)
          {:exit-message (usage summary) :ok? true}
          ;; errors => exit with description of errors
          errors
          {:exit-message (error-msg errors)}
          ;; custom validation on arguments
          (or
           (and (<= nbargs 2)
                (#{"list-all-fonts"} action))
           (and (= 3 nbargs)
                (#{"convert"} action)))
          {:action action :arguments restargs :options options}
          ;; failed custom validation => exit with usage summary
          :else
          {:exit-message (usage summary)})))
    ```

## `-main`

```clojure
(-main & args)
```

??? tip  "(`defn`)"

    ```clojure
    (defn -main [& args]
      (let [{:keys [action options arguments exit-message ok?]} (validate-args args)
            [input-file output-directory] arguments
            [output-file] arguments]
        (if exit-message
          (exit (if ok? 0 1) exit-message)
          (case action
            "convert"        (m2mtransform/md->mindmap input-file
                                                       output-directory
                                                       options)
            "list-all-fonts" (m2mtransform/list-all-fonts
                              (or output-file "./all-fonts.svg")))))
      (exit 0 ":ok"))
    ```

