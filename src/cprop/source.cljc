(ns cprop.source
  #?(:clj
      (:require [clojure.edn :as edn]
                [clojure.string :as s]
                [clojure.java.io :as io]
                [cprop.logger :refer [log]]
                [cprop.tools :refer [on-error default-on-error contains-in? with-echo]]))
  #?(:clj
      (:import java.util.MissingResourceException
               java.io.PushbackReader))
  #?(:cljs
      (:require [cljs.clojure.string :as s]
                [cljs.reader :as edn]
                [cprop.tools :refer [on-error default-on-error contains-in? with-echo]]
                [cprop.logger :refer [log]])))

(defonce default-resource-name "config.edn")
(defonce path-prop "conf")

#?(:clj
    (defn read-config [input]
      (edn/read-string
        (slurp input)))

   :cljs
    (defn read-config [read-resource path] ;; reas-resource is a JS runtime specific fn that slurps a file
      (edn/read-string
        (read-resource path))))

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
    (re-matches #"[0-9]+" v) #?(:clj (Long/parseLong v)
                                :cljs (js/parseInt v))
    (re-matches #"^(true|false)$" v) #?(:clj (Boolean/parseBoolean v)
                                        :cljs (js/eval v))
    (re-matches #"\w+" v) v
    :else
    (default-on-error (let [parsed (edn/read-string v)]
                        (if (symbol? parsed)
                          v
                          parsed))
                      v)))

;; OS level ENV vars

#?(:clj
    (defn system-getenv 
      ([] (System/getenv))
      ([v]
       (System/getenv v)))

    :cljs
    (defn system-getenv
      ([] 
       ;; (log "a function to read env vars was not provided: env vars were not read/used")
       {})
      ([v] "")))

#?(:clj
   (defn system-getprops []
     (System/getProperties))
   
   :cljs
   (defn system-getprops []
     ;; (log "a function to read system properties was not provided: system properties were not read/used")
     {}))

(defn- env->path [k]
  (k->path k "_" #"__"))

(defn read-system-env [get-env]
  (->> (get-env)
       (map (fn [[k v]] [(env->path k) 
                         (str->value v)]))
       (into {})))

;; System, application level properties

(defn- sysprop->path [k]
  (k->path k "." #"_"))

(defn read-system-props [get-props]
  (->> (get-props)
       (map (fn [[k v]] [(sysprop->path k)
                         (str->value v)]))
       (into {})))

(defn- sys->map [sys]
  (reduce (fn [m [k-path v]]
            (assoc-in m k-path v)) {} sys))

;; merge existing configuration with ENV, system properties

(defn in-debug? [get-env]
  (when-let [debug (get-env "DEBUG")]
    (= (s/lower-case debug) "y")))

(defn- substitute [m [k-path v]]
  (if (and (seq k-path) (contains-in? m k-path))
    (do
      (when (in-debug? system-getenv)
        (log "substituting" (vec k-path) "with an ENV/System property specific value"))
      (assoc-in m k-path v))
    m))

(defn merge* [config with]
  (reduce substitute config with))

;; sources

(defn from-env 
  ([] (from-env system-getenv))
  ([get-env]
   (sys->map (read-system-env get-env))))

(defn from-system-props
  ([] (from-system-props system-getprops))
  ([get-props]
   (sys->map (read-system-props get-props))))

(defn from-stream
  "load configuration from a resource that can be coerced into an input-stream"
  [resource]
  ;; TODO would throw "FileNotFoundException" i.e. won't be just nil
  (if-let [stream (io/input-stream resource)]
    (try
      (with-echo (read-config stream) "stream" resource)
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
         (with-echo (read-config file) "file" path)
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
   (if-let [url (when (seq resource)
                  (io/resource resource))]
     (try
       (-> (edn/read-string {:readers *data-readers*} (slurp url))
           (with-echo "resource" resource))
       (catch Throwable t
         (throw (Throwable. (str "failed to parse \"" resource "\": ") t))))
     (throw (MissingResourceException. (str "resource \"" resource "\" not found on the resource path") "" "")))))

(defn ignore-missing-default
  "in case source is not given (i.e. is nil) and default source is missing, ignore the error, return an empty map"
  [f source]
  (if source 
    (f source)  ;; if the source is given, don't ignore missing
    (try (f)
         (catch MissingResourceException mre
           {}))))
