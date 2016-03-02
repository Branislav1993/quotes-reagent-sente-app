(ns myapp.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [accountant.core :as accountant]
              [clojure.string  :as str]
              [cljs.core.async :as async  :refer (<! >! put! chan)]
              [taoensso.encore :as encore :refer ()]
              [taoensso.timbre :as timbre :refer-macros (tracef debugf infof warnf errorf)]
              [taoensso.sente  :as sente  :refer (cb-success?)])
   (:require-macros
              [cljs.core.async.macros :as asyncm :refer (go go-loop)]))

;;SENTE section

;;-------------------------

;;;; Define our Sente channel socket (chsk) client

(let [;; For this example, select a random protocol:
      rand-chsk-type (if (>= (rand) 0.5) :ajax :auto)
      _ (.log js/console "Randomly selected chsk type: %s" (str rand-chsk-type))

      ;; Serializtion format, must use same val for client + server:
      packer :edn ; Default packer, a good choice in most cases
      ;; (sente-transit/get-flexi-packer :edn) ; Experimental, needs Transit dep

      {:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client!
        "/chsk" ; Must match server Ring routing URL
        {:type   rand-chsk-type
         :packer packer})]

  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)   ; Watchable, read-only atom
  )

;;;; Sente event handlers

(defmulti -event-msg-handler
  "Multimethod to handle Sente `event-msg`s"
  :id ; Dispatch on event-id
  )

(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler
  :default ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event]}]
  (.log js/console "Unhandled event: " (str event)))

(defmethod -event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (if (= ?data {:first-open? true})
    (.log js/console "Channel socket successfully established!")
    (.log js/console "Channel socket state change: " (str ?data))))

(defmethod -event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (do
  (.log js/console "Push event from server: " (str ?data))
  (swap! q1 assoc :quote (:quote (:q1 (nth ?data 1))) :author (:author (:q1 (nth ?data 1))))
  (swap! q2 assoc :quote (:quote (:q1 (nth ?data 1))) :author (:author (:q2 (nth ?data 1))))))

(defmethod -event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (.log js/console "Handshake: " (str ?data))))

;;;; Sente event router (our `event-msg-handler` loop)

(defonce router_ (atom nil))
(defn  stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! []
  (stop-router!)
  (reset! router_
    (sente/start-client-chsk-router!
      ch-chsk event-msg-handler)))

;;-------------------------

;; -------------------------
;; Views

(defonce q1 (reagent/atom {:quote "" :author ""}))
(defonce q2 (reagent/atom {:quote "" :author ""}))

(defn home-page []
  [:div.row.col-md-8.col-md-offset-2
   [:h2 "Welcome to myapp"]
   [:br]
   [:div.row [:blockquote [:p (get @q1 :quote)] [:footer (get @q1 :author)]]]
   [:div.row [:blockquote.blockquote-reverse [:p (get @q2 :quote)] [:footer (get @q2 :author)]]]
   [:div.row [:a {:href "/about"} "go to about page"]]])

(defn about-page []
  [:div [:h2 "About myapp"]
   [:div [:a {:href "/"} "go to the home page"]]])

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

(secretary/defroute "/about" []
  (session/put! :current-page #'about-page))

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (accountant/configure-navigation!)
  (accountant/dispatch-current!)
  (start-router!)
  (mount-root))
