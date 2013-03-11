(ns com.ifesdjeen.clj-pusher.core-test
  (:use clojure.test
        com.ifesdjeen.clj-pusher.core))

(deftest t-establish-connestion
  (testing "When establishing a connection")
  )
(deftest t-subscribe
  (testing "When subscribing a to public channel"
    )

  (testing "When subscribing a to private channel"
    )
)

(deftest t-bind
  (testing "When binding to a global event")
  (testing "When binding to channel specific event")
  (testing "When binding to all"))

(deftest t-trigger-client-event )