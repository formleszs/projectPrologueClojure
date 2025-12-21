(ns maze-game.core
  (:require [org.httpkit.server :as server]
            [clojure.data.json :as json]
            [clojure.core.async :as async]
            [maze-game.game-state :as game-state]
            [maze-game.websocket :as ws])
  (:gen-class))

(defn start-server []
  (println "Starting maze game server on port 8080...")
  (server/run-server #'ws/app {:port 8080}))

(defn -main [& args]
  (game-state/initialize-game)
  (start-server))