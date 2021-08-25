(ns cprop.source
  (:require [clojure.edn :as edn]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [cprop.tools :refer [contains-in? expand-home with-echo in-debug? str->num]])
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

(defn k->ns-str
  "takes:
    * ns-key: a regex on how to split a key to namespaced key
    * k:      a string that might represents a namespaced key

   and creates a namespaced string based on those splits: i.e.

   => (k->ns-str #\"___\" \"crux___foo___bar___db-spec\")
   \"crux/foo/bar/db-spec\""
  [ns-key k]
  (when k
    (let [parts (s/split k ns-key)]
      (if (= (count parts) 1)
        k
        (->> parts
             (s/join "/"))))))

(defn- k->path
  "parses the given key by splitting on `level` and replacing `dash` with `-`.

   options:
           :key-parse-fn - will be called on each level (part of the split key).
                           defaults to `keyword`.
           :to-ns        - will be called on each level (part of the split key).
                           defaults to `identity`."
  [k dash level {:keys [key-parse-fn
                        to-ns-key]
                 :or {key-parse-fn keyword
                      to-ns-key identity}}]
  (as-> k $
    (s/lower-case $)
    (s/split $ level)
    (map (comp key-parse-fn
               #(s/replace % dash "-")
               to-ns-key)
         $)))

(defn- str->value [v {:keys [as-is?]}]
  "ENV vars and system properties are strings which means that there are no types, but string.
   This results in some interesting corner cases. for example:

     is '0x42' a number or a string? you ask Clojure (or if it came from edn) it is a number 66
     but should this string be parsed as 66? what if this is username?

   str->value will convert:
     * numbers to longs
     * alphanumeric values to strings
     * true/false to boolean
     * and will use Clojure reader for the rest
   in case reader can't read OR it reads a symbol, the value will be returned as is (a string)"
  (cond
    as-is? v
    (re-matches #"[0-9]+" v) (str->num v)
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

(defn ^:private with-as-is
  [{:keys [as-is? as-is-paths] :as opts} path]
  (assoc opts :as-is? (or as-is?
                          (and as-is-paths
                               (as-is-paths path)))))

;; OS level ENV vars

(defn- env->path
  ([k]
   (env->path k {}))
  ([k opts]
   (let [dash    "_"
         level   #"(?<!_)_{2}(?!_)"   ;; matches _exactly_ 2 underscores to take as level (i.e. FOO__BAR to {:foo {:bar ..}})
         ns-key  #"(?<!_)_{3}(?!_)"]  ;; matches _exactly_ 3 underscores to take as a namespaced key (i.e. FOO___BAR to :foo/bar)
     (k->path k
              dash
              level
              (assoc opts
                     :to-ns-key (partial k->ns-str
                                         ns-key))))))

(defn- read-env-map
  [m opts]
  (->> m
       (map (fn [[k v]]
              (let [path        (env->path k opts)]
                [path (->> (with-as-is opts path)
                           (str->value v))])))
       (into {})))

(defn read-system-env
  ([]
   (read-system-env {}))
  ([opts]
   (read-env-map (System/getenv) opts)))

;; System properties

;; TODO: think about reversing it (k->path k "_" #"\.")
;; since this is usually the .properties structure
(defn- sysprop->path
  ([k]
   (sysprop->path k {}))
  ([k opts]
   (k->path k "." #"_" opts)))

(defn read-system-props
  ([]
   (read-system-props {}))
  ([opts]
   (->> (System/getProperties)
        (map (fn [[k v]]
               (let [path        (sysprop->path k opts)]
                 [path (->> (with-as-is opts path)
                            (str->value v))])))
        (into {}))))

;; .properties files

(defn- prop-key->path
  ([k]
   (prop-key->path k {}))
  ([k opts]
   (k->path k "_" #"\." opts)))

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
       (map (fn [[k v]]
              (let [prop-path   (prop-key->path k opts)]
                [prop-path (->> (with-as-is opts prop-path)
                                (str->value (if-not (false? parse-seqs?) ;; could be nil, which is true in this case
                                              (prop-seq v)
                                              v)))])))
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

(defn from-env-file
  "Load an env file from a file on the filesystem
  An env file is a file that contains environment variable.
  The syntax is the same as Docker env file."
  ([path]
   (from-env-file path {}))
  ([path opts]
   (-> (with-open [rdr (io/reader path)]
         (reduce (fn [m line]
                   ;; match all lines that are not starting with #
                   ;; capturing 2 groups :
                   ;;  - the key which is the sequence of characters until `=` is found
                   ;;  - the value which is the sequence of remaining characters (maybe empty) which is right of `=`
                   (if-let [[_ k v] (re-matches #"([^#][^=]+)=(.*)" line)]
                     (assoc m k v)
                     m))
                 {} (line-seq rdr)))
       (read-env-map opts)
       (sys->map))))

(defn ignore-missing-default
  "in case source is not given (i.e. is nil) and default source is missing, ignore the error, return an empty map"
  [f source]
  (if source
    (f source)  ;; if the source is given, don't ignore missing
    (try (f)
         (catch MissingResourceException mre
           {}))))
