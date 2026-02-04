(ns markdown2mindmap.transform-nj-test
  "Tests for the nextjournal/markdown-based implementation."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [taoensso.timbre :as t :refer [infof]]
            [markdown2mindmap.test-helpers :as mth]
            [markdown2mindmap.transform-nj :as sut]))

(deftest preprocessing-test
  (testing "HTML to markdown conversion"
    (is (= "~~strikethrough~~" 
           (#'sut/preprocess-markdown "<s>strikethrough</s>")))
    (is (= "**bold**" 
           (#'sut/preprocess-markdown "<b>bold</b>")))
    (is (= "*italic*" 
           (#'sut/preprocess-markdown "<i>italic</i>")))
    (is (= "~~strike~~ and **bold** and *italic*"
           (#'sut/preprocess-markdown "<s>strike</s> and <b>bold</b> and <i>italic</i>")))))

(deftest node->text-test
  (testing "Text extraction from AST nodes"
    (is (= "hello" (#'sut/node->text {:type :text :text "hello"})))
    (is (= "<b>bold</b>" (#'sut/node->text {:type :strong :content [{:type :text :text "bold"}]})))
    (is (= "<i>italic</i>" (#'sut/node->text {:type :em :content [{:type :text :text "italic"}]})))
    (is (= "<s>strike</s>" (#'sut/node->text {:type :strikethrough :content [{:type :text :text "strike"}]})))))

(deftest simple-heading-test
  (testing "Simple heading conversion"
    (let [md "# Title"
          result (sut/ast->puml-str (:ok (sut/md->ast md)))]
      (is (= "* Title" result)))))

(deftest multiple-headings-test
  (testing "Multiple heading levels"
    (let [md "# H1\n\n## H2\n\n### H3"
          result (sut/ast->puml-str (:ok (sut/md->ast md)))]
      (is (= "* H1\n** H2\n*** H3" result)))))

(deftest simple-list-test
  (testing "Simple bullet list"
    (let [md "# Root\n\n- Item 1\n- Item 2\n- Item 3"
          result (sut/ast->puml-str (:ok (sut/md->ast md)))]
      (is (= "* Root\n**_ Item 1\n**_ Item 2\n**_ Item 3" result)))))

(deftest nested-list-test
  (testing "Nested bullet list - the bug that was fixed"
    (let [md "# A\n\n- B\n  - C\n  - H\n    - I\n    - J"
          result (sut/ast->puml-str (:ok (sut/md->ast md)))]
      (is (= "* A\n**_ B\n***_ C\n***_ H\n****_ I\n****_ J" result)))))

(deftest inline-modifiers-test
  (testing "Inline modifiers in headings"
    (let [md "# Hello **bold** and *italic*"
          result (sut/ast->puml-str (:ok (sut/md->ast md)))]
      (is (= "* Hello <b>bold</b> and <i>italic</i>" result))))
  
  (testing "Strikethrough with HTML syntax"
    (let [md "# <s>old</s> => new"
          result (sut/ast->puml-str (:ok (sut/md->ast md)))]
      (is (= "* <s>old</s> => new" result))))
  
  (testing "Strikethrough with ~~ syntax"
    (let [md "# ~~old~~ => new"
          result (sut/ast->puml-str (:ok (sut/md->ast md)))]
      (is (= "* <s>old</s> => new" result)))))

(deftest heading-with-list-test
  (testing "Heading followed by list at correct level"
    (let [md "# Root\n\n## Section\n\n- Item 1\n- Item 2"
          result (sut/ast->puml-str (:ok (sut/md->ast md)))]
      (is (= "* Root\n** Section\n***_ Item 1\n***_ Item 2" result)))))

(deftest loose-list-test
  (testing "Loose list (with blank lines) - paragraph content"
    (let [md "# A\n\n- B\n\n  - C\n  - D"
          result (sut/ast->puml-str (:ok (sut/md->ast md)))]
      (is (= "* A\n**_ B\n***_ C\n***_ D" result)))))

(deftest compare-with-expected-test
  "Compare nextjournal implementation output with expected .puml files.
   All 8 tests should pass, including test 8 which has correct nesting."
  (mth/delete-log)
  (doseq [n (range 1 9)]
    (infof "--- NJ Test %d ---" n)
    (let [input (mth/slurp-it "input" n "3md")
          expected-puml (mth/slurp-puml n)
          ;; Remove @startmindmap/@endmindmap wrapper for comparison
          expected-body (-> expected-puml
                            (str/replace #"@startmindmap\n\n?" "")
                            (str/replace #"\n?@endmindmap" "")
                            str/trim)
          actual (sut/ast->puml-str (:ok (sut/md->ast input)))]
      (testing (str "Input " n " produces correct output")
        (is (= expected-body actual)
            (str "Mismatch for input-" n))))))
