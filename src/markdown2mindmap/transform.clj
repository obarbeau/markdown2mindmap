(ns markdown2mindmap.transform
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [cybermonday.ir]
            [markdown2mindmap.enter :as m2menter]
            [markdown2mindmap.errors :as err]
            [markdown2mindmap.exit :as m2mexit]
            [puget.printer :as puget]
            [taoensso.timbre :refer [info infof warnf]]))



;; ------------------------------------
;; AST Walking
;; ------------------------------------

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



;; ------------------------------------

(defn md->hiccup
  "Converts markdown data to hiccup AST with cybermonday.
   Returns {:ok hiccup} on success, {:error ...} on failure."
  [data]
  (try
    {:ok (cybermonday.ir/md-to-ir data)}
    (catch Exception e
      (err/log-error :parse-error
                     "Error parsing markdown with cybermonday"
                     :cause e))))

(defn md->hiccup-file
  "Generates a hiccup (edn) file from a markdown file.
   Returns {:ok file} on success, {:error ...} on failure."
  [input-file output-file]
  (let [input-result (err/safe-slurp input-file)]
    (if (:error input-result)
      input-result
      (let [hiccup-result (md->hiccup (:ok input-result))]
        (if (:error hiccup-result)
          (assoc hiccup-result :file (str input-file))
          (err/safe-spit output-file (puget/pprint-str (:ok hiccup-result))))))))

(defn hiccup->puml
  "Convert hiccup data to puml text.
   Expects raw hiccup data (not wrapped in {:ok ...})."
  [hiccup-data]
  (->> hiccup-data
       walk-hiccup
       :puml
       reverse
       (str/join "\n")))

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
            (let [hiccup-result (md->hiccup (:ok input-result))]
              (if (:error hiccup-result)
                (assoc hiccup-result :file (str input-file))
                
                ;; Generate PlantUML
                (let [puml (-> (:ok hiccup-result)
                               hiccup->puml
                               (#(->puml2 (:ok style-result) %)))
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
  "Generates mindmap image(s) from markdown file(s).
   
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
  "Creates an SVG image listing all fonts available on the system.
   Returns {:ok file} on success, {:error ...} on failure."
  [output-file]
  (let [parent-result (err/safe-make-parents output-file)]
    (if (:error parent-result)
      parent-result
      (err/create-image! output-file "svg"
                     (str/join \newline ["@startuml" "listfonts" "@enduml"])))))
