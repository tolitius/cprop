(ns cprop.test.core
  (:require [cprop.core :refer [conf cursor load-config]]
            [clojure.edn :as edn]
            [clojure.test :refer :all]))

(load-config)

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

(defn- read-system-env []
  (->> {"DATOMIC_URL" "\"datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic\""
        "AWS_ACCESS__KEY" "\"AKIAIOSFODNN7EXAMPLE\""
        "AWS_SECRET__KEY" "\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\""
        "AWS_REGION" "\"ues-east-1\""
        "IO_HTTP_POOL_CONN__TIMEOUT" "60000"
        "IO_HTTP_POOL_MAX__PER__ROUTE" "10"
        "OTHER__THINGS" "[1 2 3 \"42\"]"}
       (map (fn [[k v]] [(#'cprop.core/env->path k) v]))
       (into {})))

(deftest should-merge-with-env
  (let [config (edn/read-string 
                 (slurp "test/resources/fill-me-in.edn"))]
    (is (= {:datomic {:url "datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic"},
            :aws {:access-key "AKIAIOSFODNN7EXAMPLE",
                  :secret-key "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                  :region "ues-east-1",
                  :visiblity-timeout-sec 30,
                  :max-conn 50,
                  :queue "cprop-dev"},
            :io
            {:http
             {:pool
              {:socket-timeout 600000,
               :conn-timeout 60000,
               :conn-req-timeout 600000,
               :max-total 200,
               :max-per-route 10}}},
            :other-things [1 2 3 "42"]}
           (#'cprop.core/merge* config (read-system-env))))))

(comment ;; playground

(conf :answer) ;; 42
(conf :source :account :rabbit :vhost) ;; "/z-broker"

(def rabbit 
  (cursor :source :account :rabbit))

(rabbit :vhost) ;; "/z-broker"

)
