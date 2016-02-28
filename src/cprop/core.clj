(ns cprop.core
  (:require [clojure.string :as s]
            [clojure.edn :as edn]
            [cprop.tools :refer [merge-maps]]
            [cprop.source :refer [from-resource from-file ignore-missing-default]]))

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

;; (load-config :resource "a/b/c.edn"
;;              :file "/path/to/file.edn"
;;              :merge [{} {} {} ..])
(defn load-config [& {:keys [file resource merge]
                      :or {merge []}}]
  (let [config (merge-maps (ignore-missing-default from-resource resource)
                           (ignore-missing-default from-file file))]
    (if (not-empty config)
      (as-> config $
            (apply merge-maps (cons $ merge))
            (merge* $ (read-system-env)))
      (throw (RuntimeException. (str "could not find a configuration file to load. "
                                     "looked in the classpath (as a \"resource\") "
                                     "and on a file system via \"conf\" system property"))))))

;; cursors

(defn- config? [c]
  (map? c))

(defn- get-in* [conf & path]
  (get-in conf (vec path)))

(defn- create-cursor [conf path]
  (with-meta
    (fn [& xs]
      (apply (partial get-in* conf)
             (concat path xs)))
    {:path (or path [])}))

(defn cursor [conf & path]
  (if (config? conf)
    (if-let [cpath (-> path first meta :path)]        ;; is "(first path)" a cursor?
      (create-cursor conf (concat cpath (rest path)))
      (create-cursor conf path))
    (throw (RuntimeException. (str "the first argument should be the config itself, but instead it is: '" conf "'")))))
