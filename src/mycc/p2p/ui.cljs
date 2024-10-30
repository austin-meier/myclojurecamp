(ns mycc.p2p.ui
  (:require
    [clojure.string :as string]
    [bloom.commons.fontawesome :as fa]
    [mycc.api :as api]
    [mycc.p2p.util :as util]
    [mycc.p2p.styles :as styles]))

(defn popover-view
  [content]
  [:div.info
   [fa/fa-question-circle-solid]
   [:div.popover
    content]])

(defn max-limit-preferences-view []
  [:<>
   [:section.field.max-pair-day
    [:label
     [:h1
      "Max pairings per day"
      [popover-view "Maximum number of times you will be scheduled in a given day."]]
     [:input {:type "number"
              :value @(api/subscribe [:user-profile-value :user/max-pair-per-day])
              :min 1
              :max 24
              :on-change (fn [e]
                           (api/dispatch [:set-user-value!
                                          :user/max-pair-per-day
                                          (js/parseInt (.. e -target -value) 10)]))}]]]
   [:section.field.max-pair-week
    [:label
     [:h1
     "Max pairings per week"
     [popover-view "Maximum number of times you will be scheduled in a given week."]]
     [:input {:type "number"
              :value @(api/subscribe [:user-profile-value :user/max-pair-per-week])
              :min 1
              :max (* 24 7)
              :on-change (fn [e]
                           (api/dispatch [:set-user-value!
                                          :user/max-pair-per-week
                                          (js/parseInt (.. e -target -value) 10)]))}]]]])

(defn next-day-of-week
  "Calculates next date with day of week as given"
  [now target-day-of-week]
  (let [target-day-of-week ({:monday 1
                             :tuesday 2
                             :wednesday 3
                             :thursday 4
                             :friday 5
                             :saturday 6
                             :sunday 0} target-day-of-week)
        now-day-of-week (.getDay now)
        ;; must be a nice way to do this with mod
        ;; (mod (- 7 now-day-of-week) 7)
        delta-days (get (zipmap (range 0 7)
                                (take 7 (drop (- 7 target-day-of-week)
                                              (cycle (range 7 0 -1)))))
                        now-day-of-week)
        new-date (doto (js/Date. (.valueOf now))
                   (.setDate (+ delta-days (.getDate now))))]
    new-date))

(defn add-days [day delta-days]
  (doto (js/Date. (.valueOf day))
    (.setDate (+ delta-days (.getDate day)))))

(defn format-date [date]
  (.format (js/Intl.DateTimeFormat. "en-US" #js {:weekday "long"
                                                 :month "short"
                                                 :day "numeric"})
           date))

(defn topics-view []
  [:section.field.topics
   [:h1 "Topics to Pair On"
    [popover-view "Topics you'd like to pair on. You will be matched so that you have at least one topic in common. The number beside each topic indicates how many other people have that topic selected."]]
   (let [user-topic-ids @(api/subscribe [:user-profile-value :user/topic-ids])]
    [:<>
     (when (and (empty? user-topic-ids)
                @(api/subscribe [:user-profile-value :user/pair-next-week?]))
      [:p.warning
       [fa/fa-exclamation-triangle-solid]
       "You need to select at least one topic to be matched with someone."])
     [:div.topics
      (for [topic (sort-by :topic/name @(api/subscribe [:topics]))
            :let [checked? (contains? user-topic-ids (:topic/id topic))]]
        ^{:key (:topic/id topic)}
        [:label.topic
         [:input {:type "checkbox"
                  :checked checked?
                  :on-change
                  (fn []
                    (if checked?
                      (api/dispatch [:remove-user-topic! (:topic/id topic)])
                      (api/dispatch [:add-user-topic! (:topic/id topic)])))}]
         [:span.name (:topic/name topic)] " "
         [:span.count (:topic/user-count topic)]])
      [:button
       {:on-click (fn [_]
                    (let [value (js/prompt "Enter a new topic:")]
                      (when (not (string/blank? value))
                        (api/dispatch [:new-topic! (string/trim value)]))))}
       "+ Add Topic"]]])])


(defn role-view []
  (let [role @(api/subscribe [:user-profile-value :user/role])]
    [:section.field.role
     [:h1 "Role"
      [popover-view
       [:<>
        [:div "Students are scheduled with other students and mentors."]
        [:div "Mentors are only scheduled with students."]]]]
     [:div.choices
      (for [[value label] [[:role/student "Student"]
                           [:role/mentor "Mentor"]]]
        ^{:key value}
        [:label
         [:input {:type "radio"
                  :checked (= role value)
                  :on-change (fn [_]
                               (api/dispatch [:set-user-value! :user/role value]))}]
         [:span.label label]])]]))

(defn availability-view []
  [:section.field.availability
   [:h1 "Availability"
    [popover-view
     [:<>
      [:div "Click in the calendar grid below to indicate your time availability."]
      [:div "A = available, P = preferred"]]]]

   (when-let [availability @(api/subscribe [:user-profile-value :user/availability])]
     [:table
      [:thead
       [:tr
        [:th]
        (let [next-monday (next-day-of-week (js/Date.) :monday)]
          (for [[i day] (map-indexed (fn [i d] [i d]) util/days)]
            (let [[day-of-week date] (string/split (format-date (add-days next-monday i)) #",")]
              ^{:key day}
              [:th.day
               [:div.day-of-week day-of-week]
               [:div.date date]])))]]
      [:tbody
       (doall
         (for [hour util/hours]
           ^{:key hour}
           [:tr
            [:td.hour
             hour]
            (doall
              (for [day util/days]
                ^{:key day}
                [:td
                 (let [value (availability [day hour])]
                   [:button
                    {:class (case value
                              :preferred "preferred"
                              :available "available"
                              nil "empty")
                     :on-click (fn [_]
                                 (api/dispatch [:set-availability!
                                                [day hour]
                                                (case value
                                                  :preferred nil
                                                  :available :preferred
                                                  nil :available)]))}
                    [:div.wrapper
                     (case value
                       :preferred "P"
                       :available "A"
                       nil "")]])]))]))]])])

(defn opt-in-view []
  (let [opt-in? @(api/subscribe [:user-profile-value :user/pair-next-week?])]
    [:section.field.opt-in
     [:h1 "Opt-in for pairing next week?"]
     [:div.choices
      (for [[value label] [[true "Yes"]
                           [false "No"]]]
        ^{:key value}
        [:label
         [:input {:type "radio"
                  :checked (= opt-in? value)
                  :on-change (fn [_]
                               (api/dispatch [:opt-in-for-pairing! value]))}]
         [:span.label label]])]]))

(defn time-zone-view []
  [:section.field.time-zone
   [:label
    [:h1 "Time Zone"
     [popover-view "Your time-zone. If the Auto-Detection is incorrect, email raf@clojure.camp"]]
    [:input {:type "text"
             :disabled true
             :value @(api/subscribe [:user-profile-value :user/time-zone])}]
    [:button
     {:on-click (fn []
                  (api/dispatch
                    [:set-user-value! :user/time-zone (.. js/Intl DateTimeFormat resolvedOptions -timeZone)]))}
     "Re-Auto-Detect"]]])

(defn subscription-toggle-view []
  (let [subscribed? @(api/subscribe [:user-profile-value :user/subscribed?])]
    [:section.field.subscription
     [:h1 "Subscribed?"
      [popover-view "Set to No to stop receiving emails."]]
     [:div.choices
      (for [[value label] [[true "Yes"]
                           [false "No"]]]
        ^{:key value}
        [:label
         [:input {:type "radio"
                  :checked (= subscribed? value)
                  :on-change (fn [_]
                               (api/dispatch [:update-subscription! value]))}]
         [:span.label label]])]]))

(defn format-date-2 [date]
  (.format (js/Intl.DateTimeFormat. "default" #js {:day "numeric"
                                                   :month "short"
                                                   :year "numeric"
                                                   :hour "numeric"})
           date))

(defn event-view [heading event]
 (let [guest-name (:user/name (:event/other-guest event))
       other-guest-flagged? (contains? (:event/flagged-guest-ids event)
                                       (:user/id (:event/other-guest event)))]
   [:tr.event {:class (if (< (js/Date.) (:event/at event))
                        "future"
                        "past")}
    [:th heading]
    [:td
     [:span.at (format-date-2 (:event/at event))]
     " with "
     [:span.other-guest (:user/name (:event/other-guest event))]]
    [:td
     [:div.actions
      [:a.link {:href (str "mailto:" (:user/email (:event/other-guest event)))}
       [fa/fa-envelope-solid]]
      [:a.link {:href (util/->event-url event)}
       [fa/fa-video-solid]]
      [:button.flag
       {:class (when other-guest-flagged? "flagged")
        :on-click (fn []
                    (if other-guest-flagged?
                      (api/dispatch [:flag-event-guest! (:event/id event) false])
                      (when (js/confirm (str "Are you sure you want to report " guest-name " for not showing up?"))
                       (api/dispatch [:flag-event-guest! (:event/id event) true]))))}
       [fa/fa-flag-solid]]]]]))

(defn events-view []
  (let [events @(api/subscribe [:events])
        [upcoming-events past-events] (->> events
                                           (sort-by :event/at)
                                           reverse
                                           (split-with (fn [event]
                                                         (< (js/Date.) (:event/at event)))))]
   [:table.events
    [:tbody
     (for [[index event] (map-indexed vector upcoming-events)]
       ^{:key (:event/id event)}
       [event-view (when (= 0 index) "Upcoming Sessions") event])
     (for [[index event] (map-indexed vector past-events)]
       ^{:key (:event/id event)}
       [event-view (when (= 0 index) "Past Sessions") event])]]))

<<<<<<< HEAD:src/mycc/client/ui/main.cljs
(defn header-view []
  [:div.header
   [:img.logomark
    {:src "/logomark.svg"
     :alt "Logo of Clojure Camp. A star constellation in the shape of alambda."}]
   [:div.gap]
   [:img.logotype
    {:src "/logotype.svg"
     :alt "Clojure Camp"}]
   [:div.gap]
   [:div.nav-items
    [:a.module {:href "/test"}
     [:p "#fotd"]]
    [:button.log-out
    {:on-click (fn []
                 (dispatch [:log-out!]))}
    "Log Out"]]])
(defn p2p-page-view []
  [:div.page.p2p
   [opt-in-view]
   [role-view]
   [topics-view]
   [availability-view]
   [time-zone-view]
   [max-limit-preferences-view]
   [subscription-toggle-view]
   [events-view]])

(api/register-page!
  {:page/id :page.id/p2p
   :page/path "/p3p"
   :page/nav-label "Pairing"
   :page/view #'p2p-page-view
   :page/styles styles/styles
   :page/on-enter! (fn []
                     (api/dispatch [:p2p/fetch-topics!])
                     (api/dispatch [:p2p/fetch-events!]))})
