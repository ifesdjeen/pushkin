(ns com.ifesdjeen.clj-pusher.old-core
  (:gen-class)
  (:use compojure.core
        lamina.core
        aleph.http)
  (:require  [cheshire.core :as json]
             [ring.middleware.params :as params]
             [ring.middleware.reload-modified :as reload-modified]
             [ring.util.response     :as response]
             [compojure.route :as route]
             [compojure.handler :as handler]

             [clojure.tools.nrepl.server :as nrepl]
             [clostache.parser :as clostache]))

;;
;; Live Feed
;;

(defrecord FeedWatcher
    [conn ^String identifier])

(def feed-consumers
  (atom #{}))

(defn peer-connected
  [conn identifier]
  (swap! feed-consumers conj (FeedWatcher. conn identifier))
  (printf "New live feed connection (total %d)" (count @feed-consumers)))

(defn peer-disconnected
  [conn ^String identifier]
  (swap! feed-consumers disj (FeedWatcher. conn identifier))
  (printf "Live feed connection closed (total %d)" (count @feed-consumers)))

(defn propagate
  [event data app-id & {:keys [channel]}]
  (doseq [watcher (filter #(= (.identifier %) app-id) @feed-consumers)]
    (enqueue (.conn watcher) (json/generate-string (if channel
                                                     {:event event
                                                      :data data
                                                      :channel channel}
                                                     {:event event
                                                      :data data
                                                      })))))


;;;;; (add-consumer propagate)

;;
;;
;;

(defn async-handler [response-channel request]
  (enqueue response-channel
    {:status 200
     :headers {"content-type" "text/plain"}
     :body "async response"}))

(defn websocket-handler
  [channel request]
  (let [app-id (get-in request [:query-params "app_id"])]
    (peer-connected channel app-id)

    (propagate "pusher:connection_established"
               {:socket_id "22147.1422189"}
               app-id)

    (if (channel? channel)
      (receive-all channel (fn [payload]
                             (let [data (json/parse-string payload)]
                               (propagate "pusher_internal:subscription_succeeded"
                                          {}
                                          app-id
                                          :channel (get-in data ["data" "channel"]))
                               (propagate "test-event"
                                          {:some "data"}
                                          app-id
                                          :channel (get-in data ["data" "channel"]))
                               )))
      (on-realized channel (fn [a] (println a)) nil))


    (if (channel? channel)
      (on-closed channel #(peer-disconnected channel app-id))
      (on-realized channel
                   #(peer-disconnected % app-id)
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
  []
  (start-http-server
   (-> main-routes
       params/wrap-params
       (reload-modified/wrap-reload-modified ["src"])
       wrap-ring-handler)
   {:port 9292 :websocket true}))

(defn -main
  []
  (initialize-nrepl-server)
  (start-websocket-server))