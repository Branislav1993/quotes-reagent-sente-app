(ns myapp.server
  (:require [myapp.handler :refer [start-example-broadcaster! start-router!]]
            [myapp.routes :refer [app]]
            [environ.core :refer [env]]
            [org.httpkit.server :refer [run-server]])
  (:gen-class))

(defn -main [& args]
  (let [port (Integer/parseInt (or (env :port) "3000"))]
    (run-server app {:port port :join? false})
    (start-router!)
    (start-example-broadcaster!)))
