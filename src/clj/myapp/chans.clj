(ns myapp.chans
  (:require [taoensso.sente :refer (make-channel-socket-server!)]
            [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]))

(let [packer :edn {:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn connected-uids]}
      (make-channel-socket-server! sente-web-server-adapter {:packer packer})]
  
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids) ; Watchable, read-only atom
  )