(ns mycc.fotd.core
   (:require [clojure.set :as setfns]
             [clojure.edn :refer [read-string]]))

(def clojuredocs-export-url "https://raw.githubusercontent.com/clojure-emacs/clojuredocs-export-edn/master/exports/export.compact.edn")

(defn get-clojure-lib []
  (read-string (slurp clojuredocs-export-url)))

(defn fns-from-namespaces [namespaces]
  (let [namespaces (set namespaces)]
    (filter (fn [[_ v]] (namespaces (:ns v))) (get-clojure-lib))))

(defn apply-denylists [fns & denylists]
  (let [denylist (apply setfns/union denylists)]
    (setfns/difference (set fns) denylist)))

(comment
  (get-clojure-lib)

  (fns-from-namespaces #{"clojure.core clojure.set clojure.async"})

  (apply-denylists #{"test" "age" "car" "foot"} #{"age"} #{"car"})
  ; #{"foot" "test"}
  
)

