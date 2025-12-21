(ns maze-game.core
  (:require [org.httpkit.server :as server]
            [maze-game.game-state :as game-state]
            [maze-game.websocket :as ws])
  (:gen-class))

(defonce server-stop (atom nil))
(defonce loop-started? (atom false))

(defn start-server []
  (println "Starting maze game server on port 8080...")
  (reset! server-stop (server/run-server ws/app {:port 8080})))

(defn -main [& _args]
  (game-state/initialize-game)
  (when (compare-and-set! loop-started? false true)
    (ws/game-loop))
  (start-server))
