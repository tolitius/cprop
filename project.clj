(defproject cprop "0.1.0-SNAPSHOT"
  :description "where all configuration properties converge"
  :url "https://github.com/tolitius/cprop"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]]
            
  :source-paths ["src" "src/cprop"]
            
  :profiles {:dev {:jvm-opts ["-Dconfig.edn=resources/config.edn"]}})
