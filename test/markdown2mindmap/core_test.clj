(ns markdown2mindmap.core-test
  "Tests for the legacy Cybermonday-based implementation."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [taoensso.timbre :as t :refer [infof]]
            [markdown2mindmap.test-helpers :as mth]
            [markdown2mindmap.transform :as sut]))

(deftest legacy-transform-test
  (mth/delete-log)
  (mth/delete-output-dir)
  ;; Tests 1-7: should match expected output
  (doseq [n (range 1 8)]
    (infof "--- Start of legacy test %d ---" n)
    (testing (str "->hiccup for input " n)
      (is (= (mth/read-hiccup n)
             (mth/md-file->hiccup n))))
    (testing (str "->puml for input " n)
      (is (= (mth/slurp-puml n)
             (mth/hiccup-file->puml n))))
    (sut/md->mindmap (mth/format-it "input" n "3md")
                     {:output-dir "output" :type "svg" :style "resources/custom.css"})
    (infof "\n--- End of legacy test %d ---" n)))

(deftest legacy-input-08-known-bug-test
  "Test 8 has a KNOWN BUG in the legacy implementation.
   The nested list items I and J should be at level 4 (****_) but
   the legacy implementation incorrectly puts them at level 3 (***_).
   This test documents the bug rather than asserting correct behavior."
  (mth/delete-log)
  (infof "--- Legacy test 8 (known bug) ---")
  (let [result (mth/hiccup-file->puml 8)
        lines (str/split-lines result)]
    ;; Verify the bug exists: I and J are at wrong level (3 instead of 4)
    (testing "Legacy implementation has nesting bug - I at level 3 instead of 4"
      (is (some #(= "***_ I bug here" %) lines)
          "Bug confirmed: I is at level 3 (***_) instead of correct level 4 (****_)"))
    (testing "Legacy implementation has nesting bug - J at level 3 instead of 4"
      (is (some #(= "***_ J and here" %) lines)
          "Bug confirmed: J is at level 3 (***_) instead of correct level 4 (****_)"))))
