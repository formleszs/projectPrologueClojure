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

(defn current-state []
  (let [s (dosync @game-state/game-state)]
    (update-in s [:maze :walls] vec)))

(defn game-loop []
  (async/go-loop []
    (async/<! (async/timeout 1000))
    (try
      (game-state/game-tick)
      (let [state (current-state)]
        (println "tick" (:tick state) "clients" (count @clients) "players" (count (:players state)))
        (broadcast {:type "state-update" :state state}))
      (catch Throwable t
        (println "GAME LOOP ERROR:" (.getMessage t))
        (.printStackTrace t)))
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

        (server/send! channel
                      (json/write-str {:type "state-update"
                                       :state (current-state)}))

        (server/on-close channel
          (fn [_status]
            (swap! clients dissoc player-id)
            (game-state/remove-player! player-id)))

        (server/on-receive channel
          (fn [data]
            (let [msg (json/read-str data :key-fn keyword)]
              (case (:type msg)
                "move"
                (do
                  (game-state/move-player player-id (:direction msg))
                  (broadcast {:type "state-update" :state (current-state)}))

                "start"
                (do
                  (game-state/start-game!)
                  (broadcast {:type "state-update" :state (current-state)}))

                "restart"
                (do
                  (game-state/restart-game!)
                  (broadcast {:type "state-update" :state (current-state)}))

                "chat"
                (broadcast {:type "chat"
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
