(ns myapp.core
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [accountant.core :as accountant]
            [clojure.string  :as str]
            [cljs.core.async :as async  :refer (<! >! put! chan)]
            [taoensso.encore :as encore :refer ()]
            [taoensso.timbre :as timbre :refer-macros (tracef debugf infof warnf errorf)]
            [myapp.chans :refer (chsk ch-chsk chsk-send! chsk-state)]
            [taoensso.sente  :as sente])
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)]))

(defonce quotes (reagent/atom {:quotes '()}))
(defonce broadcasting? (atom false))

(defn add-quote! [q]
  (if (< 4 (count (:quotes @quotes)))
    (do 
      (swap! quotes update-in [:quotes] drop-last)))
  (swap! quotes update-in [:quotes] conj q))


;;;; Sente event handlers
(defmulti push-msg-handler (fn [[id _]] id))

(defmethod push-msg-handler :quotes/one [[_ event]]
  (add-quote! event))

(defmulti -event-msg-handler :id)

(defn event-msg-handler [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler :default
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

;;;; Sente event router (our `event-msg-handler` loop)

(defonce router_ (atom nil))

(defn  stop-router! [] 
  (when-let [stop-f @router_] 
    (stop-f)))

(defn start-router! []
  (stop-router!)
  (reset! router_
          (sente/start-client-chsk-router! ch-chsk event-msg-handler)))

;; login function

(defn login! []
  (let [user-id (.-value (.getElementById js/document "input-login"))]
    (if (str/blank? user-id)
      (js/alert "Please enter a user-id first!")
      (do
        (.log js/console "Logging in with user-id %s" user-id)
        
        (sente/ajax-lite "/login"
                         {:method :post
                          :headers {:X-CSRF-Token (:csrf-token @chsk-state)}
                          :params  {:user-id (str user-id)}}
                         
                         (fn [ajax-resp]
                           (.log js/console "Ajax login response: %s" ajax-resp)
                           (let [login-successful? true]
                             (do
                               (.log js/console "Login successful")
                               (sente/chsk-reconnect! chsk)))))
        (reset! quotes {:quotes '()})
        ))))

(defn toogle-broadcast! []
  (swap! broadcasting? not)
  (sente/ajax-lite "/broadcast"
                   {:method :post
                    :headers {:X-CSRF-Token (:csrf-token @chsk-state)}
                    :params  {:broadcast @broadcasting?}}
                   (fn [ajax-resp]
                     (.log js/console "Ajax toogle-broadcast response: %s" (str ajax-resp)))))

;; -------------------------
;; Views

(defn home-page []
  [:div.row.col-md-12
   [:div {:class "col-md-12"}
    [:h2 {:class "col-md-4"} "Activity stream"]
    [:h3 {:class "col-md-2 pull-right"} [:a {:href "/about"} "About"]]
    [:br][:br][:br]]
   
   [:div#content.row.col-md-12
    [:p {:class "text-muted"} "The server can use this id to send events to you specifically. (Insert PERA or Mica to try out.)"]
    [:form {:class "form-group form-inline"}
     [:p.row
      [:input#input-login {:type "text" :class "form-control col-md-4" :placeholder "user-id"}]
      [:span {:class "col-md-1"}]
      [:button#btn-login {:type "button" :class "form-control btn btn-primary col-md-3" :on-click #(login!)} "Secure login!"]]]
    [:form {:class "form-group form-inline"}
     [:p.row
      [:button#btn-start {:type "button" :class "form-control btn btn-success col-md-3" :on-click #(toogle-broadcast!)} "Toogle"]
      [:p {:class "text-muted"} (str "Broadcasting: " @broadcasting?)]]]
    (for [q (:quotes @quotes)]
      ^{:key (rand-int 1000)} [:div.row [:blockquote [:p (:quote q)] [:footer (:author q)]]])
    ]])

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
