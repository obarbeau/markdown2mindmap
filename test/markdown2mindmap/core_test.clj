(ns markdown2mindmap.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [taoensso.timbre :as t :refer [infof]]
            [markdown2mindmap.test-helpers :as mth]
            [markdown2mindmap.transform :as sut]))

(deftest a-test
  (mth/delete-log)
  (doseq [n (range 1 8)]
    (infof "--- Start of test %d ---" n)
    (testing "->hiccup"
      (is (=  (mth/read-hiccup n)
              (mth/md-file->hiccup n))))
    (testing "->puml"
      (is (=  (mth/slurp-plantuml n)
              (mth/hiccup-file->puml n))))
    (sut/md->png (mth/format-it "input" n "md") (mth/output-img n))
    (infof "\n--- End of test %d ---" n)))