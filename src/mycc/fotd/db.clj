(ns mycc.fotd.db
  (:require
    [mycc.common.db :as db]))

(defn- create-fotd-data-map 
  ([] (create-fotd-data-map true))
  ([beginner?]
    {:active true
     :seen-fns #{}
     :beginner? beginner?}))

(defn- reenable-or-create-fotd-data [user-id]
  (let [user (db/get-user user-id)]  
    (if (contains? user :user/fotd)
      (assoc-in user [:user/fotd :active] true)
      (assoc user :user/fotd (create-fotd-data-map)))))

(defn unsubscribe-user [user-id]
  (let [user (db/get-user user-id)]  
    (some-> user
            (assoc-in [:user/fotd :active] false)
            db/save-user!)))

(defn subscribe-user [user-id]
   (->> (reenable-or-create-fotd-data user-id)
        db/save-user!))

(defn get-all-fotd-users []
  (->> (db/get-users)
       (filter #(contains? % :user/fotd))))

(defn get-subscribed-users []
  (->> (db/get-users)
       (filter #(:active (:user/fotd %)))))




(comment
  
  (def me "87a52fd9-6412-4866-9251-ffb4fe970712")

  (subscribe-user me)
  (unsubscribe-user me)

  (get-all-fotd-users)
  (get-subscribed-users)


  )
