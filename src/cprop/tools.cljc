(ns cprop.tools
  (:require [clojure.string :as s]
            [clojure.java.io :as io])
  (:import java.io.File
           java.io.StringReader
           java.util.Properties))

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

(defn- map->x-file [m m->x prop->x {:keys [path create?]
                                    :or {path (temp-file (str "cprops-" (System/currentTimeMillis) "-"))
                                         create? true}}]
  (let [fpath (apply str path)
        x-file (reduce (fn [f prop]
                         (str f (prop->x prop) "\n"))
                       "" (m->x m))]
    (if create?
      (do
        (spit fpath x-file)
        fpath)
      x-file)))

(defn to-prop [[k v]]
  (str k "=" v))

(defn to-env [[k v]]
  (str "export " k "=" v))

(defn map->props-file
  ([m]
   (map->props-file m {}))
  ([m opts]
   (map->x-file m map->props to-prop opts)))

(defn map->env-file
  ([m]
   (map->env-file m {}))
  ([m opts]
   (map->x-file m map->env to-env opts)))

(defn map->properties
  "convert map to java.util.Properties preserving hierarchy"
  [m]
  (let [ps (Properties.)]
    (->> (StringReader. (map->props-file m {:create? false}))
         (.load ps))
    ps))

(defn contains-in?
  "Checks whether `path` exists within `m`.

  An empty path always returns true which is akin to the behavior of `get-in`."
  [m [first & rest :as path]]
  (if (empty? path)
    true
    (and (contains? m first) (contains-in? (get m first) rest))))


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

(defn merge-maps
  "remove top level nils and call out to deep-merge-with
   with a merge function that given two values where one of them is not map
   would use the latest (the one on the right) as a 'merged' value."
  [& m]
  (->> (remove nil? m)
       (apply deep-merge-with (fn [_ v] v))))

(defn cloak [m & paths]
  (reduce (fn [am path]
            (if (contains-in? am path)
              (update-in am path (fn [k] "*******"))
              am)) m paths))

(defn in-debug? []
  (when-let [debug (System/getenv "DEBUG")]
    (= (s/lower-case debug) "y")))

(defn with-echo [config resource path]
  (when (in-debug?)
    (if-not (empty? config)
      (println (str "read config from " resource ": \"" path "\""))
      (println (str "(!) read config from " resource ": \"" path "\", but it is empty"))))
  config)

;; "home" and "expand-home" functions belong to https://github.com/Raynes/fs

(let [homedir (io/file (System/getProperty "user.home"))
      usersdir (.getParent homedir)]
  (defn home
    "With no arguments, returns the current value of the `user.home` system
     property. If a `user` is passed, returns that user's home directory. It
     is naively assumed to be a directory with the same name as the `user`
     located relative to the parent of the current value of `user.home`."
    ([] homedir)
    ([user] (if (empty? user) homedir (io/file usersdir user)))))

(defn expand-home
  "If `path` begins with a tilde (`~`), expand the tilde to the value
  of the `user.home` system property. If the `path` begins with a
  tilde immediately followed by some characters, they are assumed to
  be a username. This is expanded to the path to that user's home
  directory. This is (naively) assumed to be a directory with the same
  name as the user relative to the parent of the current value of
  `user.home`."
  [path]
  (let [path (str path)]
    (if (.startsWith path "~")
      (let [sep (.indexOf path File/separator)]
        (if (neg? sep)
          (home (subs path 1))
          (io/file (home (subs path 1 sep)) (subs path (inc sep)))))
      (io/file path))))

;; flatten-keys* belongs to Jay Fields: http://blog.jayfields.com/2010/09/clojure-flatten-keys.html

(defn- flatten-keys* [a ks m]
  (if (map? m)
    (reduce into (map (fn [[k v]]
                        (flatten-keys* a (conj ks k) v))
                      (seq m)))
    (assoc a ks m)))

(defn flatten-keys [m]
  (when (seq m)
    (flatten-keys* {} [] m)))

(defn str->num [s]
  "Convert numeric string into `java.lang.Long` or `clojure.lang.BigInt`"
  (try
    (Long/parseLong s)
    (catch NumberFormatException _
      (bigint s))))

(defn parse-num-keys
  "Key-part parser that parses keywords and integerss"
  [part]
  (if (re-matches #"\d+" part)
    (str->num part)
    (keyword part)))
