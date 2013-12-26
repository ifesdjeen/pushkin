(ns ifesdjeen.clj-pusher.core
  (:gen-class)
  (:use clojurewerkz.eep.emitter
        compojure.core)
  (:require [org.httpkit.server :as httpkit]
            [clostache.parser :as clostache]
            [compojure.route :as route]
            [compojure.handler :refer [site]]
            [cheshire.core :as json]
            [clojure.tools.nrepl.server :as nrepl]))


(defonce emitter (create))
(defonce channel-cache (atom {}))

(defn wrap-notify-peer
  [conn socket-id]
  (vary-meta
   (fn [[event data channel-id]]
     (httpkit/send! conn (json/generate-string (if channel-id
                                           {:event event
                                            :data data
                                            :channel channel-id}
                                           {:event event
                                            :data data
                                            }))))
   assoc :socket-id socket-id))

(defn peer-connected
  [conn ^String app-id socket-id]
  (defobserver emitter [app-id socket-id] (wrap-notify-peer conn socket-id))
  (defmulticast emitter app-id [[app-id socket-id]]))


(defn peer-disconnected
  [conn ^String app-id socket-id]
  (delete-handler emitter [app-id socket-id])
  (undefmulticast emitter app-id [[app-id socket-id]])
  (doseq [channel-id (get @channel-cache app-id)]
    (delete-handler emitter [app-id socket-id channel-id])
    (undefmulticast emitter [app-id channel-id] [[app-id socket-id channel-id]]))
  (println "Live feed connection closed"))

(defn propagate
  ([event data app-id channel-id]
     (notify emitter [app-id channel-id] [event data channel-id]))
  ([event data app-id]
     (notify emitter app-id [event data])))

(defn subscribe-channel
  [conn app-id socket-id channel-id]
  (defobserver emitter [app-id socket-id channel-id]
    (wrap-notify-peer conn socket-id))
  (defmulticast emitter [app-id channel-id] [[app-id socket-id channel-id]])
  (swap! channel-cache (fn [cache]
                         (update-in cache [app-id] #(set (conj % channel-id)))))
  (httpkit/send! conn (json/generate-string
                       {:event "pusher_internal:subscription_succeeded"
                        :channel channel-id})))

(defn send-data
  [app-id channel-id data]
  (propagate "test-event"
             data
             app-id
             channel-id))

(defn ws-handler [req]
  (httpkit/with-channel req req-ch
    (let [app-id (get-in req [:route-params :app_id])
          socket-id (java.util.UUID/randomUUID)]
      (peer-connected req-ch app-id socket-id)
      (httpkit/send! req-ch
                     (json/generate-string {:event "pusher:connection_established"
                                            :data {:socket_id socket-id}}))

      (httpkit/on-close req-ch
                        (fn [status]
                          (peer-disconnected req-ch app-id socket-id)))
      (httpkit/on-receive req-ch
                          (fn [raw-msg]
                            (try
                              (let [event-data (json/parse-string raw-msg)
                                    channel-id (get-in event-data ["data" "channel"])]
                                (subscribe-channel req-ch app-id socket-id channel-id))
                              (catch Exception e
                                (println (.getMessage e)))))))))


(declare nrepl-server)

(defn initialize-nrepl-server
  []
  (.start (Thread. ^Runnable (fn []
                               (printf "Starting nrepl server on port %d" 7888)
                               (defonce server (nrepl/start-server :port 7888))))))

(defn index-page
  [request]
  (clostache/render-resource "templates/application.html" {}))

(defroutes main-routes
  (GET "/app/:app_id" {} ws-handler)
  (GET "/" request (index-page request))
  (route/resources "/assets")
  (route/not-found "Page not found"))



(defn -main []
  (initialize-nrepl-server)
  (httpkit/run-server (site #'main-routes) {:port 9292}))

;; Presense channels:
;; Channels

;;(send-data "abc123" "my-channel" {:some "data"})
