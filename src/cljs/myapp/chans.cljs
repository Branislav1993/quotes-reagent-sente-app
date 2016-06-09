(ns myapp.chans
  (:require [taoensso.sente  :as sente  :refer (cb-success?)]))

(let [packer :edn {:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client!
        "/chsk" ; Must match server Ring routing URL
        {:type  :auto ;:ws or :ajax :auto will try ws, if it fails, tries ajax
         :packer packer})]
  
  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)   ; Watchable, read-only atom
  )

(defonce router_ (atom nil))

(defn  stop-router! [] 
  (when-let [stop-f @router_] 
    (stop-f)))

(defn start-router! []
  (stop-router!)
  (reset! router_
          (sente/start-client-chsk-router! ch-chsk event-msg-handler)))