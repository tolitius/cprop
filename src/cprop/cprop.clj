(ns cprop
  (:require [clojure.edn :as edn]
            [clojure.string :as s]
            [clojure.java.io :as io])
  (:import [java.util MissingResourceException]))

(defn resource [path]
  (when path
    (or (-> (Thread/currentThread) 
            .getContextClassLoader 
            (.getResource path)) 
        path)))

(defn conf-name []  ;; TODO: find app name, if there, use "app-name.conf" instead of "config.edn"
  "config.edn")

(defonce props
  (let [c-path (conf-name)]
    (if-let [path (System/getProperty c-path)]
      (try
        (edn/read-string 
          (slurp (io/file (resource path))))
        (catch Exception e 
          (throw (IllegalArgumentException. 
                   (str "a path to " c-path " \"" path "\" can't be found or have an invalid config (problem with the format?) " e)))))
      (throw (MissingResourceException. 
              (str "can't find a \"" c-path "\" env variable that points to a configuration file (usually in a form of -D" c-path "=<path>)")
              "" "")))))

(defn conf [& path]                  ;; e.g. (conf :datomic :url)
  (get-in props (vec path)))

;; (defn confa-x [& ps]
;;   (apply conf (concat [:x :y] ps)))
