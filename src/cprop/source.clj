(ns cprop.source
  (:require [clojure.edn :as edn]
            [clojure.string :as s]
            [clojure.java.io :as io])
  (:import java.util.MissingResourceException
           java.io.PushbackReader ))

(defonce default-resource-name "config.edn")

;; TODO: find app name, if there, use "app-name.conf" instead of "config.edn"
(defn path-var
  "system property cprop will look at to read the path to the app config.
  for example 'java -Dconf=...'"
  []
  "conf")

(defn read-config [input]
  (edn/read-string
    (slurp input)))

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
   (from-file (System/getProperty (path-var))))
  ([path]
   (let [file (io/file path)]
     (if (and file (.exists file))
       (try
         (read-config file)
         (catch Throwable t
           (throw (IllegalArgumentException.
                    (str "a path to " (path-var) " \"" path "\" can't be found or have an invalid config (problem with the format?) " t)))))
       (throw (MissingResourceException.
                (str "can't find a configuration file path: \"" path "\". "
                     "besides providing it via \"(load-config :file <path>)\", "
                     "it could also be set via \"" (path-var) "\" system property (i.e. -D" (path-var) "=<path>)")
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
