(ns cprop.test.core
  (:require [clojure.test :refer [deftest is testing]]
            [cprop.tools :as sut]))

(deftest contains-in?
  (let [config {:a {:b {:c [{:d 1} 2]}}}]
    (testing "true conditions"
      (doseq [input [nil [] [:a] [:a :b :c] [:a :b :c 1] [:a :b :c 0 :d]]]
        (is (true? (sut/contains-in? config input)) input)))
    (testing "false conditions"
      (doseq [input [[nil] [:a :e] [:a :b :c 69]]]
        (is (false? (sut/contains-in? config input)) input)))))
