(ns dojo.jobs.match-email
  (:require
    [clojure.string :as string]
    [clojure.set :as set]
    [chime.core :as chime]
    [pairing-scheduler.core :as ps]
    [dojo.email :as email]
    [dojo.db :as db])
  (:import
    (java.time Period DayOfWeek ZonedDateTime ZoneId LocalTime LocalDate)
    (java.time.format DateTimeFormatter)
    (java.time.temporal TemporalAdjusters)))

(def ->java-day-of-week
  {:monday DayOfWeek/MONDAY
   :tuesday DayOfWeek/TUESDAY
   :wednesday DayOfWeek/WEDNESDAY
   :thursday DayOfWeek/THURSDAY
   :friday DayOfWeek/FRIDAY
   :saturday DayOfWeek/SATURDAY
   :sunday DayOfWeek/SUNDAY})

(defn mapify [kf vf coll]
  (zipmap (map kf coll)
          (map vf coll)))

(defn adjust-day-of-week
  "Given a local-date, adjusts into the following day of week
     ex. 2021-01-04 + :thursday -> 2021-01-06"
  [local-date day-of-week]
  (.with
    local-date
    (TemporalAdjusters/nextOrSame (->java-day-of-week day-of-week))))

#_(adjust-day-of-week (LocalDate/now) :friday)

(defn convert-time
  "Converts from [:thursday 19] + 'America/Vancouver' (user's preferences)
                 + 2021-12-01  ('Monday' for which we run the matching)
                 to ZonedDateTime 2021-12-01 19:00:00 UTC"
  [[day-of-week hour-of-day] user-time-zone-string reference-local-date]
  (.withZoneSameInstant (ZonedDateTime/of (.with
                                           (adjust-day-of-week reference-local-date :monday)
                                           (TemporalAdjusters/nextOrSame (->java-day-of-week day-of-week)))
                                          (LocalTime/of hour-of-day 0)
                                          (ZoneId/of user-time-zone-string))
                        (ZoneId/of "UTC")))

#_(convert-time [:friday 19] "America/Vancouver" (LocalDate/now))
#_(LocalTime/of 19 0)
#_(LocalDate/now)

(defn ->inst [zoned-date-time]
  (java.util.Date/from (.toInstant zoned-date-time)))

(defn generate-schedule
  "Returns a list of maps, with :guest-ids, :day-of-week and :Time-of-day,
    ex.
    [{:guest-ids #{123 456}
      :day-of-week :monday
      :time-of-day 1200} ...]"
  [users local-date-start-of-week]
  (if (empty? users)
   []
   (->> {:max-events-per-day (mapify :user/id :user/max-pair-per-day users)
         :max-events-per-week (mapify :user/id :user/max-pair-per-week users)
         :topics (mapify :user/id :user/topic-ids users)
         :timezones (mapify :user/id :user/time-zone users)
         :availabilities (mapify :user/id
                                 ;; stored as {[:monday 10] :available
                                 ;;            [:tuesday 10] :preferred
                                 ;;            [:wednesday 10] nil)
                                 ;; but need #{[:monday 10 :available]
                                 ;;            [:tuesday 10 :preferred]}
                                 ;; also, remove when value is nil
                                 (fn [user]
                                   (->> (:user/availability user)
                                        (filter (fn [[_ v]] v))
                                        (map (fn [[k v]]
                                               [(->inst (convert-time k (:user/time-zone user) local-date-start-of-week)) v]))
                                        set))
                                 users)}
        (ps/schedule)
        :schedule)))

#_(generate-schedule (db/get-users) (LocalDate/now))

(defn group-by-guests
  [schedule]
  (reduce (fn [memo event]
           (-> memo
               (update (first (:guest-ids event)) (fnil conj #{}) event)
               (update (last (:guest-ids event)) (fnil conj #{}) event)))
     {} schedule))


(defn unmatched-email-template
  [user-id]
  (let [user (db/get-user user-id)]
    {:to (:user/email user)
     :subject "ClojoDojo - Your Matches for this Week"
     :body [:div
            [:p "Hi " (:user/name user) ","]
            [:p "Unfortunately, we couldn't match you with anyone this week. :("]
            [:p "- DojoBot"]]}))

(defn matched-email-template
  [user-id events]
  (let [get-user (memoize db/get-user)
        user (db/get-user user-id)]
   {:to (:user/email user)
    :subject "ClojoDojo - Your Matches for this Week"
    :body [:div
           [:p "Hi " (:user/name user) ","]
           [:p "Here are your pairing sessions for next week:"]
           (for [event (sort-by :at events)
                 :let [partner (get-user (first (disj (:guest-ids event) user-id)))]]
            [:p.event
             [:span.datetime
              [:strong
               (.format (ZonedDateTime/ofInstant (.toInstant (:at event))
                                                 (ZoneId/of (:user/time-zone user)))
                        (DateTimeFormatter/ofPattern "eee MMM dd 'at' HH:mm"))
               " (" (:user/time-zone user) ")"]]
             [:br]
             "With: "
             [:span.guest
              (:user/name partner)
              " (" (:user/email partner) ")"]
             [:br]
             "Topics: "
             (->> (set/intersection (:user/topic-ids user) (:user/topic-ids partner))
                  (map db/get-topic)
                  (map :topic/name)
                  (string/join ", "))])
           [:p "If you can't make a session, be sure to let your partner know!"]
           [:p "- DojoBot"]]}))

#_(email/send! (matched-email-template
                  #uuid "ebf40b80-6ea3-486d-9374-6125bfe06d72"
                  [{:guest-ids #{#uuid "e75af490-c666-4d80-aff4-374c31be9278"
                                 #uuid "ebf40b80-6ea3-486d-9374-6125bfe06d72"}
                    :at #inst "2021-11-08T14:00:00.000-00:00"}
                   {:guest-ids #{#uuid "e75af490-c666-4d80-aff4-374c31be9278"
                                 #uuid "ebf40b80-6ea3-486d-9374-6125bfe06d72"}
                    :at #inst "2021-11-09T14:00:00.000-00:00"}]))

#_(let [[user-id events] (first (group-by-guests (generate-schedule (db/get-users))))]
   (email/send! (sunday-email-template user-id events)))

(defn reset-opt-in! [user-id]
  (-> (db/get-user user-id)
      (assoc :user/pair-next-week? false)
      db/save-user!))

(defn send-sunday-emails! []
  (let [users-to-match (filter :user/pair-next-week? (db/get-users))
        user-id->events (-> users-to-match
                            (generate-schedule (LocalDate/now))
                            group-by-guests)
        opted-in-user-ids (set (map :user/id users-to-match))
        matched-user-ids (set (keys user-id->events))
        unmatched-user-ids (set/difference opted-in-user-ids
                                           matched-user-ids)]
   (doseq [user-id unmatched-user-ids]
     (email/send! (unmatched-email-template user-id))
     (reset-opt-in! user-id))

   (doseq [[user-id events] user-id->events]
     (email/send! (matched-email-template user-id events))
     (reset-opt-in! user-id))))

#_(send-sunday-emails!)

(defn schedule-email-job! []
  (chime/chime-at
    (->> (chime/periodic-seq
           (.adjustInto (LocalTime/of 18 0)
                        (ZonedDateTime/now (ZoneId/of "America/Toronto")))
           (Period/ofDays 1))
         (filter (fn [instant]
                   (= DayOfWeek/SUNDAY (.getDayOfWeek instant)))))
    (fn [_]
     (send-sunday-emails!))))
