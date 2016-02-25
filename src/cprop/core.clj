(ns cprop.core
  (:require [clojure.string :as s]
            [clojure.edn :as edn]
            [cprop.source :refer [from-file]]))

(defonce ^:private props (atom {}))

(defn conf [& path]                  ;; e.g. (conf :datomic :url)
  (get-in @props (vec path)))

(defn- env->path [v]
  (as-> v $
        (s/lower-case $)
        (s/replace $ "__" "-")
        (s/split $ #"_")
        (map keyword $)))

(defn- read-system-env []
  (->> (System/getenv)
       (map (fn [[k v]] [(env->path k) v]))
       (into {})))

(defn- typinize [k v]
  (try 
    (edn/read-string v)
    (catch Throwable problem
      (println "could not read value:" v "for key:" k "due to:" (.getMessage problem) ". casting it to string")
      (str v))))

;; merge with ENV, system props, etc..

(defn- substitute [m [k-path v]]
  (if (and (seq k-path) (get-in m k-path))
    (do
      ;; (println "substituting" (vec k-path) "with" v)
      (println "substituting" (vec k-path) "with a ENV/system.property specific value")
      (assoc-in m k-path (edn/read-string v)))
    m))

(defn- merge* [config with]
  (reduce substitute config with))

;; entry point

(defn load-config 
  ([] 
   (load-config (from-file)))
  ([config] 
   (let [env (read-system-env)
         sys-props {}]         ;; TODO system properties
     (reset! props (-> config
                       (merge* env))))))

;; cursors

(defn- create-cursor [path]
  (with-meta 
    (fn [& xs]
      (apply conf (concat path xs)))
    {:path path}))

(defn cursor [& path]
  (if-let [cr (meta (first path))]
    (create-cursor (flatten [(vals cr) (rest path)]))
    (create-cursor path)))
