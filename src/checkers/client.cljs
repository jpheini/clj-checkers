(ns checkers.client
  (:require [reagent.core :as reagent]
            [taoensso.sente :as sente :refer (cb-success?)]
            [cljs.core.async :refer [chan put! <!]]
            [checkers.rules :as rules])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(def game-state (reagent/atom nil))

(def player-color (reagent/atom nil))

(def dnd-chan (chan))

(def game-id (re-find #"[^/][\w-]+(?=/[\w\.]+$)"
                      (str (aget js/window "location" "pathname"))))

(let [{:keys [ch-recv send-fn]}
      (sente/make-channel-socket! "/chsk" {:type :auto})]
  (def ch-chsk    ch-recv)
  (def chsk-send! send-fn))

(defn send-join! []
  (chsk-send! [:checkers/join game-id] 10000
              (fn [color] (reset! player-color color))))

(defn send-restart! []
  (chsk-send! [:checkers/restart game-id]))

(defn send-play! [from to]
  (chsk-send! [:checkers/play [game-id from to]]))

(defmulti event :id)

(defmethod event :chsk/recv [{:keys [?data]}]
  (when (= :checkers/update (first ?data))
    (reset! game-state (second ?data))))

(defmethod event :chsk/state [_]
  (send-join!))

(defmethod event :chsk/handshake [_])

(defn handle-drop! [from to]
  (print from)
  (print to)
  (let [[from-x from-y] from
        [to-x to-y] to
        piece (get-in (:board @game-state) from)]
    (swap! game-state assoc-in [:board from-x from-y] nil)
    (swap! game-state assoc-in [:board to-x to-y] piece)
    (send-play! from to)))

(defn start-dnd-loop [ch]
  (go-loop [[first-msg-name first-msg-data] (<! ch)]
    (when (= :drag-start first-msg-name)
      (let [dragged-from (:drag-data first-msg-data)
            [msg-name msg-data] (<! ch)
            dropped-to (:drop-data msg-data)]
        (when (= :drag-end msg-name)
          (handle-drop! dragged-from dropped-to))))
    (recur (<! ch))))

(defn drag-piece! [ch drag-data e]
  (set! (.-effectAllowed (.-dataTransfer e)) "move")
  (.setData (.-dataTransfer e) "text/plain" "")
  (set! (.-opacity (.-style (.-target e))) "0.9")
  (put! ch [:drag-start {:drag-data drag-data}]))

(defn drop-piece! [ch drop-data e]
  (set! (.-opacity (.-style (.-target e))) "1")
  (put! ch [:drag-end {:drop-data drop-data}]))

(defn drag-cancel! [ch e]
  (put! ch [:drag-cancel nil]))

(defn piece-view [{:keys [color type]} xy]
  [:div {:draggable true
         :on-drag-start (partial drag-piece! dnd-chan xy)
         :style {:background (if (= :black color)
                               "black" "white")
                 :color (if (= :black (get rules/opponents color))
                          "black" "white")
                 :display "flex"
                 :align-items "center"
                 :justify-content "center"
                 :border-radius "50%"
                 :width "40px"
                 :height "40px"}}
   (when (= type :king) [:span {:style {:pointer-events "none"}} "\u2655"])])

(defn square-view [xy board]
  (let [piece (get-in board xy)]
    [:div {
           :on-drop (partial drop-piece! dnd-chan xy)
           :on-drag-over #(.preventDefault %)
           :on-drag-enter #(.preventDefault %)
           :style {:width "50px"
                   :height "50px"
                   :display "flex"
                   :justify-content "center"
                   :align-items "center"
                   :background-color
                   (if (= :black (rules/square-color xy))
                     "green" "burlywood")}}
     (when piece [piece-view piece xy])]))

(defn board-view [board]
  [:div {:style {:display "flex"
                 :width "400px"
                 :height "400px"
                 :flex-wrap "wrap"}}
   (for [y (range rules/board-size), x (range rules/board-size)]
     ^{:key (str x y)}[square-view [x y] board])])

(defn status-view [turn winner player-color]
  (let [color->string {:white "white" :black "black"}
        turn (get color->string turn)
        winner (get color->string winner)
        player-color (get color->string player-color)]
    (if player-color
      [:table.table.table-condensed
       [:tbody
        [:tr [:td "Your color"] [:td player-color]]
        (if-not winner
          (if (= turn player-color)
            [:tr.alert.alert-info [:td "Turn"] [:td "You!"]]
            [:tr [:td "Turn"] [:td turn]])
          [:tr.alert.alert-success [:td "Winner"] [:td winner]])]]
      [:p.alert.alert-warning {:style {:max-width "20em"}}
       "Cannot join this game. Create a new game to play."])))

(defn control-view [player-color]
  [:form.form {:style {:margin-top "20px"}
               :action "/"
               :method "get"}
   [:div.btn-toolbar {:role "toolbar"}
    (when player-color
      [:a.btn.btn-default {:role "button"
                           :on-click send-restart!}
       "Restart the game"])
    [:button.btn.btn-default {:type "submit" :role "button"}
     "Create a new game"]]])

(defn app-view []
  (let [player-color @player-color
        turn (:turn @game-state)
        winner (:winner @game-state)
        board (:board @game-state)]
    [:div.container {:on-drop (partial drag-cancel! dnd-chan)}
     [:div.row {:style {:margin-bottom "50px"}}
      [:header.text-center
       [:h1
        "CHECKERS"]]]
     [:div.row {:style {:margin-bottom "30px"}}
      (when player-color
        [:div.col-md-5.col-md-offset-2 {:style {:margin-bottom "30px"}}
         [board-view board]])
      [:div.col-md-4 {:style {:max-width "400px"}}
       [:div.row
        [:div.col-md-12
         [status-view turn winner player-color]]]
       [:div.row
        [:div.col-md-12
         [control-view player-color]]]]]
     [:div.row
      [:footer.text-right {:style {:margin-right "20px"}}
       [:small "Copyright (c) 2016, 2017 J-P Heini"]]]]))

(defn mount-root []
  (reagent/render-component [app-view]
                            (.getElementById js/document "app")))

(mount-root)
(sente/start-client-chsk-router! ch-chsk event)
(start-dnd-loop dnd-chan)
