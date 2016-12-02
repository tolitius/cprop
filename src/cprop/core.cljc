(ns cprop.core
  (:require [cprop.tools :refer [merge-maps throw-runtime]]
            [cprop.source :refer [merge*
                                  read-system-env
                                  read-system-props
                                  system-getenv
                                  system-getprops
                                  from-resource
                                  from-file
                                  ignore-missing-default]])
  #?(:cljs (:require-macros [cprop.tools :refer [throw-runtime]])))

;; (load-config :resource "a/b/c.edn"
;;              :file "/path/to/file.edn"
;;              :merge [{} {} {} ..])
(defn load-config [& {:keys [file resource merge]
                      :or {merge []}}]
  (let [config (merge-maps (ignore-missing-default from-resource resource)
                           (ignore-missing-default from-file file))]
    (if #?(:clj (not-empty config)
           :cljs (or (not-empty config) (seq merge))) ;; merge is enough until file & resource arrives to cljs
      (as-> config $
            (apply merge-maps (cons $ merge))
            (merge* $ (read-system-props system-getprops))
            (merge* $ (read-system-env system-getenv)))
      (throw-runtime #?(:clj (str "could not find a non empty configuration file to load. "
                                  "looked in the classpath (as a \"resource\") "
                                  "and on a file system via \"conf\" system property")
                        :cljs (str "could not find a non empty configuration file to load."))))))

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
    (throw-runtime (str "the first argument should be the config itself, but instead it is: '" conf "'"))))
