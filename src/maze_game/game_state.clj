(ns maze-game.game-state
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str]))

(def bot-spawn [1 8])
(def player-base-spawn [8 8])

(def game-state
  (atom {:players {}
         :bot {:x (first bot-spawn) :y (second bot-spawn) :target nil}
         :maze {:walls #{}
                :exit [1 1]
                :size [10 10]}
         :game-status :waiting
         :tick 0}))

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
           :bot {:x (first bot-spawn) :y (second bot-spawn) :target nil}
           :maze {:walls #{[0 0] [1 0] [2 0]       [4 0] [5 0] [6 0] [7 0] [8 0] [9 0]
                           [0 1]                                                 [9 1]
                           [0 2]       [2 2] [3 2] [4 2] [5 2]       [7 2]       [9 2]
                           [0 3]                   [4 3]                         [9 3]
                           [0 4] [1 4] [2 4]       [4 4] [5 4] [6 4]       [8 4] [9 4]
                           [0 5]                                                 [9 5]
                           [0 6]       [2 6] [3 6] [4 6] [5 6]       [7 6]       [9 6]
                           [0 7]       [2 7]                         [7 7]       [9 7]
                           [0 8]                   [4 8]       [6 8]             [9 8]
                           [0 9] [1 9] [2 9] [3 9] [4 9] [5 9] [6 9] [7 9] [8 9] [9 9]}
                  :exit [3 0]
                  :size [10 10]}
           :game-status :waiting
           :tick 0}))

(defn in-bounds? [maze x y]
  (let [[w h] (:size maze)]
    (and (<= 0 x) (< x w) (<= 0 y) (< y h))))

(defn wall? [maze x y]
  (contains? (:walls maze) [x y]))

(defn passable? [maze x y]
  (and (in-bounds? maze x y)
       (not (wall? maze x y))))

(defn candidate-spawns []
  (let [[sx sy] player-base-spawn
        offsets [[0 0] [-1 0] [1 0] [0 -1] [0 1]
                 [-1 -1] [1 -1] [-1 1] [1 1]
                 [-2 0] [2 0] [0 -2] [0 2]
                 [-2 -1] [-2 1] [2 -1] [2 1]]]
    (mapv (fn [[dx dy]] [(+ sx dx) (+ sy dy)]) offsets)))

(defn pick-spawn [maze used]
  (let [cands (->> (candidate-spawns)
                   (filter (fn [[x y]] (passable? maze x y)))
                   vec)
        cands (if (seq cands) cands [player-base-spawn])]
    (or (first (remove used cands))
        (first cands))))

(defn add-player [player-id]
  (swap! game-state
         (fn [s]
           (if (contains? (:players s) player-id)
             s
             (let [maze (:maze s)
                   used (set (map (fn [[_ p]] [(:x p) (:y p)]) (:players s)))
                   [x y] (pick-spawn maze used)]
               (assoc-in s [:players player-id]
                         {:x x :y y
                          :status :alive
                          :color (rand-nth ["#FF0000" "#00FF00" "#0000FF" "#FFFF00"])}))))))

(defn move-player [player-id direction]
  (swap! game-state
         (fn [s]
           (if (not= :playing (:game-status s))
             s
             (let [player (get-in s [:players player-id])]
               (if (or (nil? player) (not= :alive (:status player)))
                 s
                 (let [[dx dy] (case direction
                                 "up"    [0 -1]
                                 "down"  [0  1]
                                 "left"  [-1 0]
                                 "right" [1  0]
                                 [0 0])
                       new-x (+ (:x player) dx)
                       new-y (+ (:y player) dy)
                       maze (:maze s)]
                   (if (passable? maze new-x new-y)
                     (-> s
                         (assoc-in [:players player-id :x] new-x)
                         (assoc-in [:players player-id :y] new-y))
                     s))))))))

(defn parse-path [s]
  (mapv (fn [[_ xs ys]]
          [(Integer/parseInt xs) (Integer/parseInt ys)])
        (re-seq #"\((\d+),\s*(\d+)\)" (or s ""))))

(defn start-game! []
  (swap! game-state
         (fn [s]
           (if (= :playing (:game-status s))
             s
             (let [maze (:maze s)
                   players (:players s)
                   cands (->> (candidate-spawns)
                              (filter (fn [[x y]] (passable? maze x y)))
                              vec)
                   cands (if (seq cands) cands [player-base-spawn])
                   pids (vec (keys players))
                   new-players
                   (into {}
                         (map-indexed
                          (fn [i pid]
                            (let [p (get players pid)
                                  [x y] (nth cands (mod i (count cands)))]
                              [pid (-> p
                                       (assoc :x x :y y)
                                       (assoc :status :alive))]))
                          pids))]
               (-> s
                   (assoc :game-status :playing)
                   (assoc :tick 0)
                   (assoc :bot {:x (first bot-spawn) :y (second bot-spawn) :target nil})
                   (assoc :players new-players)))))))

(defn restart-game! []
  (start-game!))

(defn manhattan [x1 y1 x2 y2]
  (+ (Math/abs (long (- x2 x1)))
     (Math/abs (long (- y2 y1)))))

(defn update-bot []
  (let [s @game-state
        bot (:bot s)
        players (:players s)
        alive-players (->> players
                           (filter (fn [[_ p]] (and p (= :alive (:status p)))))
                           vec)]
    (when (seq alive-players)
      (let [bx (:x bot) by (:y bot)
            [player-id player]
            (apply min-key
                   (fn [[_ p]] (manhattan bx by (:x p) (:y p)))
                   alive-players)

            maze (:maze s)

            query (format "((find_path(%d,%d,%d,%d,Path), writeln(Path)) ; writeln([]))."
                          (int bx) (int by)
                          (int (:x player)) (int (:y player)))

            path-str (call-prolog query)
            coords (parse-path path-str)
            next-pos (second coords)]
        (when (and next-pos (= 2 (count next-pos)))
          (let [[nx ny] next-pos
                step (manhattan bx by nx ny)]
            (when (and (= step 1)
                       (passable? maze nx ny))
              (swap! game-state
                     (fn [st]
                       (let [st (assoc st :bot {:x nx :y ny :target player-id})]
                         (reduce (fn [acc [pid p]]
                                   (if (and p
                                            (= :alive (:status p))
                                            (= nx (:x p))
                                            (= ny (:y p)))
                                     (assoc-in acc [:players pid :status] :dead)
                                     acc))
                                 st
                                 (seq (:players st)))))))))))))

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
