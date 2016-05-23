(def +version+ "0.1.9-SNAPSHOT")

(set-env!
  :source-paths #{"src"}
  :dependencies '[[org.clojure/clojure      "1.8.0"]

                  ;; boot clj
                  [boot/core                "2.5.1"           :scope "provided"]
                  [adzerk/bootlaces         "0.1.13"          :scope "test"]
                  [adzerk/boot-test         "1.0.6"           :scope "test"]
                  [tolitius/boot-check      "0.1.1"           :scope "test"]])

(require '[adzerk.bootlaces :refer :all]
         '[tolitius.boot-check :as check]
         '[adzerk.boot-test :as bt])

(bootlaces! +version+)

(defn uber-env []
  (set-env! :source-paths #(conj % "test"))
  (set-env! :resource-paths #(conj % "dev-resources"))
  (System/setProperty "conf" "dev-resources/config.edn"))

(deftask dev []
  (uber-env)
  (repl))

(deftask test []
  (uber-env)
  (bt/test))

(deftask check-sources []
  (comp
    (check/with-bikeshed)
    (check/with-eastwood)
    (check/with-yagni)
    (check/with-kibit)))

(task-options!
  push {:ensure-branch nil}
  pom {:project     'cprop
       :version     +version+
       :description "where all configuration properties converge"
       :url         "https://github.com/tolitius/cprop"
       :scm         {:url "https://github.com/tolitius/cprop"}
       :license     {"Eclipse Public License"
                     "http://www.eclipse.org/legal/epl-v10.html"}})
