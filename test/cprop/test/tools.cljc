(ns cprop.test.core
  (:require [clojure.test :refer [deftest is testing]]
            [cprop.tools :as sut]))

(deftest contains-in?
  (let [config {:a {:b {:c [{:d 1} 2]}}}]
    (testing "true conditions"
      (testing "nil map edge-case"
        (is (true? (sut/contains-in? nil [])))
        (is (not= :missing (get-in nil [] :missing)))
        (is (true? (sut/contains-in? nil nil)))
        (is (not= :missing (get-in nil nil :missing))))
      (testing "primary use-cases"
        (doseq [path [nil [] [:a] [:a :b :c] [:a :b :c 1] [:a :b :c 0 :d]]]
          (is (true? (sut/contains-in? config path)) path)
          (is (not= :missing (get-in config path :missing))))))
    (testing "false conditions"
      (testing "nil map edge-case"
        (is (false? (sut/contains-in? nil [:a])) "nil path")
        (is (= :missing (get-in nil [:a] :missing))))
      (doseq [path [[nil] [:a :e] [:a :b :c 69]]]
        (is (false? (sut/contains-in? config path)) path)
        (is (= :missing (get-in config path :missing)))))))
