(ns herfi.routes.home
  (:require
    [aleph.http :as http]
    [chime.core :as ch]
    [clojure.core.async :as a]
    [clojure.pprint :as pp]
    [clojure.string :as str]
    [herfi.character :refer [skills magic-skills]]
    [herfi.layout :as layout]
    [herfi.middleware :as middleware]
    [manifold.deferred :as d]
    [manifold.stream :as s]
    [mount.core :as mount]
    [msgpack.clojure-extensions]
    [msgpack.core :as msg]
    [ring.util.response])
  (:import
    (java.time
      Instant)
    (java.util.concurrent
      ExecutorService
      Executors
      TimeUnit)))

(def conns (atom {}))
(def ready-conns (atom []))

(def world-buffer (a/chan (a/sliding-buffer 20)))
(def world (atom {}))

(def events
  (s/stream* {:permanent? true
              :buffer-size 1024}))

(def send-snapshot-count 20)
(def world-tick-rate (/ 1000 send-snapshot-count))

(def map-range 50)

(defn consume-client-event [username f stream]
  (d/loop []
    (d/chain
      (s/take! stream ::drained)

      (fn [data]
        (if (identical? ::drained data)
          ::drained
          (try
            (let [now (Instant/now)
                  data (msg/unpack data)
                  ping (- (.toEpochMilli now) (:timestamp data))
                  data (dissoc data :timestamp)]
              (swap! world assoc-in [username :ping] ping)
              (f data))
            (catch Exception e
              (println "Could not proceed event.")
              (.printStackTrace e)))))

      (fn [result]
        (when-not (identical? ::drained result)
          (d/recur))))))

(defn- send! [s v]
  (s/put! s (msg/pack v)))

(defn- predict-damage [s e]
  (first (shuffle (range s (inc e)))))

(defn- schedule-magic-effect-losing [{:keys [enemy socket]}]
  (let [now (Instant/now)]
    (ch/chime-at [(.plusSeconds now 3)]
                 (fn [_])
                 {:on-finished (bound-fn []
                                 (when (get @world enemy)
                                   (swap! world assoc-in [enemy :speed] 10)
                                   (send! socket {:magic-has-no-effect? true
                                                  :speed 10})))})))

(defn- schedule-terminate-shield [username]
  (let [now (Instant/now)]
    (ch/chime-at [(.plusSeconds now (* 60 3))]
                 (fn [_])
                 {:on-finished (bound-fn []
                                 (when (get @world username)
                                   (swap! world assoc-in [username :shield] false)))})))

(defn- process-magic-skill [{:keys [username socket skill mana selected-character world*]}]
  (let [enemy (get world* selected-character)
        enemy-socket (get @conns selected-character)
        {:keys [health shield]} enemy
        required-mana (-> skills skill :required-mana)
        prob? (fn [prob] (< (rand) prob))]
    (cond
      (or (nil? enemy) (zero? health))
      (send! socket {:skill-failed true})

      (< mana required-mana)
      (send! socket {:not-enough-mana true})

      :else
      (let [damage (case skill
                     :attack-flame (predict-damage 30 35)
                     :attack-ice (predict-damage 25 30)
                     (predict-damage 20 25))
            damage (if shield (int (* damage 0.7)) damage)
            health (max (- health damage) 0)
            mana (- mana required-mana)
            slow-down? (and (= skill :attack-ice) (prob? 0.33))
            freeze? (and (= skill :attack-light) (prob? 0.25))]
        (swap! world (fn [world]
                       (cond-> world
                         true (assoc-in [username :mana] mana)
                         (zero? health) (update-in [username :kills] inc)
                         true (assoc-in [selected-character :health] health)
                         (zero? health) (assoc-in [selected-character :state] :die)
                         (zero? health) (update-in [selected-character :deaths] inc)
                         slow-down? (assoc-in [selected-character :speed] 3)
                         freeze? (assoc-in [selected-character :speed] 0))))
        (send! socket {:enemy-damage damage
                       :enemy selected-character
                       :mana mana})
        (send! enemy-socket (cond-> {:got-damage damage
                                     :health health
                                     :skill skill
                                     :from username}
                              slow-down? (assoc :speed 3)
                              freeze? (assoc :speed 0)))
        (doseq [s @ready-conns]
          (send! s (cond-> {:enemies-got-hit true
                            :enemy selected-character
                            :skill skill}
                     (zero? health) (assoc :killer username
                                           :slaughtered selected-character))))
        (when (or freeze? slow-down?)
          (schedule-magic-effect-losing {:enemy selected-character
                                         :socket enemy-socket}))))))

(defn process-punch-skill [{:keys [username socket skill mana selected-character world*]}]
  (let [enemy (get world* selected-character)
        enemy-socket (get @conns selected-character)
        {:keys [health shield]} enemy
        required-mana (-> skills skill :required-mana)]
    (cond
      (or (nil? enemy) (zero? health))
      (send! socket {:skill-failed true})

      (< mana required-mana)
      (send! socket {:not-enough-mana true})

      :else
      (let [damage (predict-damage 5 15)
            damage (if shield (int (* damage 0.7)) damage)
            health (max (- health damage) 0)
            mana (- mana required-mana)]
        (swap! world (fn [world]
                       (cond-> world
                         true (assoc-in [username :mana] mana)
                         (zero? health) (update-in [username :kills] inc)
                         true (assoc-in [selected-character :health] health)
                         (zero? health) (assoc-in [selected-character :state] :die)
                         (zero? health) (update-in [selected-character :deaths] inc))))
        (send! socket {:enemy-damage damage
                       :enemy selected-character
                       :mana mana})
        (send! enemy-socket (cond-> {:got-damage damage
                                     :health health
                                     :skill skill
                                     :from username}))
        (doseq [s @ready-conns]
          (send! s (cond-> {:enemies-got-hit true
                            :enemy selected-character
                            :skill skill}
                     (zero? health) (assoc :killer username
                                           :slaughtered selected-character))))))))

(defn- process-potions [{:keys [socket username skill health mana]}]
  (let [health (if (= skill :hp-potion) (min (+ health 10) 100) health)
        mana (if (= skill :mp-potion) (min (+ mana 10) 100) mana)]
    (send! socket {:potion true
                   :health health
                   :mana mana})
    (swap! world (fn [world]
                   (-> world
                       (assoc-in [username :health] health)
                       (assoc-in [username :mana] mana))))))

(defn- process-shield-skill [{:keys [socket username mana skill]}]
  (let [required-mana (-> skills skill :required-mana)]
    (if (< mana required-mana)
      (send! socket {:not-enough-mana true})
      (let [mana (- mana required-mana)]
        (send! socket {:shield true
                       :mana mana})
        (doseq [s @ready-conns]
          (send! s {:enemies-got-shield true
                    :enemy username}))
        (schedule-terminate-shield username)
        (swap! world (fn [world]
                       (-> world
                           (assoc-in [username :shield] true)
                           (assoc-in [username :mana] mana))))))))

(defn- process-teleport-skill [{:keys [socket username health mana skill]}]
  (let [required-mana (-> skills skill :required-mana)]
    (cond
      (< mana required-mana)
      (send! socket {:not-enough-mana true})

      (< health 35)
      (send! socket {:not-enough-health true})

      :else (let [new-pos [(* (Math/random) map-range) 0.01 (* (Math/random) map-range)]]
              (send! socket {:teleport new-pos
                             :mana (- mana required-mana)})
              (swap! world assoc-in [username :position] new-pos)))))

(defn- process-skill [data]
  (let [world @world
        {:keys [username skill selected-character]} data
        socket (get @conns username)
        user-data (get world username)
        {:keys [health mana]} user-data]
    (cond
      (zero? health) (send! socket {:skill-failed true})
      (magic-skills skill) (process-magic-skill {:username username
                                                 :socket socket
                                                 :skill skill
                                                 :mana mana
                                                 :selected-character selected-character
                                                 :world* world})
      (= :punch skill) (process-punch-skill {:username username
                                             :socket socket
                                             :skill skill
                                             :mana mana
                                             :selected-character selected-character
                                             :world* world})
      (= :shield skill) (process-shield-skill {:username username
                                               :socket socket
                                               :skill skill
                                               :mana mana})
      (= :teleport skill) (process-teleport-skill {:username username
                                                   :socket socket
                                                   :skill skill
                                                   :health health
                                                   :mana mana})
      (#{:hp-potion :mp-potion} skill) (process-potions {:socket socket
                                                         :username username
                                                         :skill skill
                                                         :health health
                                                         :mana mana}))))

;; TODO when exception is thrown, flow hangs!
(defn process-client-event [username data socket]
  ;; TODO fix here ..(string? data)..
  (cond
    (:init? data) (let [user-data {:position [(* (Math/random) map-range) 0.01 (* (Math/random) map-range)]
                                   :health 100
                                   :mana 100
                                   :speed 10
                                   :kills 0
                                   :deaths 0
                                   :state :idle}]
                    (swap! world assoc username user-data)
                    (send! socket {:init user-data}))
    (:ready? data) (do
                     (send! socket {:ready-ack? true
                                    :world (dissoc @world username)})
                     (doseq [s @ready-conns]
                       (send! s {:new-user {:username username
                                            :position (-> @world (get username) :position)}}))
                     (swap! ready-conns conj socket))
    (:respawn? data) (when (zero? (get-in @world [username :health] -1))
                       (let [user-data {:position [(* (Math/random) map-range) 0.01 (* (Math/random) map-range)]
                                        :health 100
                                        :mana 100
                                        :speed 10
                                        :state :idle}]
                         (send! socket {:respawn user-data})
                         (swap! world update username merge user-data)))
    (:chat-message data) (doseq [socket @ready-conns]
                           (send! socket {:sender username
                                          :chat-message (:chat-message data)}))
    :else (s/put! events (assoc data :username username))))

(defn send-world-snapshots []
  (let [ec (Executors/newSingleThreadScheduledExecutor)]
    (doto ec
      (.scheduleAtFixedRate
        (fn []
          (a/put! world-buffer @world)
          (doseq [conn @ready-conns]
            (send! conn {:world @world})))
        0 world-tick-rate TimeUnit/MILLISECONDS))))

(defn handler [req]
  (-> (http/websocket-connection req)
      (d/chain
        (fn [socket]
          (let [username (-> req :query-params (get "username"))]
            (println "Connection established - user: " username)
            (swap! conns assoc username socket)
            (s/on-closed socket (fn []
                                  (println "Connection closed. user: " username)
                                  (swap! conns dissoc username)
                                  (swap! ready-conns (fn [conns] (remove #(= % socket) conns)))
                                  (swap! world dissoc username)
                                  (doseq [s @ready-conns]
                                    (send! s {:user-exit username}))))
            (consume-client-event username #(process-client-event username % socket) socket))))
      (d/catch
        (fn [_]
          {:status 400
           :headers {"content-type" "application/text"}
           :body "Expected a websocket request."}))))

(defn- shutdown [^ExecutorService ec]
  (.shutdown ec)
  (try
    (when-not (.awaitTermination ec 2 TimeUnit/SECONDS)
      (.shutdownNow ec))
    (catch InterruptedException _
      ;; TODO may no need interrupt fn
      (.. Thread currentThread interrupt)
      (.shutdownNow ec))))

(mount/defstate ^{:on-reload :noop} snapshot-sender
                :start (send-world-snapshots)
                :stop (shutdown snapshot-sender))

(mount/defstate ^{:on-reload :noop} consume-event
                :start (s/consume
                         (fn [data]
                           (try
                             (if (:skill data)
                               (process-skill data)
                               (swap! world update (:username data) merge (dissoc data :username)))
                             (catch Exception e
                               (pp/pprint e)
                               (.printStackTrace e))))
                         events)
                :stop (d/success! consume-event true))

(defn home-page [request]
  (-> request
      (layout/render "home.html")
      (update :body #(str/replace % "/main.js" (str "/main.js?v=" (.toEpochMilli (Instant/now)))))))

(defn username? [username]
  (boolean
    (and (not (str/blank? username))
         (re-matches #"^[a-zA-Z_0-9]{2,20}" username))))

(defn ws-routes []
  [""
   {:middleware [middleware/wrap-formats middleware/wrap-error]}
   ["/" {:get home-page}]
   ["/ws" {:get handler}]
   ["/check_username" {:middleware []
                       :post (fn [{{:keys [username]} :params}]
                               (when-not (username? username)
                                 (throw (ex-info
                                          (str "Username can contain letters, numbers and underscores.\n"
                                               "It has to be between 2-20 characters long.")
                                          {:data username})))
                               {:status 200
                                :body {:success? (not (get @world username))}})}]])

(comment
  (mount/stop)
  (mount/start)
  @world
  @ready-conns)
