(ns cprop.tools
  #?(:clj  (:require [clojure.string :as s]
                     [cprop.logger :refer [log]])
     :cljs (:require [cprop.logger :refer [log]]))
  #?(:cljs (:require-macros [cprop.tools])))

#?(:clj
    (defmacro if-clj [then else]
      (if (-> &env :ns not)
        then
        else)))

#?(:clj
    (defmacro on-error [msg f]
      `(if-clj
         (try ~f
              (catch Throwable t#
                (throw (RuntimeException. ~msg t#))))
         (try ~f
              (catch :default t#
                (throw (~'str ~msg " " t#)))))))

#?(:clj
    (defmacro default-on-error [f default]
      `(if-clj
         (try ~f
              (catch Throwable t#
                ~default))
         (try ~f
              (catch :default t#
                ~default)))))

#?(:clj
    (defmacro throw-runtime [msg]
      `(throw (if-clj (RuntimeException. ~msg)
                      (~'str ~msg)))))

(defn key->prop [k]
  (-> k 
      name 
      (s/replace "-" "_")))

(defn key->env [k]
  (-> (key->prop k)
      (s/upper-case)))

(defn link [connect from [to value]]
  (let [to (key->prop to)]
    [(str from connect to) value]))

(defn- map->flat [m key->x connect]
  (reduce-kv (fn [path k v] 
               (if (map? v)
                 (concat (map (partial link connect (key->x k))
                              (map->flat v key->x connect))
                         path)
                 (conj path [(key->x k) v])))
             [] m))

(defn- map->props [m]
  (map->flat m key->prop "."))

(defn- map->env [m]
  (map->flat m key->env "__"))

(defn temp-file 
  ([fname] (temp-file fname ".tmp"))
  ([fname ext]
   (.getAbsolutePath 
     (java.io.File/createTempFile fname ext))))

(defn- map->x-file [{:keys [props-file] :as m} m->x prop->x]
  (let [fpath (apply str (or (seq props-file) 
                             (temp-file (str "cprops-" (System/currentTimeMillis) "-"))))
        mprops (dissoc m :props-file)]
    (spit fpath (reduce (fn [f prop]
                          (str f (prop->x prop) "\n")) 
                        "" (m->x mprops)))
    fpath))

(defn to-prop [[k v]]
  (str k "=" v))

(defn to-env [[k v]]
  (str "export " k "=" v))

(defn map->props-file [m]
  (map->x-file m map->props to-prop))

(defn map->env-file [m]
  (map->x-file m map->env to-env))


;; props to edn TBD


(defn contains-in?
  "checks whether the nested key exists in a map"
  [m k-path] 
  (let [one-before (get-in m (drop-last k-path))]
    (when (map? one-before)                        ;; in case k-path is "longer" than a map: {:a {:b {:c 42}}} => [:a :b :c :d]
      (contains? one-before (last k-path)))))

;; author of "deep-merge-with" is Chris Chouser: https://github.com/clojure/clojure-contrib/commit/19613025d233b5f445b1dd3460c4128f39218741
(defn deep-merge-with
  "Like merge-with, but merges maps recursively, appling the given fn
  only when there's a non-map at a particular level.
  (deepmerge + {:a {:b {:c 1 :d {:x 1 :y 2}} :e 3} :f 4}
               {:a {:b {:c 2 :d {:z 9} :z 3} :e 100}})
  -> {:a {:b {:z 3, :c 3, :d {:z 9, :x 1, :y 2}}, :e 103}, :f 4}"
  [f & maps]
  (apply
    (fn m [& maps]
      (if (every? map? maps)
        (apply merge-with m maps)
        (apply f maps)))
    maps))

(defn merge-maps [& m]
  (apply deep-merge-with (fn [_ v] v) m))

(defn cloak [m & paths]
  (reduce (fn [am path]
            (if (contains-in? am path)
              (update-in am path (fn [k] "*******"))
              am)) m paths))

(defn with-echo [config resource path]
  (if-not (empty? config)
    (log (str "read config from " resource ": \"" path "\""))
    (log (str "(!) read config from " resource ": \"" path "\", but it is empty")))
  config)
