(ns checkers.server
  (:require [org.httpkit.server :refer [run-server]]
            [environ.core :refer [env]]
            [ring.util.response :refer [redirect]]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :refer [resources not-found]]
            [taoensso.sente :refer [make-channel-socket!
                                    start-server-chsk-router!]]
            [taoensso.sente.server-adapters.http-kit :refer (get-sch-adapter)]
            [checkers.rules :as rules]))

(def games (atom {}))
(def channel-socket (make-channel-socket! (get-sch-adapter) {}))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn new-game! []
  (let [game-id (uuid)]
    (swap! games assoc game-id {:players [nil nil]
                                :state (rules/initial-game-state)})
    game-id))

(defn send-update! [uid data]
  (let [msg [:checkers/update data]]
    ((:send-fn channel-socket) uid msg)))

(defn broadcast-state! [game-id]
  (doseq [player (filter some? (get-in @games [game-id :players]))]
    (send-update! player (get-in @games [game-id :state]))))

(defn player-color [game-id uid]
  (let [players (get-in @games [game-id :players])]
    (get (zipmap players rules/players) uid
         (first (flatten (filter (comp nil? second)
                                 (map vector rules/players players)))))))

(defmulti event :id)

(defmethod event :checkers/play [{:keys [uid ?data]}]
  (let [[game-id from to] ?data
        player-color (player-color game-id uid)]
    (swap! games update-in [game-id :state] rules/play from to player-color)
    (broadcast-state! game-id)))

(defmethod event :checkers/restart [{:keys [?data]}]
  (let [game-id ?data]
    (swap! games assoc-in [game-id :state] (rules/initial-game-state))
    (broadcast-state! game-id)))

(defmethod event :checkers/join [{:keys [uid ?data ?reply-fn]}]
  (let [game-id ?data
        player-color (player-color game-id uid)]
    (if player-color
      (do
        (swap! games
               assoc-in [game-id :players
                         (get (zipmap rules/players [0 1]) player-color)]
               uid)
        (send-update! uid (get-in @games [game-id :state]))
        (?reply-fn player-color))
      (?reply-fn false))))

(defmethod event :chsk/uidport-close [{:keys [uid]}]
  (reset! games (->> @games
                     (into {} (map (fn [game]
                                     {(key game)
                                      (update (val game) :players
                                              (fn [players]
                                                (vec (map #(if (= uid %) nil %)
                                                          players))))})))
                     (into {} (filter #(some (comp not nil?)
                                             (:players (val %))))))))

(defmethod event :chsk/uidport-open [_])

(defmethod event :chsk/ws-ping [_])

(defroutes routes
  (GET "/" [] (redirect (str "/games/" (new-game!) "/index.html")))
  (GET  "/chsk" request ((:ajax-get-or-ws-handshake-fn channel-socket) request))
  (POST "/chsk" request ((:ajax-post-fn channel-socket) request))
  (resources "/games/:id/")
  (not-found "<p>Page not found.</p>"))

(defn wrap-authenticate [handler]
  (fn [request]
    (if-not (-> request :session :uid)
      (assoc-in (handler request) [:session :uid] (uuid))
      (handler request))))

(def app
  (-> routes
      wrap-keyword-params
      wrap-params
      wrap-anti-forgery
      wrap-authenticate
      wrap-session))

(defn -main []
  (let [port (Integer. (or (env :port) 5000))]
    (start-server-chsk-router! (:ch-recv channel-socket) event)
    (run-server app {:port port})))
