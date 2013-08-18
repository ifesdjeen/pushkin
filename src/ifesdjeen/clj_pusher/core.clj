(ns ifesdjeen.clj-pusher.core
  (:gen-class)
  (:use clojurewerkz.eep.emitter
        compojure.core
        lamina.core
        aleph.http)
  (:require [clojure.set :as s ]
            [cheshire.core :as json]
            [ring.middleware.params :as params]
            [ring.middleware.reload-modified :as reload-modified]
            [ring.util.response     :as response]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [clojure.tools.nrepl.server :as nrepl]
            [clostache.parser :as clostache]))

(alter-var-root #'*out* (constantly *out*))

;;
;; Live Feed
;;

(defonce emitter (new-emitter))
(defonce channel-cache (atom {}))

(defn wrap-notify-peer
  [conn socket-id]
  (vary-meta
   (fn [[event data channel-id]]
     (enqueue conn (json/generate-string (if channel-id
                                           {:event event
                                            :data data
                                            :channel channel-id}
                                           {:event event
                                            :data data
                                            }))))
   assoc :socket-id socket-id))

(defn peer-connected
  [conn app-id socket-id]
  (defobserver emitter [app-id socket-id] (wrap-notify-peer conn socket-id))
  (defmulticast emitter app-id [[app-id socket-id]])
  (println "New live feed connection"))

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
  (defobserver emitter [app-id socket-id channel-id] (wrap-notify-peer conn socket-id))
  (defmulticast emitter [app-id channel-id] [[app-id socket-id channel-id]])
  (swap! channel-cache (fn [cache] (update-in cache [app-id] #(set (conj % channel-id)))))
  (enqueue conn (json/generate-string {:event "pusher_internal:subscription_succeeded"
                                       :channel channel-id})))

(defn send-data
  [app-id channel-id data]
  (propagate "test-event"
             data
             app-id
             channel-id))

;;
;;
;;

(defn async-handler [response-channel request]
  (enqueue response-channel
           {:status 200
            :headers {"content-type" "text/plain"}
            :body "async response"}))

(defn websocket-handler
  [conn request]
  (let [app-id (get-in request [:route-params :app_id])
        socket-id (java.util.UUID/randomUUID)]
    (peer-connected conn app-id socket-id)

    (enqueue conn (json/generate-string {:event "pusher:connection_established"
                                         :data {:socket_id socket-id}}))

    (if (channel? conn)
      (receive-all conn (fn [payload]
                          (try
                            (let [event-data (json/parse-string payload)
                                  channel-id (get-in event-data ["data" "channel"])]
                              (subscribe-channel conn app-id socket-id channel-id))
                            (catch Exception e
                              (println (.getMessage e))))))
      (on-realized conn (fn [a] (println a)) nil))

    (if (channel? conn)
      (on-closed conn #(peer-disconnected conn app-id socket-id))
      (on-realized conn
                   #(peer-disconnected % app-id socket-id)
                   #(throw %)))))


(defn index-page
  [request]
  (clostache/render-resource "templates/application.html" {}))

(defroutes main-routes
  (GET "/app/:app_id" {} (wrap-aleph-handler websocket-handler))
  (GET "/" request (index-page request))
  (route/resources "/assets")
  (route/not-found "Page not found"))

;;
;; Nrepl
;;

(declare nrepl-server)

(defn initialize-nrepl-server
  []
  (.start (Thread. ^Runnable (fn []
                               (printf "Starting nrepl server on port %d" 7888)
                               (defonce server (nrepl/start-server :port 7888))))))


(defn start-websocket-server
  [config]
  (start-http-server
   (-> main-routes
       params/wrap-params
       (reload-modified/wrap-reload-modified ["src"])
       wrap-ring-handler)
   config))

(defn -main
  []
  (initialize-nrepl-server)
  (start-websocket-server {:host "localhost" :port 9292 :websocket true}))

;; Presense channels:
;; Channels

;; (send-data "abc123" "my-channel" {:some "data"})
