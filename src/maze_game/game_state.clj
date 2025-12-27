(ns maze-game.game-state
  (:require [clojure.java.shell :as shell]))

(def bot-spawn [1 8])
(def player-base-spawn [8 8])

(def game-state
  (ref {:players {}
        :bot {:x (first bot-spawn)
              :y (second bot-spawn)
              :target nil
              :path []
              :path-to nil}
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
  (dosync
    (ref-set game-state
             {:players {}
              :bot {:x (first bot-spawn) :y (second bot-spawn)
                    :target nil :path [] :path-to nil}
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
              :tick 0})))

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
  (dosync
    (alter game-state
           (fn [s]
             (if (contains? (:players s) player-id)
               s
               (let [maze (:maze s)
                     used (set (map (fn [[_ p]] [(:x p) (:y p)]) (:players s)))
                     [x y] (pick-spawn maze used)]
                 (assoc-in s [:players player-id]
                           {:x x :y y
                            :status :alive
                            :color (rand-nth ["#FF0000" "#00FF00" "#0000FF" "#FFFF00"])})))))))

(defn remove-player! [player-id]
  (dosync
    (alter game-state update :players dissoc player-id)))

(defn move-player [player-id direction]
  (dosync
    (alter game-state
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
                       s)))))))))

(defn parse-path [s]
  (mapv (fn [[_ xs ys]]
          [(Integer/parseInt xs) (Integer/parseInt ys)])
        (re-seq #"\((\d+),\s*(\d+)\)" (or s ""))))

(defn start-game! []
  (dosync
    (alter game-state
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
                     (assoc :bot {:x (first bot-spawn) :y (second bot-spawn)
                                  :target nil :path [] :path-to nil})
                     (assoc :players new-players))))))))

(defn restart-game! []
  (start-game!))

(defn manhattan [x1 y1 x2 y2]
  (+ (Math/abs (long (- x2 x1)))
     (Math/abs (long (- y2 y1)))))

(defn choose-target [s]
  (let [bot (:bot s)
        bx (:x bot)
        by (:y bot)
        alive (->> (:players s)
                   (filter (fn [[_ p]] (and p (= :alive (:status p)))))
                   vec)]
    (when (seq alive)
      (apply min-key
             (fn [[_ p]] (manhattan bx by (:x p) (:y p)))
             alive))))

(defn compute-path-prolog [bx by tx ty]
  (try
    (let [goal (format "((find_path(%d,%d,%d,%d,Path), writeln(Path)) ; writeln([]))."
                       (int bx) (int by) (int tx) (int ty))
          out  (call-prolog goal)]
      (vec (parse-path out)))
    (catch Throwable _t
      [])))

(defn update-bot []
  (let [snapshot (dosync @game-state)]
    (when (= :playing (:game-status snapshot))
      (when-let [[player-id player] (choose-target snapshot)]
        (let [maze (:maze snapshot)
              bot  (:bot snapshot)
              bx   (:x bot)
              by   (:y bot)
              tx   (:x player)
              ty   (:y player)

              cached-path (vec (get bot :path []))
              cached-to   (get bot :path-to nil)

              cache-ok? (and (seq cached-path)
                             (= cached-to [tx ty])
                             (= (first cached-path) [bx by]))

              path (if cache-ok?
                     cached-path
                     (compute-path-prolog bx by tx ty))

              next-pos (second path)]
          (when (and (vector? next-pos) (= 2 (count next-pos)))
            (let [[nx ny] next-pos
                  step (manhattan bx by nx ny)]
              (when (and (= step 1)
                         (passable? maze nx ny))
                (dosync
                  (alter game-state
                         (fn [st]
                           (if (not= :playing (:game-status st))
                             st
                             (let [trimmed (if (> (count path) 1)
                                             (subvec (vec path) 1)
                                             [])
                                   st1 (assoc st :bot {:x nx :y ny
                                                       :target player-id
                                                       :path trimmed
                                                       :path-to [tx ty]})]
                               (reduce (fn [acc [pid p]]
                                         (if (and p
                                                  (= :alive (:status p))
                                                  (= nx (:x p))
                                                  (= ny (:y p)))
                                           (assoc-in acc [:players pid :status] :dead)
                                           acc))
                                       st1
                                       (seq (:players st1))))))))))))))))

(defn game-tick []
  (let [did-tick?
        (dosync
          (when (= :playing (:game-status @game-state))
            (alter game-state update :tick inc)
            true))]
    (when did-tick?
      (update-bot)
      (dosync
        (alter game-state
               (fn [s]
                 (if (not= :playing (:game-status s))
                   s
                   (let [players (:players s)]
                     (if (empty? players)
                       s
                       (let [alive-count (count (filter (fn [[_ p]]
                                                          (and p (= :alive (:status p))))
                                                        players))
                             [ex ey] (get-in s [:maze :exit])
                             at-exit-count (count (filter (fn [[_ p]]
                                                            (and p
                                                                 (= :alive (:status p))
                                                                 (= (:x p) ex)
                                                                 (= (:y p) ey)))
                                                          players))]
                         (cond
                           (zero? alive-count) (assoc s :game-status :lost)
                           (= alive-count at-exit-count) (assoc s :game-status :won)
                           :else s)))))))))))
