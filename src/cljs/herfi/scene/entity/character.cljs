(ns herfi.scene.entity.character
  (:require
    [applied-science.js-interop :as j]
    [clojure.set :as set]
    [clojure.string :as str]
    [herfi.character :as common.char]
    [herfi.common.communication :refer [on fire]]
    [herfi.scene.api :as api]
    [herfi.scene.db :as db]
    [herfi.scene.ecs :as ecs]
    [herfi.scene.network :as net]
    [herfi.scene.picker :as picker]
    [tilakone.core :as tk :refer [_]]))

(defn- create-character-username-text []
  (-> (ecs/create-entity :character-text {:position [0 2 0]})
      (ecs/add-component :render {:obj (api/sprite-text (db/get :username)
                                                        {:color "#2b2a5b"
                                                         :fontWeight "bold"
                                                         :textHeight 0.5})})))

(defn- add-render-comp [entity]
  (ecs/add-component entity :render {:type :asset
                                     :skinned? true
                                     :asset-path [:char "Scene"]
                                     :bb {:add [0 0.75 0]
                                          :size [1 2 1]}}))

(let [types #{:tree :rock}]
  (defn- get-bbs-from-collidable-entities []
    (->> (db/get :entities)
         vals
         (filter #(types (-> % :meta :type)))
         (keep :bb)
         (apply concat))))

(defn- collision? [bb]
  (some #(api/intersects-box bb %) (get-bbs-from-collidable-entities)))

(let [target (api/vec3)]
  (defn picked [hit type]
    (let [mesh-name (j/get-in hit [:object :name])]
      (cond
        (and (#{:move :down} type)
             (= "ground" mesh-name))
        (let [robot (db/get-entity-obj! :robot)
              x (j/get-in hit [:point :x])
              z (j/get-in hit [:point :z])]
          (j/call target :set x 0.01 z)
          (when-not (= :die (db/get-entity :robot :state))
            (api/look-point robot target))
          (ecs/swap! :robot assoc :target target)
          (when (and (not (and (= type :move) (db/get :selected-character)))
                     (not (db/get :selected-character-locked?)))
            (db/set :selected-character nil)
            (fire :selected-character nil)))

        (and (<= (j/get hit :distance) common.char/enemy-visibility-distance)
             (str/starts-with? mesh-name "bb-")
             (not (db/get :selected-character-locked?)))
        (let [entity-id (->> mesh-name db/get-entity :parent)
              entity (db/get-entity entity-id)]
          (db/set :selected-character entity-id)
          (fire :selected-character {:name (-> entity :id name)
                                     :health (:health entity)}))))))

(defn- valid-key-code? [code]
  ((set/union #{"KeyW" "KeyS" "KeyA" "KeyD" "KeyZ" "Space" "ArrowUp" "ArrowDown" "ArrowLeft" "ArrowRight"}
              (set (map #(str "Digit" %) (range 1 9))))
   code))

(defn- add-controller-script-comp [entity]
  (ecs/add-component entity :script
                     (let [chat-enabled? (atom false)]
                       {:init
                        (fn []
                          (picker/register picked)
                          (on :chat-enabled? #(reset! chat-enabled? %))
                          (.addEventListener js/document "keydown"
                                             (fn [event]
                                               (let [code (.-code event)]
                                                 (when (and (valid-key-code? code) (not @chat-enabled?))
                                                   (fire :event-key-down code))))
                                             false)
                          (.addEventListener js/document "keyup"
                                             (fn [event]
                                               (let [code (.-code event)]
                                                 (when (and (valid-key-code? code) (not @chat-enabled?))
                                                   (fire :event-key-up code))))
                                             false))
                        :update
                        (let [temp-vec (api/vec3)
                              temp-disc-vec (api/vec3)
                              up-vec (api/vec3 0 1 0)
                              bb-temp-pos (api/vec3)
                              bb-coll-temp-pos (api/vec3)
                              bb-new-pos (api/vec3)
                              temp-target (api/vec3)
                              temp-target-new-pos (api/vec3)
                              synced-first-time? (atom false)
                              map-range 175]
                          (fn [delta]
                            (when-not ((conj common.char/magic-skills :die) (db/get-entity :robot :state))
                              (let [{:keys [controls]} (db/get)
                                    {:keys [up down left right target render]} (db/get-entity :robot)
                                    bb (:bb render)
                                    bb-size (:bb-size render)
                                    bb-add (:bb-add render)
                                    robot (db/get-entity-obj! :robot)
                                    pos (j/get robot :position)
                                    angle (j/get controls :azimuthAngle)
                                    walk-speed (db/get-entity :robot :speed)
                                    forward-acc (* walk-speed delta)
                                    _ (api/copy bb-temp-pos pos)
                                    new-pos (api/add bb-temp-pos bb-add)]
                                (api/copy bb-new-pos new-pos)
                                (api/copy bb-coll-temp-pos new-pos)
                                (j/call bb :setFromCenterAndSize new-pos bb-size)
                                (if (and (or up down left right) (not @picker/mouse-down?))
                                  (do
                                    (ecs/swap! :robot assoc :target nil)
                                    (cond
                                      (and up left)
                                      (-> temp-vec (j/call :set -1 0 -1) api/normalize (j/call :applyAxisAngle up-vec angle))

                                      (and up right)
                                      (-> temp-vec (j/call :set 1 0 -1) api/normalize (j/call :applyAxisAngle up-vec angle))

                                      (and down right)
                                      (-> temp-vec (j/call :set 1 0 1) api/normalize (j/call :applyAxisAngle up-vec angle))

                                      (and down left)
                                      (-> temp-vec (j/call :set -1 0 1) api/normalize (j/call :applyAxisAngle up-vec angle))

                                      up
                                      (-> temp-vec (j/call :set 0 0 -1) (j/call :applyAxisAngle up-vec angle))

                                      down
                                      (-> temp-vec (j/call :set 0 0 1) (j/call :applyAxisAngle up-vec angle))

                                      left
                                      (-> temp-vec (j/call :set -1 0 0) (j/call :applyAxisAngle up-vec angle))

                                      right
                                      (-> temp-vec (j/call :set 1 0 0) (j/call :applyAxisAngle up-vec angle)))
                                    (j/call bb-coll-temp-pos :addScaledVector temp-vec forward-acc)
                                    (j/call bb :setFromCenterAndSize bb-coll-temp-pos bb-size)
                                    (if (or (collision? bb)
                                            (>= (js/Math.abs (j/get bb-coll-temp-pos :x)) map-range)
                                            (>= (js/Math.abs (j/get bb-coll-temp-pos :z)) map-range))
                                      (do
                                        (j/call bb :setFromCenterAndSize bb-new-pos bb-size)
                                        (ecs/swap! :robot assoc :collision? true))
                                      (do
                                        (j/call-in robot [:position :addScaledVector] temp-vec forward-acc)
                                        (api/look-at robot (-> pos api/clone (api/add temp-vec)))
                                        (ecs/swap! :robot assoc :collision? false))))
                                  (when target
                                    (j/call temp-disc-vec :set (j/get pos :x) 0 (j/get pos :z))
                                    (if (<= (api/distance-to temp-disc-vec target) forward-acc)
                                      (ecs/swap! :robot assoc :target nil :collision? false)
                                      (let [move (api/copy temp-target target)
                                            dir (-> move (api/sub pos) api/normalize (api/multiply-scalar forward-acc))
                                            new-pos (api/add (api/copy temp-target-new-pos pos) dir)]
                                        (j/call bb :setFromCenterAndSize (api/add new-pos bb-add) bb-size)
                                        (if (or (collision? bb)
                                                (>= (js/Math.abs (j/get new-pos :x)) map-range)
                                                (>= (js/Math.abs (j/get new-pos :z)) map-range))
                                          (do
                                            (j/call bb :setFromCenterAndSize bb-new-pos bb-size)
                                            (ecs/swap! :robot assoc :target nil :collision? true))
                                          (do
                                            (api/add pos dir)
                                            (ecs/swap! :robot assoc :collision? false)))))))
                                (when-not @synced-first-time?
                                  (reset! synced-first-time? true)
                                  (ecs/sync-entity-objs! :robot))))))})))

(defn- use-skill [skill]
  (let [loading-skill-name (if (#{:hp-potion :mp-potion} skill)
                             :potion
                             skill)]
    (net/send-to-server
      (cond-> {:skill skill}
        (db/get :selected-character) (assoc :selected-character (name (db/get :selected-character)))))
    (fire :skill-used skill)
    (db/set-in :entities :robot :loading-skills loading-skill-name true)
    (js/setTimeout
      #(db/set-in :entities :robot :loading-skills loading-skill-name false)
      (* (-> common.char/skills skill :duration) 1000))))

(defn- add-animation-comp [entity]
  (ecs/add-component entity :animation
                     (let [idle {::tk/on :idle
                                 ::tk/to :idle
                                 ::tk/actions [:idle]}
                           walk {::tk/on :walk
                                 ::tk/to :walk
                                 ::tk/actions [:walk]}
                           jump {::tk/on :jump
                                 ::tk/to :jump
                                 ::tk/actions [:jump]}
                           flame {::tk/on :attack-flame
                                  ::tk/to :attack-flame
                                  ::tk/actions [:attack-flame]}
                           ice {::tk/on :attack-ice
                                ::tk/to :attack-ice
                                ::tk/actions [:attack-ice]}
                           light {::tk/on :attack-light
                                  ::tk/to :attack-light
                                  ::tk/actions [:attack-light]}
                           punch {::tk/on :punch
                                  ::tk/to :punch
                                  ::tk/actions [:punch]}
                           die {::tk/on :die
                                ::tk/to :die
                                ::tk/actions [:die]}]
                       {:states [{::tk/name :idle
                                  ::tk/transitions [walk jump flame ice light punch die {::tk/on _}]}
                                 {::tk/name :walk
                                  ::tk/transitions [idle jump punch flame ice light die {::tk/on _}]}
                                 {::tk/name :punch
                                  ::tk/transitions [idle walk die {::tk/on _}]
                                  :loop-once? true
                                  :speed 1.5}
                                 {::tk/name :jump
                                  ::tk/transitions [idle walk die {::tk/on _}]
                                  :loop-once? true
                                  :speed 1.3}
                                 {::tk/name :attack-flame
                                  ::tk/transitions [idle walk die {::tk/on _}]
                                  :loop-once? true
                                  :speed 2}
                                 {::tk/name :attack-ice
                                  ::tk/transitions [idle walk die {::tk/on _}]
                                  :loop-once? true
                                  :speed 2.4}
                                 {::tk/name :attack-light
                                  ::tk/transitions [idle walk die {::tk/on _}]
                                  :loop-once? true
                                  :speed 3}
                                 {::tk/name :die
                                  ::tk/transitions [idle {::tk/on _}]
                                  :loop-once? true
                                  :clamp? true}]
                        :action-fn (fn [{::tk/keys [action] :as fsm} entity-id]
                                     (let [prev-state (db/get-entity entity-id :state)
                                           new-state action]
                                       (db/set-entity :robot :prev-state prev-state)
                                       (db/set-entity :robot :state new-state)
                                       (when (and
                                               (#{:walk :idle} new-state)
                                               (not= new-state prev-state)
                                               ((conj common.char/magic-skills :punch) prev-state))
                                         (use-skill prev-state)
                                         (db/set :selected-character-locked? false))
                                       fsm))})))

(defn create-chasing-camera []
  (-> (ecs/create-entity :chasing-character)
      (ecs/add-component :script {:update (let [offset (api/vec3 0 1 0)]
                                            (fn []
                                              (let [controls (db/get :controls)
                                                    pos (db/get-entity-obj! :robot :position)
                                                    x (+ (.-x pos) (.-x offset))
                                                    y (+ (.-y pos) (.-y offset))
                                                    z (+ (.-z pos) (.-z offset))]
                                                (j/call controls :moveTo x y z false))))})))

(defn create-character-position-info-on-map []
  (-> (ecs/create-entity :character-position-info-on-map)
      (ecs/add-component :script {:update #(fire :char-position (db/get-entity :robot :position))})))

(defn- key-code->skill [code]
  (case code
    "Digit1" :attack-flame
    "Digit2" :attack-ice
    "Digit3" :attack-light
    "Digit4" :punch
    "Digit5" :shield
    "Digit6" :teleport
    "Digit7" :hp-potion
    "Digit8" :mp-potion
    nil))

(defn- not-enough-mana? [skill selected-char mana]
  (and selected-char (->> common.char/skills skill :required-mana (< mana))))

(defn- ready-to-attack? [selected-char entity skill]
  (and selected-char
       (not= :die (db/get-entity selected-char :state))
       (-> entity :loading-skills skill not)
       (#{:walk :idle} (:state entity))))

(defn- select-closest-enemy []
  (let [closest-enemy-id (->> (db/get-entity :players :children)
                              (map (fn [id]
                                     [id (api/distance-to (db/get-entity-obj! :robot :position)
                                                          (db/get-entity-obj! id :position))]))
                              (sort-by second)
                              ffirst)
        entity (some-> closest-enemy-id db/get-entity)]
    (when (and entity (not (db/get :selected-character-locked?)))
      (db/set :selected-character closest-enemy-id)
      (fire :selected-character {:name (-> entity :id name)
                                 :health (:health entity)})
      (when (= :idle (db/get-entity :robot :state))
        (api/look-point (db/get-entity-obj! :robot) (db/get-entity-obj! closest-enemy-id :position))))))

(defn- process-char-direction-events [code]
  (case code
    ("KeyW" "ArrowUp") (ecs/swap! :robot assoc :up true)
    ("KeyS" "ArrowDown") (ecs/swap! :robot assoc :down true)
    ("KeyA" "ArrowLeft") (ecs/swap! :robot assoc :left true)
    ("KeyD" "ArrowRight") (ecs/swap! :robot assoc :right true)
    "KeyZ" (select-closest-enemy)
    "Space" (when-not (= :die (db/get-entity :robot :state))
              (ecs/swap! :robot assoc :state :jump))
    nil))

(defn- process-magic-skill [{:keys [selected-char selected-char-pos entity mana char-pos skill]}]
  (cond
    (and selected-char-pos (> (api/distance-to selected-char-pos char-pos) 35))
    (fire :enemy-too-far true)

    (not-enough-mana? skill selected-char mana)
    (fire :not-enough-mana true)

    (ready-to-attack? selected-char entity skill)
    (do
      (api/look-point (db/get-entity-obj! :robot) selected-char-pos)
      (ecs/swap! :robot assoc :state skill :target nil)
      (db/set :selected-character-locked? true))))

(defn- process-punch [{:keys [selected-char selected-char-pos entity mana char-pos skill]}]
  (cond
    (and selected-char-pos
         (> (api/distance-to selected-char-pos char-pos) 4))
    (fire :enemy-too-far true)

    (not-enough-mana? skill selected-char mana)
    (fire :not-enough-mana true)

    (ready-to-attack? selected-char entity skill)
    (do
      (api/look-point (db/get-entity-obj! :robot) selected-char-pos)
      (ecs/swap! :robot assoc :state :punch)
      (db/set :selected-character-locked? true))))

(defn process-teleport [{:keys [entity health mana skill state]}]
  (cond
    (->> common.char/skills skill :required-mana (< mana))
    (fire :not-enough-mana true)

    (< health 35)
    (fire :not-enough-health-for-tp true)

    (not= :idle state)
    (fire :not-idle-state-for-tp true)

    (-> entity :loading-skills skill not)
    (use-skill skill)))

(defn process-shield [{:keys [entity mana skill]}]
  (cond
    (->> common.char/skills skill :required-mana (< mana))
    (fire :not-enough-mana true)

    (-> entity :loading-skills skill not)
    (use-skill skill)))

(defn- add-events-script-comp [entity]
  (ecs/add-component entity :script
                     {:name :process-events
                      :init (fn []
                              (on :event-key-down
                                  (fn [code]
                                    (let [entity (db/get-entity :robot)
                                          char-pos (db/get-entity-obj! :robot :position)
                                          health (:health entity)
                                          mana (:mana entity)
                                          state (:state entity)
                                          selected-char (db/get :selected-character)
                                          selected-char-pos (some-> selected-char (db/get-entity-obj! :position))
                                          skill (key-code->skill code)]
                                      (when (> health 0)
                                        (process-char-direction-events code)
                                        (cond
                                          (common.char/magic-skills skill)
                                          (process-magic-skill {:selected-char selected-char
                                                                :selected-char-pos selected-char-pos
                                                                :entity entity
                                                                :mana mana
                                                                :char-pos char-pos
                                                                :skill skill})

                                          (= :punch skill)
                                          (process-punch {:selected-char selected-char
                                                          :selected-char-pos selected-char-pos
                                                          :entity entity
                                                          :mana mana
                                                          :char-pos char-pos
                                                          :skill skill})

                                          (= :teleport skill)
                                          (process-teleport {:health health
                                                             :mana mana
                                                             :entity entity
                                                             :state state
                                                             :skill skill})

                                          (= :shield skill)
                                          (process-shield {:mana mana
                                                           :entity entity
                                                           :skill skill})

                                          (and (#{:hp-potion :mp-potion} skill)
                                               (-> entity :loading-skills :potion not))
                                          (use-skill skill))))))
                              (on :event-key-up (fn [code]
                                                  (case code
                                                    ("KeyW" "ArrowUp") (ecs/swap! :robot assoc :up false)
                                                    ("KeyS" "ArrowDown") (ecs/swap! :robot assoc :down false)
                                                    ("KeyA" "ArrowLeft") (ecs/swap! :robot assoc :left false)
                                                    ("KeyD" "ArrowRight") (ecs/swap! :robot assoc :right false)
                                                    nil))))}))

(defn create-character []
  (-> (ecs/create-entity :robot {:position [15 0.01 15]
                                 :state :idle
                                 :speed 10
                                 :health 100
                                 :mana 100})
      (ecs/add-entity (create-character-username-text))
      (add-render-comp)
      (add-controller-script-comp)
      (add-events-script-comp)
      (add-animation-comp)))

(comment
  )
