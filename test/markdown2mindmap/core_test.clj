(ns markdown2mindmap.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [taoensso.timbre :as t :refer [infof]]
            [markdown2mindmap.test-helpers :as mth]
            [markdown2mindmap.transform :as sut]))

(deftest a-test
  (mth/delete-log)
  (mth/delete-output-dir)
  (doseq [n (range 1 8)]
    (infof "--- Start of test %d ---" n)
    (testing "->hiccup"
      (is (=  (mth/read-hiccup n)
              (mth/md-file->hiccup n))))
    (testing "->puml"
      (is (=  (mth/slurp-puml n)
              (mth/hiccup-file->puml n))))
    (sut/md->mindmap (mth/format-it "input" n "md") "output" "svg")
    (infof "\n--- End of test %d ---" n)))