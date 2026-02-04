(ns markdown2mindmap.transform-nj
  "Markdown to PlantUML mindmap transformation using nextjournal/markdown.

   This implementation uses a simple recursive AST traversal approach,
   which is cleaner than the enter/exit state machine used in the
   cybermonday-based implementation."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [markdown2mindmap.errors :as err]
            [nextjournal.markdown :as md]
            [taoensso.timbre :refer [info warnf]]))



;; ------------------------------------
;; Preprocessing
;; ------------------------------------

(defn- preprocess-markdown
  "Preprocess markdown to convert HTML tags to standard markdown syntax.
   - <s>text</s> → ~~text~~ (strikethrough)
   - <b>text</b> → **text** (bold)
   - <i>text</i> → *text* (italic)"
  [md-str]
  (-> md-str
      (str/replace #"<s>([^<]*)</s>" "~~$1~~")
      (str/replace #"<b>([^<]*)</b>" "**$1**")
      (str/replace #"<i>([^<]*)</i>" "*$1*")))

;; ------------------------------------
;; AST to PlantUML conversion
;; ------------------------------------

(defn- node->text
  "Extract plain text from a node, applying inline modifiers."
  [node]
  (case (:type node)
    :text (:text node)
    :strong (str "<b>" (apply str (map node->text (:content node))) "</b>")
    :em (str "<i>" (apply str (map node->text (:content node))) "</i>")
    :strikethrough (str "<s>" (apply str (map node->text (:content node))) "</s>")
    :softbreak " "
    ;; Default: recursively get text from content
    (if-let [content (:content node)]
      (apply str (map node->text content))
      "")))

(defn- plain->text
  "Extract text from a :plain node (used in list items).
   Stops at first :softbreak to match legacy behavior (ignore continuation text)."
  [node]
  (if (= :plain (:type node))
    (->> (:content node)
         (take-while #(not= :softbreak (:type %)))
         (map node->text)
         (apply str))
    (node->text node)))

(declare ast->puml-lines)

(defn- process-heading
  "Process a heading node. Returns a single puml line."
  [node _context]
  (let [level (:heading-level node)
        text (apply str (map node->text (:content node)))
        stars (apply str (repeat level "*"))]
    [(str stars " " text)]))

(defn- process-list-item
  "Process a list item. The first :plain or :paragraph child becomes the item text,
   any :bullet-list or :numbered-list children are nested lists."
  [node context]
  (let [heading-level (:heading-level context 1)
        list-depth (:list-depth context 1)
        total-level (+ heading-level list-depth)
        stars (apply str (repeat total-level "*"))
        ;; Separate text content from nested lists
        ;; Text can be in :plain (tight list) or :paragraph (loose list)
        text-nodes (filter #(#{:plain :paragraph} (:type %)) (:content node))
        list-nodes (filter #(#{:bullet-list :numbered-list} (:type %)) (:content node))
        ;; Get text from text nodes
        text (str/join " " (map plain->text text-nodes))
        ;; Current item line
        current-line (str stars "_ " text)
        ;; Process nested lists (depth is incremented by process-list)
        nested-lines (mapcat #(ast->puml-lines % context) list-nodes)]
    (cons current-line nested-lines)))

(defn- process-list
  "Process a bullet-list or numbered-list node.
   Increments list-depth for all items in this list."
  [node context]
  (let [list-context (update context :list-depth (fnil inc 0))]
    (mapcat #(ast->puml-lines % list-context) (:content node))))

(defn- ast->puml-lines
  "Convert an AST node to PlantUML lines. Returns a seq of strings.
   Context tracks current heading level and list depth."
  [node context]
  (case (:type node)
    :doc
    (mapcat #(ast->puml-lines % context) (:content node))

    :heading
    (let [lines (process-heading node context)
          ;; Update context with new heading level for subsequent lists
          new-context (assoc context :heading-level (:heading-level node))]
      ;; Return lines but the context update is handled by processing order
      ;; We need to track heading level for lists that follow
      (with-meta lines {:new-context new-context}))

    (:bullet-list :numbered-list)
    (process-list node context)

    :list-item
    (process-list-item node context)

    :paragraph
    ;; Paragraphs outside of lists are ignored in mindmap (or could be added to previous heading)
    []

    ;; Default: process children if any
    (if-let [content (:content node)]
      (mapcat #(ast->puml-lines % context) content)
      [])))

(defn- ast->puml
  "Convert a full AST document to PlantUML mindmap lines.
   Tracks heading level across the document for proper list indentation."
  [ast]
  (loop [nodes (:content ast)
         context {:heading-level 1 :list-depth 0}
         result []]
    (if (empty? nodes)
      result
      (let [node (first nodes)
            lines (ast->puml-lines node context)
            ;; Update context if this was a heading
            new-context (if (= :heading (:type node))
                          (assoc context :heading-level (:heading-level node))
                          context)]
        (recur (rest nodes)
               new-context
               (into result lines))))))



;; ------------------------------------
;; Public API
;; ------------------------------------

(defn md->ast
  "Parse markdown string to AST using nextjournal/markdown.
   Preprocesses HTML tags to standard markdown syntax.
   Returns {:ok ast} on success, {:error ...} on failure."
  [markdown-str]
  (try
    {:ok (md/parse (preprocess-markdown markdown-str))}
    (catch Exception e
      (err/log-error :parse-error
                     "Error parsing markdown"
                     :cause e))))

(defn ast->puml-str
  "Convert AST to PlantUML mindmap string (without @start/@end directives)."
  [ast]
  (str/join "\n" (ast->puml ast)))

(defn ->puml-wrapped
  "Wrap puml text with PlantUML mindmap directives and optional styles."
  [styles puml]
  (str/join
   \newline
   (list
    "@startmindmap"
    styles
    puml
    "@endmindmap")))



(defn- process-single-file
  "Process a single markdown file to mindmap.
   Returns {:ok results} or {:error ...}."
  [input-file {:keys [type style with-svg svg-output-dir with-puml puml-output-dir]}]
  (let [svg-output-directory (or svg-output-dir (.getParent input-file))
        puml-output-directory (or puml-output-dir (.getParent input-file))
        output-name (str/replace (.getName input-file) #"(?i)\.3md" "")
        output-img (io/file svg-output-directory (str output-name "." type))
        output-puml (io/file puml-output-directory (str output-name ".puml"))]

    ;; Read input file
    (let [input-result (err/safe-slurp input-file)]
      (if (:error input-result)
        input-result

        ;; Read style file if specified
        (let [style-result (if style (err/safe-slurp style) {:ok nil})]
          (if (:error style-result)
            style-result

            ;; Parse markdown
            (let [ast-result (md->ast (:ok input-result))]
              (if (:error ast-result)
                (assoc ast-result :file (str input-file))

                ;; Generate PlantUML
                (let [puml (-> (:ok ast-result)
                               ast->puml-str
                               (#(->puml-wrapped (:ok style-result) %)))
                      results (atom {:ok true :files []})]

                  ;; Create output directories
                  (when (or with-puml puml-output-dir)
                    (err/safe-make-parents output-puml))
                  (when (or with-svg svg-output-dir)
                    (err/safe-make-parents output-img))

                  ;; Write PUML file
                  (when (or with-puml puml-output-dir)
                    (let [previous-content (when (.exists (io/as-file output-puml))
                                             (:ok (err/safe-slurp output-puml)))]
                      (if (= puml previous-content)
                        (printf "unchanged file %s\n" output-puml)
                        (let [result (err/safe-spit output-puml puml)]
                          (if (:error result)
                            (swap! results assoc :error true :puml-error result)
                            (do
                              (printf "generated %s\n" output-puml)
                              (swap! results update :files conj (str output-puml))))))))

                  ;; Generate image
                  (when (or with-svg svg-output-dir)
                    (let [result (err/create-image! output-img type puml)]
                      (if (:error result)
                        (swap! results assoc :error true :image-error result)
                        (swap! results update :files conj (str output-img)))))

                  @results)))))))))

(defn md->mindmap
  "Generate mindmap image(s) from markdown file(s).

   Options:
   - :type - Output format: \"svg\" (default) or \"png\"
   - :style - Path to custom CSS style file
   - :with-svg - Generate SVG output
   - :svg-output-dir - Output directory for SVG files
   - :with-puml - Generate intermediate .puml file
   - :puml-output-dir - Output directory for .puml files

   Returns a map with :processed (count), :errors (list of errors)."
  [input-file-or-dir options]
  (let [input (io/file input-file-or-dir)]
    (if-not (.exists input)
      (do
        (err/log-error :file-not-found
                   (str "Input path does not exist: " input-file-or-dir)
                   :file input-file-or-dir)
        {:processed 0 :errors [{:file input-file-or-dir :type :file-not-found}]})

      (let [files (filter #(str/ends-with? (str %) ".3md") (file-seq input))
            results (atom {:processed 0 :errors []})]

        (if (empty? files)
          (do
            (warnf "No .3md files found in: %s" input-file-or-dir)
            {:processed 0 :errors [] :warning "No .3md files found"})

          (do
            (doseq [file files]
              (info "Processing:" (str file))
              (let [result (process-single-file file options)]
                (swap! results update :processed inc)
                (when (:error result)
                  (swap! results update :errors conj
                         {:file (str file)
                          :type (:type result)
                          :message (:message result)}))))
            @results))))))

(defn list-all-fonts
  "Create an SVG image listing all fonts available on the system.
   Returns {:ok file} on success, {:error ...} on failure."
  [output-file]
  (let [parent-result (err/safe-make-parents output-file)]
    (if (:error parent-result)
      parent-result
      (err/create-image! output-file "svg"
                     (str/join \newline ["@startuml" "listfonts" "@enduml"])))))
