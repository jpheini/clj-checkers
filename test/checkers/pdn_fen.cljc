(ns checkers.pdn-fen
  (:require [checkers.rules :as rules]
            [instaparse.core :as insta]))

;; The following grammar is adapted from http://pdn.fmjd.org/fen.html.
;; The implementation below is actually closer to PDN 2.0 than PDN 3.0
;; described in the aforementioned link: using question mark for unknown color
;; and dash for ranges are not supported.
(def pdn2-fen-parse-tree
  (insta/parser
   "(* Productions *)
    Fen                   : COLOR NumericSquares DOT?
    NumericSquares        : (<COLON> COLOR NumericSquareSequence)+
    NumericSquareSequence : NumericSquareRange (<COMMA> NumericSquareRange)*
    NumericSquareRange    : KING? NUMSQUARE?

    (* Tokens *)
    COLOR                 : #'[WB]'
    KING                  : 'K'
    NUMSQUARE             : #'([1-9][0-9]*)|(0[1-9][0-9]*)|0'
    COMMA                 : ','
    COLON                 : ':'
    DOT                   : '.'"))

(defn transform-color [[_ color]]
  (case color
    "W" :white
    "B" :black))

(defn rank->coords [rank]
  (let [y (int (/ (dec (* rank 2)) 8))
        x (- (mod (dec (* rank 2)) 8) (mod y 2))]
    [x y]))

(defn transform-positions [color squares state]
  (doseq [square (drop 1 squares)]
    (let [type (if (= (count square) 3) :king :man)
          rank (bigint (last (last square)))
          [x y] (rank->coords rank)]
      (swap! state assoc-in [:board x y] {:color color :type type}))))

(defn transform [fen state]
  (let [turn (transform-color (second fen))
        player1 (nth (nth fen 2) 2)
        player1-color (transform-color (nth (nth fen 2) 1))
        player2 (nth (nth fen 2) 4)
        player2-color (transform-color (nth (nth fen 2) 3))]
    (swap! state assoc :turn turn)
    (transform-positions player1-color player1 state)
    (transform-positions player2-color player2 state)))

(defn empty-board []
  (vec (for [x (range rules/board-size)]
         (vec (for [y (range rules/board-size)]
                nil)))))

(defn game-position [fen-tag]
  (let [game-state (atom (rules/initial-game-state))
        fen-parse-tree (pdn2-fen-parse-tree fen-tag)]
    (swap! game-state assoc :board (empty-board))
    (transform fen-parse-tree game-state)
    @game-state))
