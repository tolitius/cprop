(ns cprop.core-test
  (:require [cprop :refer [conf cursor]]
            [clojure.test :refer :all]))

(deftest should-slurp-and-provide
  (testing "should read config from -Dconfig.var"
    (is (= (conf :answer) 42)))
  (testing "should be able to naviage nested props"
    (is (= (conf :source :account :rabbit :vhost) "/z-broker"))))

(deftest should-create-cursors
  (testing "should create a rabbit cursor"
    (is (= ((cursor :source :account :rabbit) :vhost) "/z-broker"))))

(deftest should-compose-cursors
  (testing "should compose one level"
    (is (= ((cursor (cursor :source) :account) :rabbit :vhost) "/z-broker")))
  (testing "should compose nested cursors"
    (is (= ((cursor (cursor (cursor :source) :account) :rabbit) :vhost) "/z-broker"))))

(comment ;; playground

(conf :answer) ;; 42
(conf :source :account :rabbit :vhost) ;; "/z-broker"

(def rabbit 
  (cursor :source :account :rabbit))

(rabbit :vhost) ;; "/z-broker"

)
