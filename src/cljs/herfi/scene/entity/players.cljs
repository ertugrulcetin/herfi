(ns herfi.scene.entity.players
  (:require
    [applied-science.js-interop :as j]
    [herfi.character :refer [enemy-visibility-distance]]
    [herfi.common.config :as config]
    [herfi.scene.api :as api]
    [herfi.scene.db :as db]
    [herfi.scene.ecs :as ecs]
    [herfi.scene.particles :as particles]
    [tilakone.core :as tk])
  (:require-macros
    [kezban.core :refer [when-let*]]))

(defn add-storm-line-particle [entity]
  (let [{:keys [name
                obj
                update
                on-end
                duration]} (particles/storm-line 3000)]
    (ecs/add-entity entity (-> (ecs/create-entity name)
                               (ecs/add-component :render {:obj obj})
                               (ecs/add-component :script {:update update
                                                           :name name
                                                           :on-end on-end
                                                           :duration duration})))))

(defn- create-bb-for-player []
  (let [geo (api/box 1.5 4 1.5)
        m (api/basic-material {})
        bounding-box (api/mesh geo m)]
    (j/assoc! m :visible false)
    bounding-box))

(defn- create-dummy-enemies []
  (let [players (db/get-entity :players)]
    (when config/dev?
      (ecs/add-entity players (-> (ecs/create-entity "F9Devil" {:position [5 0.01 5]
                                                                :health 85
                                                                :meta {:type :char}})
                                  (ecs/add-entity (-> (ecs/create-entity :character-text {:position [0 2 0]})
                                                      (ecs/add-component :render {:obj (api/sprite-text "F9Devil"
                                                                                                        {:color "#2b2a5b"
                                                                                                         :fontWeight "bold"
                                                                                                         :textHeight 0.5})})))
                                  (ecs/add-component :render {:type :asset
                                                              :skinned? true
                                                              :asset-path [:char "Scene"]})
                                  (ecs/add-entity (-> (ecs/create-entity :bb-dancer)
                                                      (ecs/add-component :render {:obj (create-bb-for-player)})))
                                  (ecs/add-component :animation {:idle-animation "attack-flame"})))
      (ecs/add-entity players (-> (ecs/create-entity "0000000" {:position [10 0.01 5]
                                                                :health 100
                                                                :props {:name "Dancer 2"}
                                                                :meta {:type :char}})
                                  (ecs/add-entity (-> (ecs/create-entity :character-text {:position [0 2 0]})
                                                      (ecs/add-component :render {:obj (api/sprite-text "0000000"
                                                                                                        {:color "#2b2a5b"
                                                                                                         :fontWeight "bold"
                                                                                                         :textHeight 0.5})})))
                                  (ecs/add-component :render {:type :asset
                                                              :skinned? true
                                                              :asset-path [:char "Scene"]})
                                  (ecs/add-entity (-> (ecs/create-entity :bb-dancer-2)
                                                      (ecs/add-component :render {:obj (create-bb-for-player)})))
                                  (ecs/add-component :animation {:idle-animation "attack-light"
                                                                 :speed 1.25}))))))

(defn create-players []
  (-> (ecs/create-entity :distance-checker)
      (ecs/add-component :script {:update (fn []
                                            (->> (db/get-entity :players :children)
                                                 (filter (fn [id]
                                                           (when-let* [enemy (db/get-entity-obj! id :position)
                                                                       char (db/get-entity-obj! :robot :position)]
                                                                      (if (> (api/distance-to char enemy)
                                                                             enemy-visibility-distance)
                                                                        (ecs/swap! id assoc :enabled false)
                                                                        (ecs/swap! id assoc :enabled true)))))
                                                 doall))}))
  (-> (ecs/create-entity :players) (ecs/add-component :render {:obj (api/mesh)}))
  #_(create-dummy-enemies))

(defn create-player [username pos]
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
             ::tk/actions [:die]}
        transitions [idle walk jump flame ice light punch die {::tk/on tk/_}]]
    (-> (db/get-entity :players)
        (ecs/add-entity (-> (ecs/create-entity username {:position pos})
                            (ecs/add-entity (-> (ecs/create-entity (str "character-text-" username) {:position [0 2 0]})
                                                (ecs/add-component :render {:obj (api/sprite-text username
                                                                                                  {:color "#2b2a5b"
                                                                                                   :fontWeight "bold"
                                                                                                   :textHeight 0.5})})))
                            (ecs/add-component :render {:type :asset
                                                        :skinned? true
                                                        :asset-path [:char "Scene"]})
                            (ecs/add-entity (-> (ecs/create-entity (str "bb-" username))
                                                (ecs/add-component :render {:obj (create-bb-for-player)})))
                            (ecs/add-component :animation {:states [{::tk/name :idle
                                                                     ::tk/transitions transitions}
                                                                    {::tk/name :walk
                                                                     ::tk/transitions transitions}
                                                                    {::tk/name :punch
                                                                     ::tk/transitions transitions
                                                                     :loop-once? true
                                                                     :speed 1.5}
                                                                    {::tk/name :jump
                                                                     ::tk/transitions transitions
                                                                     :loop-once? true
                                                                     :speed 1.3}
                                                                    {::tk/name :attack-flame
                                                                     ::tk/transitions transitions
                                                                     :loop-once? true
                                                                     :speed 2}
                                                                    {::tk/name :attack-ice
                                                                     ::tk/transitions transitions
                                                                     :loop-once? true
                                                                     :speed 2.4}
                                                                    {::tk/name :attack-light
                                                                     ::tk/transitions transitions
                                                                     :loop-once? true
                                                                     :speed 3}
                                                                    {::tk/name :die
                                                                     ::tk/transitions transitions
                                                                     :loop-once? true
                                                                     :clamp? true}]
                                                           :other-player? true
                                                           :action-fn (fn [{::tk/keys [action] :as fsm} entity-id]
                                                                        (let [prev-state (db/get-entity entity-id :state)
                                                                              new-state action]
                                                                          (db/set-entity entity-id :prev-state prev-state)
                                                                          (db/set-entity entity-id :state new-state)
                                                                          fsm))}))))))
