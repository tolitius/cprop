(ns cprop.test.core
  (:require [cprop.core :refer [load-config cursor]]
            [cprop.source :refer [merge* from-stream from-file from-resource]]
            [clojure.edn :as edn]
            [clojure.test :refer :all]))

(deftest should-slurp-and-provide
  (testing "should read config from -Dconfig.var"
    (let [c (load-config)]
      (is (= (c :answer) 42))))
  (testing "should be able to naviage nested props"
    (let [c (load-config)]
      (is (= (get-in c [:source :account :rabbit :vhost]) "/z-broker")))))

(deftest should-create-cursors
  (testing "should create a rabbit cursor"
    (let [c (load-config)]
      (is (= ((cursor c :source :account :rabbit) :vhost) "/z-broker"))
      (is (= ((cursor c)) c)))))

(deftest should-compose-cursors
  (testing "should compose one level"
    (let [c (load-config)]
      (is (= ((cursor c (cursor c :source) :account) :rabbit :vhost) "/z-broker"))
      (is (= ((cursor c (cursor c) :source :account) :rabbit :vhost) "/z-broker"))))
  (testing "should compose nested cursors"
    (let [c (load-config)]
      (is (= ((cursor c (cursor c (cursor c :source) :account) :rabbit) :vhost) "/z-broker")))))

(defn- read-test-env []
  (->> {"DATOMIC__URL" "\"datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic\""
        "AWS__ACCESS_KEY" "\"AKIAIOSFODNN7EXAMPLE\""
        "AWS__SECRET_KEY" "\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\""
        "AWS__REGION" "\"ues-east-1\""
        "IO__HTTP__POOL__CONN_TIMEOUT" "60000"
        "IO__HTTP__POOL__MAX_PER_ROUTE" "10"
        "OTHER_THINGS" "[1 2 3 \"42\"]"}
       (map (fn [[k v]] [(#'cprop.source/env->path k)
                         (#'cprop.source/str->value v)]))
       (into {})))

(deftest from-source
  (is (map? (from-stream "dev-resources/config.edn")))
  (is (map? (from-file "dev-resources/config.edn")))
  (is (map? (from-resource "config.edn")))
  (is (map? (load-config :file "dev-resources/config.edn")))
  (is (map? (load-config :resource "config.edn")))
  (is (map? (load-config :resource "config.edn"
                         :file "dev-resources/fill-me-in.edn"))))

(deftest with-merge
  (is (= (load-config :resource "config.edn" 
                      :merge [{:source {:account {:rabbit {:port 4242}}}}])
         (assoc-in (load-config) [:source :account :rabbit :port] 4242)))
  (is (= (load-config :file "dev-resources/config.edn" 
                      :merge [{:source {:account {:rabbit {:port 4242}}}}
                              {:datomic {:url :foo}}])
         (assoc-in (assoc-in (load-config) [:source :account :rabbit :port] 4242)
                   [:datomic :url] :foo)))
  (is (= (load-config :resource "config.edn"
                      :file "dev-resources/config.edn"
                      :merge [{:source {:account {:rabbit {:port 4242}}}}
                              {:datomic {:url :foo}}
                              {:datomic {:url :none}}])
         (assoc-in (assoc-in (load-config) [:source :account :rabbit :port] 4242)
                   [:datomic :url] :none))))

(deftest should-merge-with-env
  (let [config (edn/read-string
                 (slurp "dev-resources/fill-me-in.edn"))
        merged (merge* config (read-test-env))]

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

           merged))))

(deftest should-merge-with-sys-props
  (let [props {"datomic_url" "sys-url"
               "aws_access.key" "sys-key"
               "io_http_pool_socket.timeout" "4242"}
        _      (doseq [[k v] props] (System/setProperty k v))
        config (load-config :resource "fill-me-in.edn"
                            :file "dev-resources/fill-me-in.edn")]

    (is (= {:datomic {:url "sys-url"},
            :aws
            {:access-key "sys-key",
             :secret-key "ME TOO",
             :region "FILL ME IN AS WELL",
             :visiblity-timeout-sec 30,
             :max-conn 50,
             :queue "cprop-dev"},
            :io
            {:http
             {:pool
              {:socket-timeout 4242,
               :conn-timeout :I-SHOULD-BE-A-NUMBER,
               :conn-req-timeout 600000,
               :max-total 200,
               :max-per-route :ME-ALSO}}},
            :other-things
            ["I am a vector and also like to place the substitute game"]}

           config))

    (doseq [[k _] props] (System/clearProperty k))))
