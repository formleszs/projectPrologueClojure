(ns maze-game.game-state
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.core.async :as async])
  )

(def game-state (atom {
  :players {}      ; {player-id {:x, :y, :status, :color}}
  :bot {:x 0 :y 0 :target nil}
  :maze {:walls #{}
         :exit {:x 1 :y 1}
         :size [10 10]}
  :game-status :playing
  :tick 0
}))

(def prolog-process (atom nil))

(defn call-prolog [query]
  (let [result (shell/sh "swipl" "-q"
                         "-f" "src/prolog/maze_logic.pl"
                         "-f" "src/prolog/pathfinding.pl"
                         "-g" query "-t" "halt")]
    (if (= 0 (:exit result))
      (:out result)
      (throw (Exception. (str "Prolog error: " (:err result)))))))

(defn initialize-game []
  (reset! game-state
    {:players {}
     :bot {:x 0 :y 0 :target nil}
     :maze {:walls #{[0 0] [2 0] [3 0] [4 0]
                    [0 1]               [4 1]
                    [0 2] [1 2] [2 2]   [4 2]
                    [0 3]               [4 3]
                    [0 4] [2 4]         [4 4]
                    [0 5] [2 5]         [4 5]
                    [0 6] [1 6] [2 6] [3 6] [4 6]}
           :exit [1 0]
           :size [5 7]}
     :game-status :playing
     :tick 0}))

(defn add-player [player-id]
  (swap! game-state update-in [:players player-id]{
    :x 0 :y 2
    :status :alive
    :color (rand-nth ["#FF0000" "#00FF00" "#0000FF" "#FFFF00"])
  }))

(defn move-player [player-id direction]
  (let [player (get-in @game-state [:players player-id])
        [dx dy] (case direction
                  "up" [0 -1]
                  "down" [0 1]
                  "left" [-1 0]
                  "right" [1 0]
                  [0 0])
        new-x (+ (:x player) dx)
        new-y (+ (:y player) dy)
        walls (:walls (:maze @game-state))]
    (when (and (not (contains? walls [new-x new-y]))
               (>= new-x 0) (>= new-y 0)
               (< new-x (first (:size (:maze @game-state))))
               (< new-y (second (:size (:maze @game-state)))))
      (swap! game-state assoc-in [:players player-id :x] new-x)
      (swap! game-state assoc-in [:players player-id :y] new-y))))

(defn parse-path [s]
  (mapv (fn [[_ xs ys]]
          [(Integer/parseInt xs) (Integer/parseInt ys)])
        (re-seq #"\((\d+),\s*(\d+)\)" s)))

(defn update-bot []
  (let [bot (:bot @game-state)
        players (:players @game-state)
        alive-players (filter (fn [[_ p]] (= :alive (:status p))) players)]
    (when (seq alive-players)
      (let [[player-id player] (first alive-players)
            query (format "find_path(%d,%d,%d,%d,Path), writeln(Path)."
                          (:x bot) (:y bot) (:x player) (:y player))
            path-str (call-prolog query)
            path (parse-path path-str)
            next-pos (second path)]
        (when next-pos
          (let [[x y] next-pos]
            (swap! game-state assoc :bot {:x x :y y :target player-id})

            (doseq [[pid p] (:players @game-state)]
              (when (and (= (:x p) x)
                         (= (:y p) y)
                         (= (:status p) :alive))
                (swap! game-state assoc-in [:players pid :status] :dead)))))))))


(defn game-tick []
  (when (= :playing (:game-status @game-state))
    (swap! game-state update :tick inc)
    (update-bot)

    (let [players (:players @game-state)
          alive-count (count (filter #(= :alive (:status (val %))) players))
          exit (:exit (:maze @game-state))
          at-exit-count (count (filter #(and (= (:x (val %)) (first exit))
                                            (= (:y (val %)) (second exit))
                                            (= :alive (:status (val %))))
                                      players))]
      (cond
        (zero? alive-count)
        (swap! game-state assoc :game-status :lost)

        (= alive-count at-exit-count)
        (swap! game-state assoc :game-status :won)))))