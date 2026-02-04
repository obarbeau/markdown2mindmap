(ns markdown2mindmap.errors
  "Centralized error handling and safe file I/O operations."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [taoensso.timbre :refer [error]])
  (:import (java.io FileNotFoundException FileOutputStream IOException)
           (net.sourceforge.plantuml SourceStringReader
                                     FileFormatOption
                                     FileFormat)))

;; ------------------------------------
;; Error result construction
;; ------------------------------------

(defn error-result
  "Create a standardized error result map."
  [type message & {:keys [file cause]}]
  {:error true
   :type type
   :message message
   :file file
   :cause cause})

(defn log-error
  "Log an error and return an error result."
  [type message & {:keys [file cause]}]
  (error (str "[" (name type) "] " message
              (when file (str " - File: " file))
              (when cause (str " - Cause: " (.getMessage cause)))))
  (error-result type message :file file :cause cause))

;; ------------------------------------
;; Safe file I/O
;; ------------------------------------

(defn safe-slurp
  "Read file contents with error handling.
   Returns {:ok content} on success, {:error ...} on failure."
  [file]
  (try
    {:ok (slurp file)}
    (catch FileNotFoundException e
      (log-error :file-not-found
                 (str "File not found: " file)
                 :file (str file)
                 :cause e))
    (catch IOException e
      (log-error :io-error
                 (str "Error reading file: " file)
                 :file (str file)
                 :cause e))
    (catch Exception e
      (log-error :unknown-error
                 (str "Unexpected error reading file: " file)
                 :file (str file)
                 :cause e))))

(defn safe-spit
  "Write content to file with error handling.
   Returns {:ok file} on success, {:error ...} on failure."
  [file content]
  (try
    (spit file content)
    {:ok (str file)}
    (catch IOException e
      (log-error :io-error
                 (str "Error writing file: " file)
                 :file (str file)
                 :cause e))
    (catch SecurityException e
      (log-error :permission-denied
                 (str "Permission denied writing file: " file)
                 :file (str file)
                 :cause e))
    (catch Exception e
      (log-error :unknown-error
                 (str "Unexpected error writing file: " file)
                 :file (str file)
                 :cause e))))

(defn safe-make-parents
  "Create parent directories with error handling.
   Returns {:ok path} on success, {:error ...} on failure."
  [file]
  (try
    (io/make-parents file)
    {:ok (str (.getParent (io/file file)))}
    (catch IOException e
      (log-error :io-error
                 (str "Error creating directories for: " file)
                 :file (str file)
                 :cause e))
    (catch SecurityException e
      (log-error :permission-denied
                 (str "Permission denied creating directories for: " file)
                 :file (str file)
                 :cause e))))

;; ------------------------------------
;; PlantUML image generation
;; ------------------------------------

(defn create-image!
  "Generate an image file from PlantUML text.
   Returns {:ok file} on success, {:error ...} on failure."
  [output-file type puml-text]
  (let [file (io/file output-file)]
    (try
      (with-open [out (FileOutputStream. file)]
        (let [format (->> type
                          str/upper-case
                          (.getField FileFormat)
                          ;; The nil is there because you are getting a static field,
                          ;; rather than a member field of a particular object.
                          (#(.get ^java.lang.reflect.Field % nil))
                          FileFormatOption.)
              reader (SourceStringReader. puml-text)
              description (.outputImage reader out format)]
          (if description
            (do
              (printf "generated %s\n" output-file)
              {:ok (str output-file)})
            (log-error :plantuml-error
                       "PlantUML failed to generate image (possibly invalid syntax)"
                       :file (str output-file)))))
      (catch IOException e
        (log-error :io-error
                   (str "Error writing image file: " output-file)
                   :file (str output-file)
                   :cause e))
      (catch IllegalArgumentException e
        (log-error :invalid-format
                   (str "Invalid output format: " type)
                   :file (str output-file)
                   :cause e))
      (catch Exception e
        (log-error :plantuml-error
                   (str "PlantUML error generating image: " output-file)
                   :file (str output-file)
                   :cause e)))))
