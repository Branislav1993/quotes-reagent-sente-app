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
                                    
(defonce quotes (reagent/atom {:quotes []}))

(defn update-quotes! [f & args]
  (apply swap! quotes update-in [:quotes] f args))

(defn add-quote! [q]
   (update-quotes! conj q))

;;SENTE section

;;-------------------------

;;;; Define our Sente channel socket (chsk) client

(let [      ;; Serializtion format, must use same val for client + server:
      packer :edn ; Default packer, a good choice in most cases
      ;; (sente-transit/get-flexi-packer :edn) ; Experimental, needs Transit dep

      {:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client!
        "/chsk" ; Must match server Ring routing URL
        {:type  :auto
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
  (push-msg-handler ?data))

(defmethod -event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (.log js/console "Handshake: " (str ?data))))
    
    
(defmulti push-msg-handler (fn [[id _]] id))

(defmethod push-msg-handler :quotes/two [[_ event]]
  (add-quote! event))
  

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

(defn home-page []
   [:div.row.col-md-8.col-md-offset-2
   [:h2 "Activity stream"]
   [:div.row [:a {:href "/about"} "go to about page"]]
   [:br]
   (for [q (rseq (:quotes @quotes))]
      ^{:key (rand-int 1000)} [:div.row [:blockquote [:p (:quote q)] [:footer (:author q)]]])
   ])

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
