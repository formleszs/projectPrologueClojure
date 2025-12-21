(ns maze-game.websocket
  (:require [org.httpkit.server :as server]
            [clojure.data.json :as json]
            [clojure.core.async :as async]
            [clojure.java.io :as io]
            [maze-game.game-state :as game-state])
  (:import [java.util UUID]))

(def clients (atom {}))

(defn broadcast [message]
  (doseq [client (vals @clients)]
    (server/send! client (json/write-str message))))

(defn game-loop []
  (async/go-loop []
    (async/<! (async/timeout 1000))
    (game-state/game-tick)
    (broadcast {:type "state-update"
                :state @game-state/game-state})
    (recur)))

(defn index-page []
  (slurp (io/resource "public/index.html")))

(defn ws-handler [req]
  (if-not (:websocket? req)
    {:status 400
     :headers {"Content-Type" "text/plain; charset=utf-8"}
     :body "Expected WebSocket request"}
    (server/with-channel req channel
      (let [player-id (str (UUID/randomUUID))]
        (swap! clients assoc player-id channel)
        (game-state/add-player player-id)

        (server/on-close channel
          (fn [_status]
            (swap! clients dissoc player-id)
            (swap! game-state/game-state update :players dissoc player-id)))

        (server/on-receive channel
          (fn [data]
            (let [msg (json/read-str data :key-fn keyword)]
              (case (:type msg)
                "move" (game-state/move-player player-id (:direction msg))
                "chat" (broadcast {:type "chat"
                                   :player player-id
                                   :message (:message msg)})
                nil))))))))

(defn app [req]
  (case (:uri req)
    "/" {:status 200
         :headers {"Content-Type" "text/html; charset=utf-8"}
         :body (index-page)}

    "/ws" (ws-handler req)

    "/favicon.ico" {:status 204 :body ""}

    {:status 404
     :headers {"Content-Type" "text/plain; charset=utf-8"}
     :body "Not found"}))
