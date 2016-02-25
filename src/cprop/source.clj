(ns cprop.source
  (:require [clojure.edn :as edn]
            [clojure.string :as s]
            [clojure.java.io :as io])
  (:import [java.util MissingResourceException]))

(defn- resource [path]
  (when path
    (or (-> (Thread/currentThread) 
            .getContextClassLoader 
            (.getResource path)) 
        path)))

;; TODO: find app name, if there, use "app-name.conf" instead of "config.edn"
(defn path-var 
  "system property cprop will look at to read the path to the app config. 
   for example 'java -Dconf=...'"
  []  
  "conf")

(defn from-file 
  ([]
   (from-file (System/getProperty (path-var))))
  ([path]
   (if (seq path)
     (try
       (edn/read-string 
         (slurp (io/file (resource path))))
       (catch Exception e 
         (throw (IllegalArgumentException. 
                  (str "a path to " (path-var) " \"" path "\" can't be found or have an invalid config (problem with the format?) " e)))))
     (throw (MissingResourceException. 
              (str "can't find a \"" (path-var) "\" env variable that points to a configuration file (usually in a form of -D" (path-var) "=<path>)")
              "" "")))))
