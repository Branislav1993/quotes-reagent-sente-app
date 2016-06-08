(ns myapp.handler
  (:require [clojure.core.async :as async  :refer (<! <!! >! >!! put! chan go go-loop)]
            [taoensso.encore    :as encore :refer ()]
            [taoensso.timbre    :as timbre :refer (tracef debugf infof warnf errorf)]
            [taoensso.sente     :as sente]
            [myapp.chans :refer (ch-chsk chsk-send! connected-uids)]))

;;SENTE section
;;---------------------------------------------


(defn login-handler
  "Here's where you'll add your server-side login/auth procedure (Friend, etc.).
  In our simplified example we'll just always successfully authenticate the user
  with whatever user-id they provided in the auth request."
  [ring-req]
  (let [{:keys [session params]} ring-req
        {:keys [user-id]} params]
    (debugf "Login request: %s" params)
    {:status 200 :session (assoc session :uid user-id)}))

;;;; Sente event handlers

(defmulti -event-msg-handler
  "Multimethod to handle Sente `event-msg`s"
  :id ; Dispatch on event-id
  )

(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler :default 
; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (debugf "Unhandled event: %s" event)
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

(defonce router_ (atom nil))
(defn  stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! []
  (stop-router!)
  (reset! router_
    (sente/start-server-chsk-router!
      ch-chsk event-msg-handler)))

;;;; Some server>user async push examples

(def quotes '({:quote "Don't cry because it's over, smile because it happened." :author "Dr. Seuss"}
              {:quote "Be yourself; everyone else is already taken." :author "Oscar Wilde"}
              {:quote "Two things are infinite: the universe and human stupidity; and I'm not sure about the universe." :author "Albert Einstein"}
              {:quote "A warm smile is the universal language of kindness." :author "William Arthur Ward"}
              {:quote "The real man smiles in trouble, gathers strength from distress, and grows brave by reflection." :author "Thomas Paine"}))

(defn rand-quote [] (rand-nth quotes))

(defn quote-event []
    [:quotes/two (rand-quote)])

(defn start-example-broadcaster! []
  (let [broadcast!
        (fn [i]
          (debugf "Broadcasting server>user: %s" @connected-uids)
          (doseq [uid (:any @connected-uids)]
          (debugf "UID: %s" uid)
            (chsk-send! uid (quote-event))))]

    (go-loop [i 0]
      (<! (async/timeout 2000))
      (broadcast! i)
      (recur (inc i)))))

