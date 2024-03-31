(ns markdown2mindmap.log
  (:require [cybermonday.ir]
            [taoensso.encore :as enc]
            [taoensso.timbre :as t]
            [taoensso.timbre.appenders.core :as appenders]))

(def log-file-name "./output/markdown2mindmap.log")
(def log-keys [:ol-ul :li :p :heading :modifier :buffer :ignore-string])

(t/merge-config!
 {:timestamp-opts  {:pattern ""}
  :min-level :warn
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
           (when-let [err ?err]
             (str enc/system-newline (t/stacktrace err)))))))
  :appenders {:spit (appenders/spit-appender {:fname log-file-name})
              :println {:enabled? false}}})

;;(info t/*config*)

;; ------------------------------------