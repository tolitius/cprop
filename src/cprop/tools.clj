(ns cprop.tools
  (:require [clojure.string :as s]))

(defn key->prop [k]
  (-> k 
      name 
      (s/replace "-" "_")))

(defn link [from [to value]]
  (let [to (key->prop to)]
    [(str from "." to) value]))

(defn map->props [m]
  (reduce-kv (fn [path k v] 
               (if (map? v)
                 (concat (map (partial link (key->prop k))
                              (map->props v))
                         path)
               (conj path [(key->prop k) v])))
  [] m))

(defn map->props-file [{:keys [props-file] :as m}]
  (let [fpath (apply str (or (seq props-file) 
                             (str "/tmp/cprops-"
                                  (System/currentTimeMillis)
                                  ".tmp.properties")))
        mprops (dissoc m :props-file)]
    (spit fpath (reduce (fn [f [k v]]
                          (str f k "=" v "\n")) 
                        "" (map->props mprops)))
    fpath))

;; props to edn

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
