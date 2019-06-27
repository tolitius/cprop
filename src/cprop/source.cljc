(ns cprop.source
  (:require [clojure.edn :as edn]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [cprop.tools :refer [contains-in? expand-home with-echo in-debug?]])
  (:import java.util.MissingResourceException
           java.io.PushbackReader
           java.io.StringReader
           java.util.Properties))

(defonce default-resource-name "config.edn")
(defonce path-prop "conf")

(defn read-config [input]
  (edn/read-string
    {:readers *data-readers*}
    (slurp input)))

(defn- k->path [k dash level]
  (as-> k $
        (s/lower-case $)
        (s/split $ level)
        (map (comp keyword
                   #(s/replace % dash "-"))
             $)))

(defn- str->value [v {:keys [as-is?]}]
  "ENV vars and system properties are strings. str->value will convert:
  the numbers to longs, the alphanumeric values to strings, and will use Clojure reader for the rest
  in case reader can't read OR it reads a symbol, the value will be returned as is (a string)"
  (cond
    as-is? v
    (re-matches #"[0-9]+" v) (Long/parseLong v)
    (re-matches #"^(true|false)$" v) (Boolean/parseBoolean v)
    (re-matches #"\w+" v) v
    :else
    (try
      (let [parsed (edn/read-string {:readers *data-readers*} v)]
        (if (symbol? parsed)
          v
          parsed))
         (catch Throwable _
           v))))

;; OS level ENV vars

(defn- env->path [k]
  (k->path k "_" #"__"))

(defn read-system-env
  ([]
   (read-system-env {}))
  ([opts]
   (->> (System/getenv)
        (map (fn [[k v]] [(env->path k)
                          (str->value v opts)]))
        (into {}))))

;; System properties

;; TODO: think about reversing it (k->path k "_" #"\.")
;; since this is usually the .properties structure
(defn- sysprop->path [k]
  (k->path k "." #"_"))

(defn read-system-props
  ([]
   (read-system-props {}))
  ([opts]
   (->> (System/getProperties)
        (map (fn [[k v]] [(sysprop->path k)
                          (str->value v opts)]))
        (into {}))))

;; .properties files

(defn- prop-key->path [k]
  (k->path k "_" #"\."))

(defn prop-seq [value]
  (let [xs (s/split value #",")]
    (if (> (count xs) 1)
      (str xs)
      value)))

(defn slurp-props-file
  "mutable Properties to immutable map"
  [path]
  (let [ps (Properties.)]
    (->> (StringReader. (slurp path))
         (.load ps))
    (into {} ps)))

(defn- read-props-file
  ([path]
   (read-props-file path {}))
  ([path {:keys [parse-seqs?] :as opts}]
  (->> (slurp-props-file path)
       (map (fn [[k v]] [(prop-key->path k)
                         (str->value (if-not (false? parse-seqs?) ;; could be nil, which is true in this case
                                       (prop-seq v)
                                       v)
                                     opts)]))
       (into {}))))


(defn- sys->map [sys]
  (reduce (fn [m [k-path v]]
            (assoc-in m k-path v)) {} sys))

;; merge existing configuration with ENV, system properties

(defn- substitute [m [k-path v]]
  (if (and (seq k-path) (contains-in? m k-path))
    (do
      (when (in-debug?)
        (println "substituting" (vec k-path) "with an ENV/System property specific value"))
      (assoc-in m k-path v))
    m))

(defn merge* [config with]
  (reduce substitute config with))

;; sources

(defn from-env
  ([]
   (from-env {}))
  ([opts]
   (sys->map (read-system-env opts))))

(defn from-system-props
  ([]
   (from-system-props {}))
  ([opts]
   (sys->map (read-system-props opts))))

(defn from-props-file
  ([path]
   (from-props-file path {}))
  ([path opts]
   (sys->map (read-props-file path opts))))

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
   (let [path (expand-home path)
         file (io/file path)]
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
