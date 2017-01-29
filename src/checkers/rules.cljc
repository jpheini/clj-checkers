(ns checkers.rules
  (:require [clojure.set :as set]))

(def board-size 8)
(def opponents {:black :white, :white :black})
(def players (vec (keys opponents)))
(def up [[-1 -1] [1 -1]])
(def down [[-1 1] [1 1]])
(def all-directions (vec (concat up down)))

(defn generate-board []
  (vec (for [x (range board-size)]
         (vec (for [y (range board-size)]
                (when (odd? (+ x y))
                  (cond (< y 3)
                        {:color :black, :type :man}
                        (>= y (- board-size 3))
                        {:color :white, :type :man})))))))

(defn square-color [[x y]]
  (if (odd? (+ x y)) :black :white))

(defn neighbors
  ([xy] (neighbors all-directions xy))
  ([xy player-color player-type]
   (let [deltas (cond (= player-type :king) all-directions
                      (= player-color :white) up
                      (= player-color :black) down)]
     (neighbors deltas xy)))
  ([deltas xy]
   (filter (fn [[x y]]
             (and (every? #(< -1 % board-size) [x y])
                  (odd? (+ x y))))
           (map #(vec (map + xy %))
                deltas))))

(defn delta [[a1 a2] [b1 b2]]
  [(- a1 b1) (- a2 b2)])

(defn piece-color [board xy]
  (:color (get-in board xy)))

(defn jump-positions [board from player-color player-type]
  (let [valid-neighbors (neighbors from player-color player-type)
        opponent-color (player-color opponents)
        opponent-neighbors (filter #(= opponent-color (piece-color board %))
                                   valid-neighbors)]
    (filter #(nil? (get-in board %))
            (reduce into () (map #(neighbors (vector (delta % from)) %)
                                 opponent-neighbors)))))

(defn captured-piece-position [board from to player-color player-type]
  (let [neighbors-of-from (set (neighbors from))
        neighbors-of-to (set (neighbors to))]
    (first (set/intersection neighbors-of-from neighbors-of-to))))

(defn can-jump? [board from player-color player-type]
  (seq (jump-positions board from player-color player-type)))

(defn is-jumping? [board from to player-color player-type]
  (seq (set/intersection #{to}
                         (set (jump-positions board from
                                              player-color player-type)))))

(defn moves-to-valid-square? [board from to player-color player-type]
  (let [valid-neighbors (neighbors from player-color player-type)]
    (and (nil? (get-in board to))
         (some #(= to %) valid-neighbors))))

(defn squares-held-by-player [board player-color]
  (for [[x row] (map-indexed vector board)
        [y square] (map-indexed vector row)
        :when (= player-color (:color square))]
    [x y]))

(defn cannot-jump? [board player-color]
  (not-any? (fn [[x y :as square]] (can-jump? board square player-color
                                   (get-in board [x y :type])))
            (squares-held-by-player board player-color)))

(defn can-move? [board from player-color player-type]
  (let [valid-neighbors (neighbors from player-color player-type)]
    (some #(nil? (get-in board %)) valid-neighbors)))

(defn cannot-move-nor-jump? [board player-color]
  (and (cannot-jump? board player-color)
       (not-any? (fn [[x y :as square]]
                   (can-move? board square player-color
                              (get-in board [x y :type])))
                 (squares-held-by-player board player-color))))

(defn valid-move? [board must-jump-from from to player-color player-type]
  (let [cannot-jump? (cannot-jump? board player-color)
        is-jumping? (is-jumping? board from to player-color player-type)
        moves-to-valid-square? (moves-to-valid-square? board from to
                                                       player-color
                                                       player-type)]
    (or (and (not must-jump-from) (or is-jumping?
                                      (and cannot-jump?
                                           moves-to-valid-square?)))
        (and (= from must-jump-from) is-jumping?))))

(defn cannot-move-or-no-pieces-left? [board player-color]
  (or (cannot-move-nor-jump? board player-color)
      (not (seq (filter #(= (:color %) player-color) (flatten board))))))

(defn winner [board]
  (cond (cannot-move-or-no-pieces-left? board :white) :black
        (cannot-move-or-no-pieces-left? board :black) :white
        :else nil))

(defn execute-play [game-state from to player-color player-type]
  (let [piece (get-in (:board game-state) from)
        [from-x from-y] from
        [to-x to-y] to
        piece-promoted? (or (and (zero? to-y)
                                 (= :white player-color))
                            (and (= to-y (dec board-size))
                                 (= :black player-color)))
        captured-piece-position (captured-piece-position (:board game-state)
                                                         from to
                                                         player-color
                                                         player-type)
        player-jumped? (seq captured-piece-position)
        [captured-piece-x captured-piece-y] captured-piece-position]
    (as-> game-state state
      (assoc-in state [:board from-x from-y] nil)
      (assoc-in state [:board to-x to-y] piece)
      (if player-jumped?
        (assoc-in state [:board captured-piece-x captured-piece-y] nil)
        state)
      (if piece-promoted?
        (assoc-in state [:board to-x to-y] (assoc piece :type :king))
        state)
      (if (and player-jumped?
               (not piece-promoted?)
               (can-jump? (:board state) to player-color :king))
        (assoc state :must-jump-from to)
        (-> state
            (assoc :turn (player-color opponents))
            (assoc :must-jump-from nil)))
      (assoc state :winner (winner (:board state))))))

(defn has-turn? [game-state player-color]
  (= (:turn game-state) player-color))

(defn moves-own-piece? [game-state from player-color]
  (= (:color (get-in (:board game-state) from)) player-color))

(defn play [game-state from to player-color]
  (let [board (:board game-state)
        must-jump-from (:must-jump-from game-state)
        piece (get-in board from)
        player-type (if must-jump-from :king (:type piece))]
    (if (and (has-turn? game-state player-color)
             (moves-own-piece? game-state from player-color)
             (valid-move? board must-jump-from from to
                          player-color player-type))
        (execute-play game-state from to player-color player-type)
        game-state)))

(defn initial-game-state []
  {:board (generate-board)
   :turn (first players)
   :must-jump-from nil
   :winner nil})
