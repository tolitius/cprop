(ns cprop.core
  (:require [clojure.string :as s]
            [clojure.edn :as edn]
            [cprop.tools :refer [merge-maps]]
            [cprop.source :refer [merge*
                                  read-system-env
                                  read-system-props
                                  from-resource
                                  from-file
                                  ignore-missing-default]]))

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
      (throw (RuntimeException. (str "could not find a non empty configuration file to load. "
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
