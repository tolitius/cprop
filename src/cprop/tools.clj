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
