(ns mycc.fotd.core
   (:require [clojure.set :as setfns]
             [clojure.edn :refer [read-string]]
             [mycc.fotd.db :as db]))

(def clojuredocs-export-url "https://raw.githubusercontent.com/clojure-emacs/clojuredocs-export-edn/master/exports/export.compact.edn")
(def global-denylist #{})

(defn get-clojure-lib []
  (read-string (slurp clojuredocs-export-url)))

(defn fns-from-namespaces [fns namespaces]
  (let [namespaces (set namespaces)]
    (into {} (filter (fn [[_ v]] (namespaces (:ns v))) fns))))

(defn apply-denylists [fns & denylists]
  (let [denylist (apply setfns/union denylists)]
    (setfns/difference (set fns) denylist)))

(defn get-random-user-fn [fns user]
  (let [fotd-info (:user/fotd user)
        fns (fns-from-namespaces fns (:subscribed-ns fotd-info))]
    (-> fns
        keys
        set
        (apply-denylists (:seen-fns fotd-info) global-denylist)
        vec
        rand-nth
        fns
        (assoc :email (:user/email user)))))

(defn generate-daily-fns []
  (let [fns (get-clojure-lib)]
    (->> (db/get-subscribed-users)
         (map #(get-random-user-fn fns %)))))

(comment
  (get-clojure-lib)

  (fns-from-namespaces (get-clojure-lib) #{"clojure.core clojure.set clojure.async"})

  (apply-denylists #{"test" "age" "car" "foot"} #{"age"} #{"car"})
  ; #{"foot" "test"}

  (first (db/get-subscribed-users))

  (generate-daily-fns)
)

