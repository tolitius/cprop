(ns cprop.source
  (:require [clojure.edn :as edn]
            [clojure.string :as s]
            [clojure.java.io :as io])
  (:import java.util.MissingResourceException
           java.io.PushbackReader ))

(defonce default-resource-name "config.edn")
(defonce path-prop "conf")

(defn read-config [input]
  (edn/read-string
    (slurp input)))

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
    (re-matches #"^(true|false)$" v) (Boolean/parseBoolean v)
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

(defn read-system-env []
  (->> (System/getenv)
       (map (fn [[k v]] [(env->path k) 
                         (str->value v)]))
       (into {})))

;; System, application level properties

(defn- sysprop->path [k]
  (k->path k "." #"_"))

(defn read-system-props []
  (->> (System/getProperties)
       (map (fn [[k v]] [(sysprop->path k)
                         (str->value v)]))
       (into {})))

(defn- sys->map [sys]
  (reduce (fn [m [k-path v]]
            (assoc-in m k-path v)) {} sys))

;; merge existing configuration with ENV, system properties

(defn in-debug? []
  (when-let [debug (System/getenv "DEBUG")]
    (= (s/lower-case debug) "y")))

(defn- substitute [m [k-path v]]
  (if (and (seq k-path) (get-in m k-path))
    (do
      (when (in-debug?)
        (println "substituting" (vec k-path) "with an ENV/System property specific value"))
      (assoc-in m k-path v))
    m))

(defn merge* [config with]
  (reduce substitute config with))

;; sources

(defn from-env []
  (sys->map (read-system-env)))

(defn from-system-props []
  (sys->map (read-system-props)))

(defn from-stream
  "load configuration from a resource that can be coerced into an input-stream"
  [resource]
  ;; TODO would throw "FileNotFoundException" i.e. won't be just nil
  (if-let [stream (io/input-stream resource)]
    (try
      (read-config stream)
      (catch Throwable t
        (throw (IllegalArgumentException.
                 (str "the \"" resource "\" contains invalid config (problem with the format?) " t)))))
    (throw (MissingResourceException.
             (str "the \"" resource "\" could not be located") "" ""))))

(defn from-file
  "load configuration from a file on the filesystem"
  ([]
   (from-file (System/getProperty path-prop)))
  ([path]
   (let [file (io/file path)]
     (if (and file (.exists file))
       (try
         (read-config file)
         (catch Throwable t
           (throw (IllegalArgumentException.
                    (str "a path to " path-prop " \"" path "\" can't be found or have an invalid config (problem with the format?) " t)))))
       (throw (MissingResourceException.
                (str "can't find a configuration file path: \"" path "\". "
                     "besides providing it via \"(load-config :file <path>)\", "
                     "it could also be set via \"" path-prop "\" system property (i.e. -D" path-prop "=<path>)")
                "" ""))))))

(defn from-resource
  "load configuration from a resource relative to the classpath"
  ([]
   (from-resource default-resource-name))
  ([resource]
   (if-let [url (io/resource resource)]
     (try
       (with-open [r (-> url io/reader PushbackReader.)]
         (edn/read r))
       (catch Throwable t
         (throw (Throwable. (str "failed to parse \"" resource "\": " (.getLocalizedMessage t))))))
     (throw (MissingResourceException. (str "resource \"" resource "\" not found on the resource path") "" "")))))

(defn ignore-missing-default
  "in case source is not given (i.e. is nil) and default source is missing, ignore the error, return an empty map"
  [f source]
  (if source 
    (f source)  ;; if the source is given, don't ignore missing
    (try (f)
         (catch MissingResourceException mre
           {}))))
