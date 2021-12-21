(ns markdown2mindmap.core
  (:require [clojure.string]
            [clojure.java.io]
            [clojure.string :as str]
            [cybermonday.ir]
            [clojure.walk :refer [postwalk prewalk prewalk-demo postwalk-demo]]
            [puget.printer :as puget]
            [taoensso.timbre :as t :refer [info infof log]]
            [taoensso.encore :as enc]
            [taoensso.timbre.appenders.core :as appenders])
  (:import (java.io FileOutputStream)
           (net.sourceforge.plantuml SourceStringReader))
  (:gen-class))


(def log-file-name "./output/markdown2mindmap.log")

(t/merge-config!
 {:timestamp-opts  {:pattern ""}
  :output-fn
  (fn  ([data]
        (let [{:keys [level ?err #_vargs msg_ ?ns-str ?file hostname_
                      timestamp_ ?line]} data]
          (str
           ;; nothing else than message!
           ;;(when-let [ts (force timestamp_)] (str ts " "))
           ;;(force hostname_)  " "
           ;;(str/upper-case (name level))  " "
           ;;"[" (or ?ns-str ?file "?") ":" (or ?line "?") "] - "
           (force msg_)
           (when-not false
             (when-let [err ?err]
               (str enc/system-newline (t/stacktrace err))))))))
  :appenders {:spit (appenders/spit-appender {:fname log-file-name})
              :println {:enabled? false}}})

;;(info t/*config*)

;; ------------------------------------

(defn- enter-heading [result [elt-name attributes & children :as x]]
  ;; on utilise le level dans l'attribut du heading
  (let [level-heading (:level attributes)]
    (swap! result assoc-in [:heading :level] level-heading)
    (swap! result assoc-in [:heading :inside] true)
    (swap! result assoc-in [:heading :children] (count children)))
  (info (t/color-str :blue ">>enter-heading") @result)
  x)

(defn- enter-ol-ul
  ""
  [result [elt-name attributes & children :as x]]
  (swap! result assoc-in [:ul :inside] true)
  (swap! result update-in [:ul :children] #(cons (count children) %))
  (info (t/color-str :blue ">>enter-ol-ul") @result)
  x)

(defn- enter-li
  ""
  [result [elt-name attributes & children :as x]]
  (swap! result assoc-in [:li :inside] true)
  ;; on triche sur le nombre d'enfants.
  (swap! result assoc-in [:li :children] 1)
  (info (t/color-str :blue ">>enter-li") @result)
  x)

(defn- enter-p
  ""
  [result [elt-name attributes & children :as x]]
  #_(info @result)
  (swap! result assoc-in [:p :inside] true)
  (swap! result assoc-in [:p :children] (count children))
  (info (t/color-str :blue ">>enter-p") @result)
  x)

(defn- enter-em
  ""
  [result [elt-name attributes & children :as x]]
  (swap! result assoc :modifier :em)
  (info (t/color-str :blue ">>enter-em") @result)
  x)

(defn- enter-s
  ""
  [result [elt-name attributes & children :as x]]
  (swap! result assoc :modifier :s)
  (info (t/color-str :blue ">>enter-s") @result)
  x)

(defn- enter-strong
  ""
  [result [elt-name attributes & children :as x]]
  (swap! result assoc :modifier :strong)
  (info (t/color-str :blue ">>enter-strong") @result)
  x)

(defn- apply-modifier
  ""
  [result s]
  (let [modifier (:modifier @result)]
    (swap! result assoc :modifier nil)
    (case modifier
      :em (format "<i>%s</i>" s)
      :strong (format "<b>%s</b>" s)
      :s (format "<s>%s</s>" s)
      s)))

;; ------------------------------------

(defn- leave-heading
  ""
  [result]
  (swap! result assoc-in [:heading :inside] false)
  (let [text (-> (get-in @result [:heading :level])
                 (repeat "*")
                 (concat " " (:buffer @result))
                 (#(apply str %)))]
    (swap! result update :plantuml conj text)
    (swap! result assoc :buffer ""))
  (info (t/color-str :purple "<<leave-heading") @result))


(defn- last-children-simple? [result ctype dec-children]
  (swap! result assoc-in [ctype :children] dec-children)
  (zero? dec-children))

(defn- last-children-imbriqued?
  "cas pour éléments imbriqués: ul"
  [result ctype children]
  (let [[child1 & rest] children
        child1dec (dec (or child1 1))]
    (if (zero? child1dec)
      (do
        (swap! result assoc-in [ctype :children] rest)
        ;; on positionne inside à false seulement si on
        ;; est complètement sorti des éléments ul imbriqués
        (when (nil? rest)
          (swap! result assoc-in [ctype :inside] false))
        true)
      (do (swap! result assoc-in [ctype :children] (cons child1dec rest))
          false))))

(defn- last-children?
  "renvoie true si last children d'une imbrication donnée (pour ul)
  ou last children tout court pour les éléments non imbriqués"
  [result ctype]
  (let [children (get-in @result [ctype :children])]
    (if (number? children)
      (last-children-simple? result ctype (dec children))
      (last-children-imbriqued? result ctype children))))

(defn- leave-ol-ul
  ""
  [result]
  (info (t/color-str :purple "<<leave-ol-ul") @result))

(defn- leave-li
  ""
  [result]
  (swap! result assoc-in [:li :inside] false)
  ;; Si on est dans un ul alors:
  ;; - on utilise le level heading + celui de l'ul - 1 pour déterminer le level total
  (let [level-heading (get-in @result [:heading :level])
        level-ol-ul (count (get-in @result [:ul :children]))
        level-total (+ level-heading level-ol-ul)
        text (-> (repeat level-total  "*")
                 (concat  "_ " (:buffer @result))
                 (#(apply str %)))]
    #_(infof "level-total=%d (:inside-ol-ul result)=%s" level-total (get-in @result [:ul :inside]))
    (swap! result update :plantuml conj text)
    (swap! result assoc :buffer "")
    (info (t/color-str :purple "<<leave-li") @result)
    (when (and (get-in @result [:ul :inside])
               (last-children? result :ul))
      (leave-ol-ul result))))

(defn- leave-p
  ""
  [result]
  (swap! result assoc-in [:p :inside] false)
  (info (t/color-str :purple "<<leave-p") @result)

  (when (and (get-in @result [:li :inside])
             (last-children? result :li))
    (leave-li result))
  ;; on efface le buffer s'il n'a pas été utilisé.
  (swap! result assoc :buffer ""))

(defn- process-string
  [result s]
  (if (:ignore-string @result)
    (info "ignore string " s)
    (do
      (swap! result update-in [:buffer] str (apply-modifier result s))
      (info (t/color-str :blue ">>process-string") s @result)))

  (swap! result assoc :ignore-string false)

  (when (and (get-in @result [:heading :inside])
             (last-children? result :heading))
    (leave-heading result))

  (when (and (get-in @result [:p :inside])
             (last-children? result :p))
    (leave-p result))
  s)

(defn- through-slb
  [result x]
  (when (get-in @result [:ul :inside])
    (swap! result assoc :ignore-string true))
  ;; slb est normalement un enfant de p il faut donc décrémenter au besoin
  (when (and (get-in @result [:p :inside])
             (last-children? result :p))
    (leave-p result))
  x)

;; ------------------------------------

(defn walk-fn
  [result x]
  #_(infof "\nx=%s %s" x (str/replace (type x) #"class clojure\.lang\." ""))
  (cond
    (vector? x)
    (let [[elt-name attributes & children] x]
      (infof "\nvector with %d children=%s" (count children) x)
      (case elt-name
        :div ;;root div
        x

        :markdown/heading
        (enter-heading result x)

        :ul
        (enter-ol-ul result x)

        :ol
        (enter-ol-ul result x)

        :markdown/bullet-list-item
        (enter-li result x)

        :markdown/ordered-list-item
        (enter-li result x)

        :markdown/soft-line-break
        (through-slb result x)

        :p
        (enter-p result x)

        :em
        (enter-em result x)

        :s
        (enter-s result x)

        :strong
        (enter-strong result x)

        ;; else
        (do #_(info "nothing known " x)
            x)))

    (string? x)
    (process-string result x)

    #_#_(keyword? x)
      (infof "keyword=%s" x)

    #_#_:else
      (infof "what is it? %s" x)))


(defn md->hiccup
  "Convert markdown data to hiccup tree with cybermonday"
  [data]
  (cybermonday.ir/md-to-ir data))

(defn walk-hiccup
  ""
  [hiccup-data]
  (let [result (atom {})]
    (prewalk (partial walk-fn result) hiccup-data)
    @result))

(defn hiccup->map
  ""
  [hiccup-data]
  (->> hiccup-data
       walk-hiccup
       :plantuml
       reverse
       (str/join "\n")))

(defn ->plantuml2 [xx]
  (clojure.string/join
   "\n"
   (list
    "@startmindmap"
    xx
    "@endmindmap")))

(defn create-image! [output-file map-data]
  (let [uml (->plantuml2 map-data)
        out (FileOutputStream. (clojure.java.io/file output-file))]
    (-> (SourceStringReader. uml)
        (.generateImage out))
    (.close out)))

(defn md->png [input-file output-file]
  (->> (slurp input-file)
       md->hiccup
       hiccup->map
       (create-image! output-file)))

(defn -main [input-file output-file]
  (md->png input-file output-file)
  (System/exit 0))