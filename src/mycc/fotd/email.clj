(ns mycc.fotd.email
  (:require [hiccup.core :as h]
            [garden.core :as g]))

(defn create-email-html [email]
  (h/html
    [:h1 {:style (g/style {:font-weight "bold"})} (:name email)]))

(defn send-email! [email]
  (create-email-html email))

(defn send-emails! [emails]
  (map send-email! emails))

