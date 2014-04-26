(ns ifesdjeen.clj-pusher.core-test
  (:import [java.util.concurrent CountDownLatch TimeUnit])
  (:require [gniazdo.core :as ws]
            [cheshire.core :as json])
  (:use clojure.test
        ifesdjeen.clj-pusher.core))

(alter-var-root #'*out* (constantly *out*))

(defn- noop [& _])
(defonce connection-str "ws://localhost:9292/app/abc123?protocol=5&client=js&version=1.12.5&flash=true")

(use-fixtures :each
    (fn [f]
        (run-websocket-server!)
        (f)
        (stop-websocket-server!)))

(defmacro with-socket
  [binding url
   {:keys [on-connect on-receive on-binary on-error on-close]
    :or   {on-connect noop
           on-receive noop
           on-binary  noop
           on-error   noop
           on-close   noop}}
   & body]
  `(let [~binding (ws/connect ~url
                              :on-receive ~on-receive
                              :on-connect ~on-connect
                              :on-binary ~on-binary
                              :on-error ~on-error
                              :on-close ~on-close)]
     ~@body
     (ws/close ~binding)))

(deftest t-establish-connestion
  (let [result (atom nil)
        latch  (CountDownLatch. 1)]
    (with-socket socket connection-str
      {:on-receive (fn [response]
                     (.countDown latch)
                     (reset! result response))}
      (.await latch 1 TimeUnit/SECONDS)
      (let [result (-> result deref (json/parse-string true))]
        (is (= "pusher:connection_established" (:event result)))))))

(deftest t-subscribe-to-channel
  (let [result (atom [])
        latch  (CountDownLatch. 2)]
    (with-socket socket connection-str
      {:on-receive (fn [response]
                     (.countDown latch)
                     (swap! result conj response))}
      (ws/send-msg socket
                   (json/generate-string
                    {:event "pusher:subscribe" :data {:channel "my-channel"}}))
      (.await latch 1 TimeUnit/SECONDS)
      (let [result (->> result
                        deref
                        (map #(json/parse-string % true)))]
        (is (= 2 (count result)))
        (is (= "pusher_internal:subscription_succeeded" (-> result last :event)))
        ))))

(deftest t-receive-data
  (let [result (atom [])
        latch  (CountDownLatch. 3)]
    (with-socket socket connection-str
      {:on-receive (fn [response]
                     (.countDown latch)
                     (swap! result conj response))}
      (.await latch 1 TimeUnit/SECONDS)
      (ws/send-msg socket
                   (json/generate-string
                    {:event "pusher:subscribe" :data {:channel "my-channel"}}))
      (.await latch 1 TimeUnit/SECONDS)
      (send-data "abc123" "my-channel" {:some "data"})
      (.await latch 1 TimeUnit/SECONDS)
      (let [result (->> result
                        deref
                        (map #(json/parse-string % true)))]
        (is {:event "test-event," :data {:some "data"}, :channel "my-channel"}
            (last result))
        (is (= 3 (count result)))))))
