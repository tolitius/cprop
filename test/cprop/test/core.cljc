(ns cprop.test.core
  (:require [cprop.core :refer [load-config cursor]]
            [cprop.source :refer [merge* from-stream from-file from-env-file from-resource from-props-file from-env from-system-props]]
            [cprop.tools :as tools]
            [clojure.string :as s]
            [clojure.edn :as edn]
            [clojure.pprint :as pp]
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

(defn- read-test-env [opts]
  (->> {"DATOMIC__URL" "\"datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic\""
        "AWS__ACCESS_KEY" "\"AKIAIOSFODNN7EXAMPLE\""
        "AWS__SECRET_KEY" "\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\""
        "AWS__REGION" "\"ues-east-1\""
        "IO__HTTP__POOL__CONN_TIMEOUT" "60000"
        "IO__HTTP__POOL__MAX_PER_ROUTE" "10"
        "OTHER_THINGS" "[1 2 3 \"42\"]"
        "SOME_BIG_INT" "10000000000000000000"
        "SOME_DATE" "7 Nov 22:44:53 2015"
        "CLUSTERS__0__URL" "http://somewhere"
        "CRUX__CRUX___DB_SPEC__DBNAME" "crux-db"
        "CRUX_AND_FRIENDS__CRUX___AND___FRIENDS___DB_SPEC__DBNAME" "crux-and-friends-db"
        "CRUX_IN_CROCS__CRUX_DOT_AND_DOT_FRIENDS___DB_SPEC__CRUX_DOT_JDBC___HOST" "jdbc-bc-jd"
        "CRUX_IN_CROCS__CRUX_DOT_AND_DOT_FRIENDS___DB_SPEC__CRUX_DOT_JDBC___USER" "robocop"
        "CRUX_IN_CROCS__CRUX_DOT_AND_DOT_FRIENDS___DB_SPEC__CRUX_DOT_JDBC___PASSWORD" "my friends call me Murphy"}
       (map (fn [[k v]] [(#'cprop.source/env->path k opts)
                         (#'cprop.source/str->value v opts)]))
       (into {})))

(deftest from-source
  (is (map? (from-stream "dev-resources/config.edn")))
  (is (map? (from-file "dev-resources/config.edn")))
  (is (map? (from-resource "config.edn")))
  (is (map? (from-env-file "dev-resources/.env")))
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
        merged (merge* config (read-test-env {}))
        merged-as-is (merge* config (read-test-env {:as-is? true}))
        merged-with-custom-parser (merge* config (read-test-env {:key-parse-fn tools/parse-num-keys}))]

    (testing "normal parsing"
      (is (= {:datomic {:url "datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic"},
              :aws {:access-key "AKIAIOSFODNN7EXAMPLE",
                    :secret-key "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                    :region "ues-east-1",
                    :visiblity-timeout-sec 30,
                    :max-conn 50,
                    :queue "cprop-dev"},
              :clusters [{:name "one",
                          :url nil}],
              :io
              {:http
               {:pool
                {:socket-timeout 600000,
                 :conn-timeout 60000,
                 :conn-req-timeout 600000,
                 :max-total 200,
                 :max-per-route 10}}},
              :other-things [1 2 3 "42"]
              :some-big-int 10000000000000000000N
              ;; incorrectly parsed substitution (i.e. should have been "7 Nov 22:44:53 2015")
              ;; next assertion corrects that
              :some-date 7}
             merged)))

    (testing "as-is parsing (i.e. parsed all params as strings)"
      (is (= {:datomic {:url "\"datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic\""},
              :aws {:access-key "\"AKIAIOSFODNN7EXAMPLE\"",
                    :secret-key "\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"",
                    :region "\"ues-east-1\"",
                    :visiblity-timeout-sec 30, ;; the non strings come directly from internal config
                    :max-conn 50,
                    :queue "cprop-dev"},
              :clusters [{:name "one",
                          :url nil}],
              :io
              {:http
               {:pool
                {:socket-timeout 600000,
                 :conn-timeout "60000",
                 :conn-req-timeout 600000,
                 :max-total 200,
                 :max-per-route "10"}}},
              :other-things "[1 2 3 \"42\"]"
              :some-big-int "10000000000000000000"
              :some-date "7 Nov 22:44:53 2015"}

             merged-as-is)))

    (testing "custom key-path parsing"
      (is (= {:datomic {:url "datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic"},
              :aws {:access-key "AKIAIOSFODNN7EXAMPLE",
                    :secret-key "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                    :region "ues-east-1",
                    :visiblity-timeout-sec 30,
                    :max-conn 50,
                    :queue "cprop-dev"},
              :clusters [{:name "one",
                          :url "http://somewhere"}],
              :io
              {:http
               {:pool
                {:socket-timeout 600000,
                 :conn-timeout 60000,
                 :conn-req-timeout 600000,
                 :max-total 200,
                 :max-per-route 10}}},
              :other-things [1 2 3 "42"]
              :some-big-int 10000000000000000000N
              ;; incorrectly parsed substitution (i.e. should have been "7 Nov 22:44:53 2015")
              ;; next assertion corrects that
              :some-date 7}
             merged-with-custom-parser)))))

(deftest should-merge-with-sys-props
  (let [props {"datomic_url" "sys-url"
               "aws_access.key" "0x424242"
               "io_http_pool_socket.timeout" "4242"}
        _      (doseq [[k v] props] (System/setProperty k v))
        config (load-config :resource "fill-me-in.edn"
                            :file "dev-resources/fill-me-in.edn")]

    (is (= {:datomic {:url "sys-url"},
            :aws
            {:access-key "0x424242",
             :secret-key "ME TOO",
             :region "FILL ME IN AS WELL",
             :visiblity-timeout-sec 30,
             :max-conn 50,
             :queue "cprop-dev"},
            :clusters [{:name "one",
                        :url nil}],
            :io
            {:http
             {:pool
              {:socket-timeout 4242,
               :conn-timeout :I-SHOULD-BE-A-NUMBER,
               :conn-req-timeout 600000,
               :max-total 200,
               :max-per-route :ME-ALSO}}},
            :other-things ["I am a vector and also like to place the substitute game"]
            :some-big-int :I-SHOULD-BE-A-BIG-INT
            :some-date "so/me/date"}

           config))

    (doseq [[k _] props] (System/clearProperty k))))

(deftest should-merge-with-props-file
  (let [config (load-config :file "dev-resources/fill-me-in.edn"
                            :merge [(from-props-file "dev-resources/overrides.properties")])]

    (is (= {:datomic
            {:url "datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic"},
            :source
            {:account
             {:rabbit
              {:host "localhost",
               :port 5672,
               :vhost "/z-broker",
               :username "guest",
               :password "guest"}}},
            :answer 42,
            :aws
            {:access-key "super secret key",
             :secret-key "super secret s3cr3t!!!",
             :region "us-east-2",
             :visiblity-timeout-sec 30,
             :max-conn 50,
             :queue "cprop-dev"},
            :clusters [{:name "one",
                        :url nil}],
            :database {:aname {:password "shh dont tell"}}
            :io
            {:http
             {:pool
              {:socket-timeout 600000,
               :conn-timeout 42,
               :conn-req-timeout 600000,
               :max-total 200,
               :max-per-route 42}}},
            :other-things ["1" "2" "3" "4" "5" "6" "7"]
            :some-big-int :I-SHOULD-BE-A-BIG-INT
            :some-date "so/me/date"}

           config))))

(deftest should-merge-booleans
  (let [props {"datomic_url" "true"
               "aws_access.key" "false"
               "io_http_pool_socket.timeout" "True"
               "io_http_pool_conn.timeout" ".true"
               "io_http_pool_max.total" "truee"}
        _      (doseq [[k v] props] (System/setProperty k v))
        config (load-config :resource "fill-me-in.edn"
                            :file "dev-resources/fill-me-in.edn")]
    (is (true? (get-in config [:datomic :url])))
    (is (false? (get-in config [:aws :access-key])))
    (is (string? (get-in config [:io :http :pool :socket-timeout])))
    (is (string? (get-in config [:io :http :pool :conn-timeout])))
    (is (string? (get-in config [:io :http :pool :max-total])))
    (doseq [[k _] props] (System/clearProperty k))))

(deftest should-read-system-props []
  (let [ps        {"datomic_url" "sys-url"
                   "aws_access.key" "sys-key"
                   "io_http_pool_socket.timeout" "4242"
                   "kafka_producer_linger.ms" "10"
                   "kafka_producer_buffer.size" "65536"
                   "database_aname_password" "shh, don't tell"}
        _            (doseq [[k v] ps]
                       (System/setProperty k v))
        props        (from-system-props)
        props-as-is  (from-system-props {:as-is? true})
        props-as-is-path  (from-system-props {:as-is-paths #{[:io :http :pool :socket-timeout]
                                                             [:kafka :producer :linger-ms]}})
        props-with-custom-parser (from-system-props
                                  {:key-parse-fn #(case %
                                                "aname" :parsed
                                                (keyword %))})]
    (testing "normal parsing"
      (is (= {:http {:pool {:socket-timeout 4242}}}
             (props :io)))
      (is (= {:producer {:linger-ms 10
                        :buffer-size 65536}}
             (props :kafka)))
      (is (= {:access-key "sys-key"}
             (props :aws)))
      (is (= {:url "sys-url"}
             (props :datomic)))
      (is (= {:aname {:password "shh, don't tell"}}
             (props :database))))

    (testing "as-is: i.e. parse as strings"
      (is (= {:http {:pool {:socket-timeout "4242"}}}
             (props-as-is :io)))
      (is (= {:producer {:linger-ms "10"
                         :buffer-size "65536"}}
             (props-as-is :kafka)))
      (is (= {:access-key "sys-key"}
             (props-as-is :aws)))
      (is (= {:url "sys-url"}
             (props-as-is :datomic)))
      (is (= {:aname {:password "shh, don't tell"}}
             (props-as-is :database))))

    (testing "as-is-paths: i.e. parse as strings only the given path"
      (is (= {:http {:pool {:socket-timeout "4242"}}}
             (props-as-is-path :io)))
      (is (= {:producer {:linger-ms "10"
                           :buffer-size 65536}}
             (props-as-is-path :kafka)))
      (is (= {:access-key "sys-key"}
               (props-as-is-path :aws)))
      (is (= {:url "sys-url"}
               (props-as-is-path :datomic)))
      (is (= {:aname {:password "shh, don't tell"}}
               (props-as-is-path :database))))

    (testing "custom key-path parsing"
      (is (= {:http {:pool {:socket-timeout 4242}}}
             (props-with-custom-parser :io)))
      (is (= {:producer {:linger-ms 10
                         :buffer-size 65536}}
             (props-with-custom-parser :kafka)))
      (is (= {:access-key "sys-key"}
             (props-with-custom-parser :aws)))
      (is (= {:url "sys-url"}
             (props-with-custom-parser :datomic)))
      (is (= {:parsed {:password "shh, don't tell"}}
             (props-with-custom-parser :database))))

    (doseq [[k _] ps]
      (System/clearProperty k))))

(deftest should-read-from-props-file []
  (let [ps (from-props-file "dev-resources/overrides.properties")
        ps-as-is (from-props-file "dev-resources/overrides.properties" {:as-is? true})
        ps-as-is-path (from-props-file "dev-resources/overrides.properties" {:as-is-paths #{'(:other-things)}})
        ps-with-custom-parser (from-props-file "dev-resources/overrides.properties"
                                               {:key-parse-fn #(case %
                                                             "aname" :parsed
                                                             (keyword %))})]

    (testing "normal parsing"
      (is (= {:aws
              {:region "us-east-2",
               :secret-key "super secret s3cr3t!!!",
               :access-key "super secret key"},
              :other-things ["1" "2" "3" "4" "5" "6" "7"],
              :io {:http {:pool {:conn-timeout 42, :max-per-route 42}}},
              :database {:aname {:password "shh dont tell"}},
              :source {:account {:rabbit {:host "localhost"}}},
              :datomic
              {:url
               "datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic"}}
             ps)))

    (testing "as-is: i.e. read all prop values as strings"
      (is (= {:aws
              {:region "us-east-2",
               :secret-key "super secret s3cr3t!!!",
               :access-key "super secret key"},
              :other-things "[\"1\" \"2\" \"3\" \"4\" \"5\" \"6\" \"7\"]",
              :io {:http {:pool {:conn-timeout "42", :max-per-route "42"}}},
              :database {:aname {:password "shh dont tell"}},
              :source {:account {:rabbit {:host "localhost"}}},
              :datomic
              {:url
               "datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic"}}
             ps-as-is)))

    (testing "as-is-path: i.e. read prop values as strings only for given path, normal parsing for others"
        (is (= {:aws
                              {:region "us-east-2",
                               :secret-key "super secret s3cr3t!!!",
                               :access-key "super secret key"},
                :other-things "[\"1\" \"2\" \"3\" \"4\" \"5\" \"6\" \"7\"]",
                :io {:http {:pool {:conn-timeout 42, :max-per-route 42}}},
                :database {:aname {:password "shh dont tell"}},
                :source {:account {:rabbit {:host "localhost"}}},
                :datomic
                              {:url
                               "datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic"}}
               ps-as-is-path)))

    (testing "custom key-path parsing"
      (is (= {:aws
              {:region "us-east-2",
               :secret-key "super secret s3cr3t!!!",
               :access-key "super secret key"},
              :other-things ["1" "2" "3" "4" "5" "6" "7"],
              :io {:http {:pool {:conn-timeout 42, :max-per-route 42}}},
              :database {:parsed {:password "shh dont tell"}},
              :source {:account {:rabbit {:host "localhost"}}},
              :datomic
              {:url
               "datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic"}}
             ps-with-custom-parser)))))

(deftest should-throw-on-file-not-found
  (is (thrown-with-msg? java.util.MissingResourceException
                        #"can't find a configuration file path: \"not-here\". besides providing it via \"\(load-config :file <path>\)\", it could also be set via \"conf\" system property \(i.e. -Dconf=<path>\)"
                        (load-config :resource "empty.edn" :file "not-here"))))

(deftest should-throw-when-empty-config
  (is (thrown-with-msg? java.lang.RuntimeException
                        #"could not find a non empty configuration file to load. looked in the classpath \(as a \"resource\"\) and on a file system via \"conf\" system property"
                        (load-config :resource "empty.edn" :file "dev-resources/empty.edn"))))

(deftest should-ignore-top-level-nils-on-merge
  (testing "should ignore top level nils with merging config with other sources"
    (let [with-vector-nil (load-config :merge [nil])
          with-nil (load-config :merge nil)
          with-nil-and-friends (load-config :merge [nil {} nil {} {}])
          c (load-config)]
      (is (= c
             with-nil
             with-vector-nil
             with-nil-and-friends)))))

(deftest should-parse-env-namespaced-keys
  (let [config (edn/read-string
                 (slurp "dev-resources/with-namespaced-keys.edn"))
        merged (merge* config (read-test-env {}))]

    (testing "should replace namespaced keys with ENV values"
      (is (= {:crux {:crux/db-spec
                     {:dbname "crux-db"
                      :dbtype "postgresql"}}
              :crux-and-friends {:crux/and/friends/db-spec
                                 {:dbname "crux-and-friends-db"
                                  :dbtype "postgresql"}}}
             merged)))))

(deftest should-parse-dotted-namespaced-keys
  "custom custom parse a dotted key
   since env variables have nothing but underscore to work with.
   we can use anything that suits our use case (say: _DOT_) to represent a '.' for example.

   i.e. 'FOO_DOT_BAR___BAZ' env key is parsed as 'foo.bar/baz'"

  (let [config (edn/read-string
                 (slurp "dev-resources/with-dotted-keys.edn"))
        env (read-test-env {:key-parse-fn (fn [k]                        ;; <= custom key parse function
                                            (-> k
                                                (s/replace #"-dot-" ".")
                                                keyword))})
        merged (merge* config env)]

    (testing "should replace namespaced dotted keys with ENV values"
      (is (= {:crux-in-crocs {:crux.and.friends/db-spec
                              {:crux.node/topology '[cprop.tools/with-echo]
                               :crux.jdbc/dbtype "postgresql"
                               :crux.jdbc/dbname "cruxdb"
                               :crux.jdbc/host "jdbc-bc-jd"
                               :crux.jdbc/user "robocop"
                               :crux.jdbc/password "my friends call me Murphy"}}}
             merged)))))

(deftest should-read-from-env-file
  (let [expected {:dotted.key "dotted.key"
                  :hyphen-key "hyphen-key"
                  :namspaced/key "namespaced/key"
                  :simple "simple"
                  :empty-key ""
                  :super {:nested {:key "super nested key"}}}]
    (is (= expected
           (select-keys (load-config :resource "config.edn"
                                     :merge [(from-env-file "dev-resources/.env")])
                        (keys expected))))))
