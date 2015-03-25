(ns cprop.core-test
  (:require [cprop :refer [conf]]
            [clojure.test :refer :all]))

(deftest a-test
  (testing "should read config from -Dconfig.var"
    (is (= (conf :answer) 42))))
