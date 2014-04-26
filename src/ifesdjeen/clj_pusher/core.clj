(ns ifesdjeen.clj-pusher.core
  (:gen-class)
  (:require [clojure.walk :refer :all]
            [compojure.core :refer :all]
            [clojurewerkz.eep.emitter :refer :all]
            [org.httpkit.server :as httpkit]
            [clostache.parser :as clostache]
            [compojure.route :as route]
            [compojure.handler :refer [site]]
            [cheshire.core :as json]
            [clojure.tools.nrepl.server :as nrepl]))


(defonce emitter (create))
(defonce socket-subscriptions (atom {}))

(defn unsubscribe-peer [app-id socket-id channel-id]
  (when (contains? (get @socket-subscriptions socket-id) channel-id)
    (delete-handler emitter [app-id socket-id channel-id])
    (undefmulticast emitter [app-id channel-id]
                    [[app-id socket-id channel-id]])
    (swap! socket-subscriptions (fn [cache]
                                  (update-in cache [socket-id]
                                             #(disj % channel-id))))))

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
  (defmulticast emitter app-id [[app-id socket-id]])
  (swap! socket-subscriptions #(assoc % socket-id #{})))


(defn peer-disconnected
  [conn ^String app-id socket-id]
  (delete-handler emitter [app-id socket-id])
  (undefmulticast emitter app-id [[app-id socket-id]])
  (doseq [channel-id (get @socket-subscriptions socket-id)]
    (unsubscribe-peer app-id socket-id channel-id))
  (swap! socket-subscriptions #(dissoc % socket-id))
  (println "Live feed connection closed"))

(defn propagate
  ([event data app-id channel-id]
     (notify emitter [app-id channel-id] [event data channel-id]))
  ([event data app-id]
     (notify emitter app-id [event data])))



(defn send-data
  [app-id channel-id data]
  (propagate "test-event"
             data
             app-id
             channel-id))

(defmulti execute-command (fn [{:keys [event]} req-ch app-id socket-id] event))

(defmethod execute-command "pusher:subscribe"
  [msg conn app-id socket-id]
  (let [channel-id (get-in msg [:data :channel])]
    (defobserver emitter [app-id socket-id channel-id]
      (wrap-notify-peer conn socket-id))
    (defmulticast emitter [app-id channel-id] [[app-id socket-id channel-id]])
    (swap! socket-subscriptions (fn [cache]
                           (update-in cache [socket-id]
                                      #(conj % channel-id))))
    (httpkit/send! conn (json/generate-string
                         {:event "pusher_internal:subscription_succeeded"
                          :channel channel-id}))))

(defmethod execute-command "pusher:unsubscribe"
  [msg conn app-id socket-id]
  (let [channel-id (get-in msg [:data :channel])]
    (unsubscribe-peer app-id socket-id channel-id)))

(defmethod execute-command "pusher:ping"
  [msg conn app-id socket-id]
  (let [channel-id (get-in msg [:data :channel])]
    (httpkit/send! conn (json/generate-string
                         {:event "pusher:pong"
                          :data {}}))))

(defn ws-handler [req]
  (httpkit/with-channel req conn
    (let [app-id    (get-in req [:route-params :app_id])
          socket-id (java.util.UUID/randomUUID)]
      (peer-connected conn app-id socket-id)
      (httpkit/send! conn
                     (json/generate-string {:event "pusher:connection_established"
                                            :data {:socket_id socket-id}}))
      (httpkit/on-close conn
                        (fn [status]
                          (try
                            (peer-disconnected conn app-id socket-id)
                            (catch Exception e
                              (println (.getMessage e))))))
      (httpkit/on-receive conn
                          (fn [raw-msg]
                            (try
                              (let [msg (json/parse-string raw-msg true)]
                                (execute-command msg conn app-id socket-id))
                              (catch Exception e
                                (println (.getMessage e)))))))))


(declare nrepl-server)

(defn initialize-nrepl-server
  []
  (.start (Thread. ^Runnable (fn []
                               (printf "Starting nrepl server on port %d\n" 7888)
                               (defonce nrepl-server (nrepl/start-server :port 7888))))))

(defn index-page
  [request]
  (clostache/render-resource "templates/application.html" {}))

(defroutes main-routes
  (GET "/app/:app_id" {} ws-handler)
  (GET "/" request (index-page request))
  (route/resources "/assets")
  (route/not-found "Page not found"))


(defn run-websocket-server!
  []
  (httpkit/run-server (site #'main-routes) {:port 9292}))

(defn -main []
  (initialize-nrepl-server)
  (httpkit/run-server (site #'main-routes) {:port 9292}))

;; Presense channels:
;; Channels

;;(send-data "abc123" "my-channel" {:some "data"})
