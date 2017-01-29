(ns checkers.rules-test
  (:require  #?(:clj [clojure.test :refer :all]
                :cljs [cljs.test :refer :all :include-macros true])
             [checkers.pdn-fen :refer [game-position]
              :rename {game-position pos}]
             [checkers.rules :refer [play initial-game-state]]))

(deftest black-player-starts
  (let [state (initial-game-state)]
    (is (= :black (:turn state)))))

(deftest player-can-move-when-in-turn
  (let [state1 (initial-game-state)
        state2 (play state1 [1 2] [2 3] :black)]
    (is (= {:color :black :type :man} (get-in state2 [:board 2 3])))))

(deftest player-cannot-move-on-opponents-turn
  (let [state1 (initial-game-state)
        state2 (play state1 [0 5] [1 4] :white)]
    (is (= state1 state2))))

(deftest white-plays-after-black
  (let [state1 (initial-game-state)
        state2 (play state1 [1 2] [2 3] :black)]
    (is (= :white (:turn state2)))))

(deftest player-cannot-move-to-occupied-square
  (let [state1 (initial-game-state)
        state2 (play state1 [0 1] [2 1] :black)]
    (is (= state1 state2))))

(deftest fen-generates-starting-position
  (let [state1 (pos (str "B:W21,22,23,24,25,26,27,28,29,30,31,32:"
                         "B1,2,3,4,5,6,7,8,9,10,11,12"))
        state2 (initial-game-state)]
    (is (= state1 state2))))

(deftest man-cannot-move-backward
  (let [state1 (pos (str "W:W18,21,23,24,25,26,27,28,29,30,31,32:"
                         "B1,2,3,4,5,6,7,8,9,10,11,16"))
        state2 (play state1 [3 4] [2 5] :white)]
    (is (= state1 state2))))

(deftest jumping-captures-opponent-piece
  (let [state1 (pos "W:W18,21,22,23,26,28,29,30,31:B2,3,4,5,6,7,9,12,13,27")
        state2 (play state1 [4 7] [6 5] :white)]
    (is (nil? (get-in state2 [:board 5 6])))))

(deftest jumping-is-mandatory
  (let [state1 (pos "W:W18,21,22,23,26,28,29,30,31:B2,3,4,5,6,7,9,12,13,27")
        state2 (play state1 [7 6] [6 5] :white)]
    (is (= state1 state2))))

(deftest man-jumps-forward
  (let [state1 (pos "W:W18,21,22,23,26,28,29,30,31:B2,3,4,5,6,7,9,12,13,27")
        state2 (play state1 [4 7] [6 5] :white)]
    (is (= {:color :white :type :man} (get-in state2 [:board 6 5])))))

(deftest man-cannot-jump-backward
  (let [state1 (pos "W:W18,21,22,23,26,28,29,30,31:B2,3,4,5,6,7,9,12,13,27")
        state2 (play state1 [4 5] [6 7] :white)]
    (is (= state1 state2))))

(deftest man-can-multijump-backward
  (let [state (-> (pos "B:W6,14,23,24,25,26,28,30,32:B1,2,7,8,13,16,19")
                  (play [3 0] [1 2] :black)
                  (play [1 2] [3 4] :black)
                  (play [3 4] [5 6] :black)
                  (play [5 6] [7 4] :black))]
    (is (= {:color :black :type :man} (get-in state [:board 7 4])))))

(deftest all-available-jumps-must-be-made
  (let [state1 (pos (str "B:W11,18,19,21,22,23,26,29,30,31,32:"
                         "B2,3,4,5,6,7,8,9,12,13"))
        state2 (play state1 [6 1] [4 3] :black)]
    (is (= :black (:turn state2)))))

(deftest man-reaching-kings-row-will-be-kinged
  (let [state1 (pos "B:W9,10,19,21,25,28,30:B2,3,7,12,13,14,27")
        state2 (play state1 [5 6] [4 7] :black)]
    (is (= {:color :black :type :king} (get-in state2 [:board 4 7])))))

(deftest king-can-move-backward
  (let [state1 (pos "B:WK10,14,20,23,28:B2,5,11,K29,K31")
        state2 (play state1 [0 7] [1 6] :black)]
    (is (= {:color :black :type :king} (get-in state2 [:board 1 6])))))

(deftest king-can-jump-backward
  (let [state1 (pos "W:WK3,14,20,23,28:B2,5,7,11,K29,K31")
        state2 (play state1 [5 0] [3 2] :white)]
    (is (= {:color :white :type :king} (get-in state2 [:board 3 2])))))

(deftest jumping-into-kings-row-will-terminate-multijump
  (let [state (-> (pos "W:W19,20,21,23,28:B2,5,7,8,11,16,17,22,K29")
                  (play [5 4] [7 2] :white)
                  (play [7 2] [5 0] :white))]
    (is (= :black (:turn state)))))

(deftest player-wins-if-no-opponents-pieces-left
  (let [state (-> (pos "B:W14,15,K24:BK17,26,27")
                  (play [1 4] [3 2] :black)
                  (play [3 2] [5 4] :black)
                  (play [5 4] [7 6] :black))]
    (is (= :black (:winner state)))))

(deftest player-wins-if-opponent-has-no-legal-moves
  (let [state1 (pos "B:WK1,K5,6,9,10,13:B2,7,27")
        state2 (play state1 [4 1] [2 3] :black)]
    (is (= :black (:winner state2)))))
