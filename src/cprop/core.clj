(ns cprop.core
  (:require [clojure.string :as s]
            [clojure.edn :as edn]
            [cprop.tools :refer [merge-maps]]
            [cprop.source :refer [from-resource from-file ignore-missing-default]]))

(defn- k->path [k dash level]
  (as-> k $
        (s/lower-case $)
        (s/split $ level)
        (map (comp keyword
                   #(s/replace % dash "-"))
             $)))

(defn- str->value [v]
  "ENV vars and system properties are strings. str->value will convert:
  the numbers to longs, the alphanumeric values to strings, and will use Clojure reader for the rest
  in case reader can't read OR it reads a symbol, the value will be returned as is (a string)"
  (cond
    (re-matches #"[0-9]+" v) (Long/parseLong v)
    (re-matches #"\w+" v) v
    :else
    (try 
      (let [parsed (edn/read-string v)]
        (if (symbol? parsed)
          v
          parsed))
         (catch Throwable _
           v))))

;; OS level ENV vars

(defn- env->path [k]
  (k->path k "_" #"__"))

(defn- read-system-env []
  (->> (System/getenv)
       (map (fn [[k v]] [(env->path k) 
                         (str->value v)]))
       (into {})))

;; System, application level properties

(defn- sysprop->path [k]
  (k->path k "." #"_"))

(defn- read-system-props []
  (->> (System/getProperties)
       (map (fn [[k v]] [(sysprop->path k)
                         (str->value v)]))
       (into {})))

;; merge ENV and system properties

(defn- substitute [m [k-path v]]
  (if (and (seq k-path) (get-in m k-path))
    (do
      (println "substituting" (vec k-path) "with an ENV/System property specific value")
      (assoc-in m k-path v))
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
            (merge* $ (read-system-props))
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
