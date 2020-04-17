(ns cprop.test.source
  (:require [cprop.source :as sut]
            [clojure.test :refer [are deftest testing]]))

(deftest str->value
  (testing "all data types are handled as expected"
    (are [input expected] (= expected (#'sut/str->value input {}))
      "" ""
      "nil" nil
      "hello world" "hello world"
      "-1" -1
      "1" 1
      "100000000000000000000" 100000000000000000000
      "-100000000000000000000" -100000000000000000000
      "true" true
      "false" false
      "#{1 2 3}" #{1 2 3}
      "[:a :b]" [:a :b]
      ":keyword" :keyword)))
