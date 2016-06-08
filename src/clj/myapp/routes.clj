(ns myapp.routes
  (:require [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [not-found resources]]
            [hiccup.page :refer [include-js include-css html5]]
            [myapp.middleware :refer [wrap-middleware]]
            [environ.core :refer [env]]
            [myapp.chans :refer (ring-ajax-get-or-ws-handshake ring-ajax-post)]))

(def mount-target
  [:div#app
   [:h3 "ClojureScript has not been compiled!"]
   [:p "please run "
    [:b "lein figwheel"]
    " in order to start the compiler"]])

(def page
  (html5
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1"}]
     [:title "Activity Stream"]
     (include-css (if (env :dev) "css/site.css" "css/site.min.css"))
     (include-css "css/bootstrap.min.css")]
    [:body.container mount-target
     (include-js "js/app.js")]))

(defn login-handler
  "Here's where you'll add your server-side login/auth procedure (Friend, etc.).
  In our simplified example we'll just always successfully authenticate the user
  with whatever user-id they provided in the auth request."
  [ring-req]
  (let [{:keys [session params]} ring-req
        {:keys [user-id]} params]
    {:status 200 :session (assoc session :uid user-id)}))

(defroutes routes
  (GET "/" [] page)
  (GET "/about" [] page)
  (GET  "/chsk"  ring-req (ring-ajax-get-or-ws-handshake ring-req))
  (POST "/chsk"  ring-req (ring-ajax-post                ring-req))
  (POST "/login" ring-req (login-handler                 ring-req))
  (resources "/")
  (not-found "Not Found"))

(def app (wrap-middleware #'routes))
