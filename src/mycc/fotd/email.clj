(ns mycc.fotd.email
  (:require [hiccup.core :as h]))

(defn create-email-html [email])

(defn send-email [email]
  (create-email-html [email]))

(defn send-emails [emails]
  (map send-email))

