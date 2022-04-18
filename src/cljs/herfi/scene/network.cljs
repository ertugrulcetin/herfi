(ns herfi.scene.network
  (:require
    [applied-science.js-interop :as j]
    [cljs.core.async :as a :refer [<! >! put!]]
    [cljs.core.async.impl.protocols :refer [closed?]]
    [haslett.client :as ws]
    [haslett.format :as format]
    [herfi.character :as common.char]
    [herfi.common.communication :refer [on fire]]
    [herfi.common.config :as config]
    [herfi.scene.api :as api]
    [herfi.scene.db :as db]
    [herfi.scene.ecs :as ecs]
    [herfi.scene.entity.players :as players]
    [msgpack-cljs.core :as msgp])
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]]))

;; TODO IF BROWSER TAB IS NOT ACTIVE, APP SENDS OLD DATA - SO NEED TO FIND SOME SOLUTION!

(defonce world (a/chan (a/sliding-buffer 1)))
(defonce world-buffer (a/chan (a/sliding-buffer 10)))
(defonce latest-world-snapshot (atom nil))

(defonce ws nil)
(defonce sink (atom nil))

(defn- get-buffer [ch]
  (or (some-> ch
              (j/get-in [:buf :buf :arr])
              (js->clj)
              (#(sort-by :id (fn [x y] (compare y x)) %)))
      []))

(def binary
  (reify format/Format
    (read [_ s] (msgp/unpack s))

    (write [_ v] (msgp/pack v))))

(defn- ws-connect []
  (ws/connect (str config/ws-url (str "?username=" (db/get :username)))
              {:sink (a/chan 1 (map #(assoc % :timestamp (js/Date.now))))
               :format binary}))

(defn connect []
  (go-loop [ws* (ws-connect)]
    (set! ws ws*)
    (let [stream (<! ws*)
          socket (:socket stream)
          on-close (j/get socket :onclose)]
      (if-not (ws/connected? stream)
        (do
          (ws/close stream)
          (<! (a/timeout 1000))
          (recur (ws-connect)))
        (do
          (println "Connected!")
          (reset! sink (:sink stream))
          (j/assoc! socket
                    :onclose (fn [e]
                               (on-close e)
                               (fire :ws-conn-lost? true)
                               (println "Connection closed!"))
                    :onerror (fn [] (.close socket))))))))

(defn send-init-message []
  (go (>! (:sink (<! ws)) {:init? true})))

(defn- send-ready-message [sink]
  (go (>! sink {:ready? true})))

(defn send-to-server [data]
  (let [sink @sink]
    (if (and sink (not (closed? sink)))
      (a/put! sink data)
      (js/console.error (str "Could not send data to server. " (pr-str data))))))

(defn send-states-to-server []
  (let [tick-rate 30
        sleep (/ 1000 tick-rate)]
    (go-loop [stream (<! ws)
              sink (:sink stream)]
      (if (closed? sink)
        ;; TODO if closed try to figure out re-connect
        (println "Closed")
        (let [robot (db/get-entity :robot)]
          (<! (a/timeout sleep))
          (>! sink {:state (:state robot)
                    :position (:position robot)
                    :rotation (:rotation robot)
                    :collision? (:collision? robot)
                    :focus? (not (.-hidden js/document))})
          (recur stream sink))))))

(defn process-server-events []
  (go-loop [stream (<! ws)]
    (if-not (ws/connected? stream)
      (println "Closed")
      (let [data (<! (:source stream))]
        (cond
          (:init data) (do
                         (ecs/swap! :robot merge (:init data))
                         (send-ready-message (:sink stream)))
          (:ready-ack? data) (do
                               (send-states-to-server)
                               (doseq [[k v] (:world data)]
                                 (players/create-player k (:position v)))
                               (fire :all-ready true))
          (:new-user data) (players/create-player (-> data :new-user :username) (-> data :new-user :position))
          (:user-exit data) (ecs/remove-entity (:user-exit data))
          (:skill-failed data) (do
                                 (fire :skill-failed true)
                                 (ecs/swap! :robot assoc :state :idle))
          (:not-enough-mana data) (do
                                    (fire :not-enough-mana true)
                                    (ecs/swap! :robot assoc :state :idle))
          (:not-enough-health data) (fire :not-enough-health-for-tp true)
          (:enemy-damage data) (do
                                 (fire :enemy-damage data)
                                 (db/set-entity :robot :mana (:mana data)))
          (:got-damage data) (do
                               (when (common.char/magic-skills (:skill data))
                                 (ecs/add-component (db/get-entity :robot) :particle {:name (:skill data)
                                                                                      :duration 4000}))
                               (fire :got-damage data)
                               (db/set-entity :robot :health (:health data))
                               (when (zero? (:health data))
                                 (ecs/swap! :robot assoc :state :die))
                               (when-let [speed (:speed data)]
                                 (db/set-entity :robot :speed speed)
                                 (j/call (db/get :camera-shake) :shake)))
          (:enemies-got-hit data) (do
                                    (when-let [enemy (some-> :enemy data db/get-entity)]
                                      (when (common.char/magic-skills (:skill data))
                                        (ecs/add-component enemy :particle {:name (:skill data)
                                                                            :duration 4000})))
                                    (when (:slaughtered data)
                                      (fire :killed-by-info data)))
          (:shield data) (dotimes [_ 50] (players/add-storm-line-particle (db/get-entity :robot)))
          (:enemies-got-shield data) (when-let [enemy (some-> :enemy data db/get-entity)]
                                       (dotimes [_ 50] (players/add-storm-line-particle enemy)))
          (:potion data) (do
                           (db/set-entity :robot :health (:health data))
                           (db/set-entity :robot :mana (:mana data))
                           (fire :potion data))
          (:magic-has-no-effect? data) (db/set-entity :robot :speed (:speed data))
          (:teleport data) (do
                             (ecs/swap! :robot assoc :position (:teleport data) :mana (:mana data))
                             (fire :potion data))
          (:respawn data) (do
                            (ecs/swap! :robot merge (:respawn data) {:loading-skills {} :target nil})
                            (fire :respawn-successful true))
          (:chat-message data) (fire :add-message-to-chat data)
          (:world data) (let [snapshot (:world data)]
                          (put! world snapshot)
                          (put! world-buffer snapshot)
                          (reset! latest-world-snapshot snapshot)
                          (fire :world snapshot)))
        (recur stream)))))

(let [time (atom (js/performance.now))
      temp-dir (api/vec3)]
  (defn- process-world-snapshot []
    (let [now (js/performance.now)
          elapsed-time (/ (- now @time) 1000)
          username (db/get :username)]
      (reset! time now)
      (if-let [world (a/poll! world)]
        (doseq [[k v] (dissoc world  username)]
          ;; TODO remove (keyword k)
          (let [entity-id (keyword k)]
            (ecs/swap! entity-id assoc
                       :position (:position v)
                       :rotation (:rotation v)
                       :state (if (:focus? v) (:state v) :idle)
                       :health (:health v))))
        (doseq [world (dissoc @latest-world-snapshot username)]
          (let [entity-id (first world)
                entity (second world)
                obj (db/get-entity-obj! entity-id)]
            (if (and obj (:focus? entity) (not= :die (:state entity)))
              (do
                (when-not (:collision? entity)
                  (if (= :walk (:state entity))
                    (let [char-speed (:speed entity)
                          acc (* elapsed-time char-speed)
                          _ (j/call-in obj [:position :setY] (-> entity :position second))
                          dir (api/normalize (api/get-world-direction obj temp-dir))]
                      (j/call-in obj [:position :add] (api/multiply-scalar dir acc)))
                    (ecs/swap! entity-id assoc :position (:position entity))))
                (ecs/swap! entity-id assoc
                           :rotation (:rotation entity)
                           :state (:state entity)
                           :health (:health entity)))
              (ecs/swap! entity-id assoc :state :idle))))))
    (js/setTimeout process-world-snapshot (/ 1000 60))))

(defn- init-listeners []
  (on :respawn (fn [_] (send-to-server {:respawn? true})))
  (on :chat-message (fn [msg] (send-to-server {:chat-message msg}))))

(defn start []
  (when config/multiplayer?
    (connect)
    (send-init-message)
    (process-server-events)
    (process-world-snapshot)
    (init-listeners)))
