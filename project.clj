(defproject cprop "0.1.3-SNAPSHOT"
  :description "where all configuration properties converge"
  :url "https://github.com/tolitius/cprop"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]]
            
  :profiles {:dev {:jvm-opts ["-Dconf=resources/config.edn"]}})
