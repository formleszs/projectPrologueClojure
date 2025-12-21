(ns maze-game.game-state
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.core.async :as async])
  )

(def game-state (atom {
  :players {}
  :bot {:x 0 :y 0 :target nil}
  :maze {:walls #{}
         :exit {:x 1 :y 1}
         :size [10 10]}
  :game-status :playing
  :tick 0
}))

(def prolog-process (atom nil))

(defn call-prolog [goal]
  (let [result (shell/sh "swipl"
                         "-q"
                         "-s" "src/prolog/maze_logic.pl"
                         "-s" "src/prolog/pathfinding.pl"
                         "-g" goal
                         "-t" "halt")]
    (when (seq (:err result))
      (println "PROLOG STDERR:" (:err result)))
    (if (zero? (:exit result))
      (:out result)
      (throw (Exception.
              (str "Prolog failed (exit=" (:exit result) ")\n"
                   "STDERR:\n" (:err result) "\n"
                   "STDOUT:\n" (:out result)))))))



(defn initialize-game []
  (reset! game-state
          {:players {}
           :bot {:x 1 :y 5 :target nil}
           :maze {:walls #{[0 0] [2 0] [3 0] [4 0]
                           [0 1] [4 1]
                           [0 2] [1 2] [2 2] [4 2]
                           [0 3] [4 3]
                           [0 4] [2 4] [4 4]
                           [0 5] [2 5] [4 5]
                           [0 6] [1 6] [2 6] [3 6] [4 6]}
                  :exit [1 0]
                  :size [5 7]}
           :game-status :playing
           :tick 0}))

(defn add-player [player-id]
  (swap! game-state assoc-in [:players player-id]
         {:x 3 :y 5
          :status :alive
          :color (rand-nth ["#FF0000" "#00FF00" "#0000FF" "#FFFF00"])}))

(defn in-bounds? [x y]
  (let [[w h] (get-in @game-state [:maze :size])]
    (and (<= 0 x) (< x w) (<= 0 y) (< y h))))

(defn wall? [x y]
  (contains? (get-in @game-state [:maze :walls]) [x y]))

(defn passable? [x y]
  (and (in-bounds? x y)
       (not (wall? x y))))

(defn move-player [player-id direction]
  (let [player (get-in @game-state [:players player-id])]
    (when player
      (let [[dx dy] (case direction
                      "up"    [0 -1]
                      "down"  [0  1]
                      "left"  [-1 0]
                      "right" [1  0]
                      [0 0])
            new-x (+ (:x player) dx)
            new-y (+ (:y player) dy)
            walls (get-in @game-state [:maze :walls])
            [w h] (get-in @game-state [:maze :size])
            passable? (fn [x y]
                        (and (<= 0 x) (< x w)
                             (<= 0 y) (< y h)
                             (not (contains? walls [x y]))))]
        (when (passable? new-x new-y)
          (swap! game-state
                 (fn [s]
                   (-> s
                       (assoc-in [:players player-id :x] new-x)
                       (assoc-in [:players player-id :y] new-y)))))))))


(defn parse-path [s]
  (mapv (fn [[_ xs ys]]
          [(Integer/parseInt xs) (Integer/parseInt ys)])
        (re-seq #"\((\d+),\s*(\d+)\)" s)))

(defn update-bot []
  (let [bot (:bot @game-state)
        players (:players @game-state)
        alive-players (filter (fn [[_ p]] (and p (= :alive (:status p))))
                              (seq players))]
    (when (seq alive-players)
      (let [[player-id player] (first alive-players)
            walls (get-in @game-state [:maze :walls])
            [w h] (get-in @game-state [:maze :size])
            passable? (fn [x y]
                        (and (<= 0 x) (< x w)
                             (<= 0 y) (< y h)
                             (not (contains? walls [x y]))))

            query (format "((find_path(%d,%d,%d,%d,Path), writeln(Path)) ; writeln([]))."
                          (int (:x bot)) (int (:y bot))
                          (int (:x player)) (int (:y player)))

            path-str (call-prolog query)

            coords (map (fn [[_ xs ys]]
                          [(Integer/parseInt xs) (Integer/parseInt ys)])
                        (re-seq #"\((-?\d+),\s*(-?\d+)\)" (or path-str "")))

            next-pos (second coords)]
        (when (and next-pos (= 2 (count next-pos)))
          (let [old-x (:x bot)
                old-y (:y bot)
                [x y] next-pos
                step (+ (Math/abs (long (- x old-x)))
                        (Math/abs (long (- y old-y))))]
            (when (and (= step 1) (passable? x y))
              (swap! game-state
                     (fn [s]
                       (let [moved? (or (not= old-x x) (not= old-y y))
                             s (assoc s :bot {:x x :y y :target player-id})]
                         (if moved?
                           (reduce (fn [st [pid p]]
                                     (if (and p
                                              (= :alive (:status p))
                                              (= x (:x p))
                                              (= y (:y p)))
                                       (assoc-in st [:players pid :status] :dead)
                                       st))
                                   s
                                   (seq (:players s)))
                           s)))))))))))

(defn game-tick []
  (when (= :playing (:game-status @game-state))
    (swap! game-state update :tick inc)
    (update-bot)

    (let [players (:players @game-state)]
      (when (pos? (count players))
        (let [alive-count (count (filter #(= :alive (:status (val %))) players))
              exit (get-in @game-state [:maze :exit])
              at-exit-count (count (filter #(and (= (:x (val %)) (first exit))
                                                (= (:y (val %)) (second exit))
                                                (= :alive (:status (val %))))
                                          players))]
          (cond
            (zero? alive-count)
            (swap! game-state assoc :game-status :lost)

            (= alive-count at-exit-count)
            (swap! game-state assoc :game-status :won)))))))
