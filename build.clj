(ns build
  (:refer-clojure :exclude [test])
  (:require [cawdy.core :as cawdy]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.build.api :as b]
            [clojure.tools.deps.alpha :as t]
            [deps-deploy.deps-deploy :as d]
            [org.corfield.build :as bb]))

(def lib 'io.github.obarbeau/markdown2mindmap)
(def version "0.1.0-SNAPSHOT")
(def main 'markdown2mindmap.core)

(defn run-task-enhanced
  "Same as seancorfield's `run-task`,
   except that user deps.edn (linked to practicalli) is also included for additional aliases."
  [{:keys [java-opts jvm-opts main main-args main-opts] :as opts} aliases]
  (let [task     (str/join ", " (map name aliases))
        _        (println "\nRunning task for:" task)
        basis    (b/create-basis {:user (str (System/getProperty "user.home") "/.clojure/deps.edn")
                                  :aliases aliases})
        combined (t/combine-aliases basis aliases)
        cmds     (b/java-command
                  {:basis     basis
                   :java-opts (into (or java-opts (:jvm-opts combined))
                                    jvm-opts)
                   :main      (or 'clojure.main main)
                   :main-args (into (or main-args
                                        (:main-opts combined)
                                        ["-m" "cognitect.test-runner"])
                                    main-opts)})
        {:keys [exit]} (b/process cmds)]
    (when-not (zero? exit)
      (throw (ex-info (str "Task failed for: " task) {}))))
  opts)

(defn eastwood "Run Eastwood." [opts]
  (-> opts (run-task-enhanced [:lint/eastwood])))

(defn coverage [opts]
  (-> opts (run-task-enhanced [:test/cloverage])))

(defn test "Run the tests." [opts]
  (-> opts (run-task-enhanced [:test/kaocha])))

(defn test-watch "Run the tests and watch." [opts]
  (-> opts (run-task-enhanced [:test/watch])))

(defn ci "Run the CI pipeline of tests (and build the uberjar)." [opts]
  (-> opts
      (assoc :lib lib :version version :main main)
      (test)
      (bb/clean)
      (bb/uber)))

(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def copy-srcs ["src" "resources"])

(defn jar
  [params]
  (let [basis (b/create-basis)]
    (b/write-pom {:class-dir class-dir
                  :lib lib
                  :version version
                  :basis basis
                  :src-dirs ["src"]
                  :resource-dirs ["resources"]})
    (b/copy-dir {:src-dirs copy-srcs
                 :target-dir class-dir})
    (b/jar {:class-dir class-dir
            :jar-file jar-file}))
  (println "Created:" jar-file)
  params)

(defn deploy
  [params]
  (let [params' (-> params bb/clean jar)]
    (d/deploy {:installer :remote
               :artifact jar-file
               :pom-file (b/pom-path {:lib lib :class-dir class-dir})
               :sign-releases? true
               :sign-key-id (or (System/getenv "CLOJARS_GPG_ID")
                                (throw (RuntimeException. "CLOJARS_GPG_ID environment variable not set")))})
    params'))

(defn codox
  [params]
  (let [basis (b/create-basis {:extra '{:deps {codox/codox {:mvn/version "0.10.8"}
                                               codox-theme-rdash/codox-theme-rdash {:mvn/version "0.1.2"}}}
                               ;; This is needed because some of the namespaces
                               ;; rely on optional dependencies provided by :dev
                               :aliases [:dev]})
        expression `(do
                      ((requiring-resolve 'codox.main/generate-docs)
                       {:metadata {:doc/format :markdown}
                        :themes [:rdash]
                        :source-paths  ["src"]
                        :output-path "doc"
                        :name ~(str lib)
                        :version ~version
                        :description "@todo description"})
                      nil)
        process-params (b/java-command
                        {:basis basis
                         :main "clojure.main"
                         :main-args ["--eval" (pr-str expression)]})]
    (b/process process-params))
  params)

(defn caddy
  "First run caddy server: `caddy run --config ./resources/Caddyfile`.
   In another terminal run `clojure -T:dev:build caddy`.
   Finally go to <https://localhost:2015>."
  [_opts]
  (let [conn (cawdy/connect "http://localhost:2019")]
    (cawdy/create-server conn :project-documentation
                         {:listen [":2015"]
                          :automatic_https {:disable false}})
    (cawdy/add-route conn
                     :project-documentation "localhost"
                     :files {:root (.getAbsolutePath (io/file "doc"))})
    (println "Please enjoy documentation at https://localhost:2015")))

