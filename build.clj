(ns build
  "Use `clj -T:dev:build <var>` for these tasks"
  (:refer-clojure :exclude [test])
  (:require [cawdy.core :as cawdy]
            [clojure.java.io :as io]
            [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as d]
            [org.corfield.build :as bb]))

(def lib 'io.github.obarbeau/markdown2mindmap)
(def version "0.1.0-SNAPSHOT")
(def main 'markdown2mindmap.core)

(defn test "Run the tests." [opts]
  (bb/run-tests opts))

(defn ci "Run the CI pipeline of tests (and build the uberjar)." [opts]
  (-> opts
      (assoc :lib lib :version version :main main)
      (bb/run-tests)
      (bb/clean)
      (bb/uber)))

(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def copy-srcs ["src" "resources"])

(defn clean
  [params]
  (b/delete {:path "target"})
  params)

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
  (let [params' (-> params clean jar)]
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
  [opts]
  (let [conn (cawdy/connect "http://localhost:2019")]
    (cawdy/create-server conn :project-documentation
                         {:listen [":2015"]
                          :automatic_https {:disable false}})
    (cawdy/add-route conn
                     :project-documentation "localhost"
                     :files {:root (.getAbsolutePath (io/file "doc"))})
    (println "Please enjoy documentation at https://localhost:2015")))

(defn eastwood "Run Eastwood." [opts]
  (-> opts (bb/run-task [:eastwood])))

(defn coverage [opts]
  (-> opts (bb/run-task [:coverage :dev])))
