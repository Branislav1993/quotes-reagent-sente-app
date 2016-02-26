(ns quotesapp.server
  (:use [org.httpkit.server :only [run-server]])
  (:require [quotesapp.handler :refer [app start-example-broadcaster! start-router!]])
  (:gen-class))

 (defn -main [& args]
   (let [port (Integer/parseInt "3000")]
     (run-server app {:port port :join? false})
     (start-router!)
     (start-example-broadcaster!)))
