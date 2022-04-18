(ns herfi.scene.ecs
  (:refer-clojure :exclude [swap!])
  (:require
    [applied-science.js-interop :as j]
    [clojure.data :as data]
    [com.rpl.specter :as sp :refer-macros [select setval]]
    [herfi.character :as common.char]
    [herfi.scene.animation :as animation]
    [herfi.scene.api :as api]
    [herfi.scene.db :as db]
    [herfi.scene.particles :as particles]
    [herfi.scene.utils :as utils]
    [tilakone.core :as tk]))

(defn sync-entity-objs! [id]
  (let [{enabled :enabled
         render :render
         scale* :scale
         rotation* :rotation
         position* :position} (db/get-entity id)
        obj (:obj render)
        r (api/obj->vec obj :rotation)
        s (api/obj->vec obj :scale)
        p (api/obj->vec obj :position)
        visible (j/get obj :visible)]
    (when (not= r rotation*) (db/set-entity id :rotation r))
    (when (not= s scale*) (db/set-entity id :scale s))
    (when (not= p position*) (db/set-entity id :position p))
    (when (not= enabled visible) (db/set-entity id :enabled visible))))

(defn swap! [id f & args]
  (let [old-state (db/get-entity id)
        _ (apply db/update-entity (into [id f] args))
        new-state (db/get-entity id)
        {enabled :enabled
         render :render
         scale* :scale
         rotation* :rotation
         position* :position
         animation :animation} new-state
        keys-for-state-diff #{:up :down :left :right :target :jump :position :state}]
    (when animation
      (let [old-state* (select-keys old-state keys-for-state-diff)
            new-state* (select-keys new-state keys-for-state-diff)
            [_ diff _] (data/diff old-state* new-state*)]
        (when (seq diff)
          ((:trigger-fn animation) diff new-state))))
    (when render
      (let [obj (:obj render)]
        (cond-> obj
          (not= rotation* (:rotation old-state)) (api/rotation rotation*)
          (not= scale* (:scale old-state)) (#(apply api/scale (into [%] scale*)))
          (not= position* (:position old-state)) (api/position position*)
          (not= enabled (:enabled old-state)) (j/assoc! :visible enabled))
        (sync-entity-objs! id)))))

(defn add-entity [parent child]
  (cond
    (= (:id child) (:id parent)) (throw (ex-info "Child and parent can not be the same entity." {:entity child}))
    (:parent child) (throw (ex-info "Entity has already a parent." {:entity child})))
  (let [parent-id (if (string? parent) parent (:id parent))
        child-id (:id child)]
    (when-not (db/get-entity parent-id)
      (throw (ex-info "Parent entity does not exist" {:parent-id parent-id})))
    (db/update-in-entity parent-id [:children] (fnil conj []) child-id)
    (db/set-entity child-id :parent parent-id)
    (when-let [obj (db/get-entity parent-id :render :obj)]
      (when-let [c-obj (db/get-entity child-id :render :obj)]
        (when (and (api/object3d? obj) (api/object3d? c-obj))
          (j/call obj :add c-obj))))
    (db/get-entity parent-id)))

(defn create-entity
  ([id]
   (create-entity id {}))
  ([id {enabled :enabled
        scale* :scale
        rotation* :rotation
        position* :position
        :or {enabled true
             position* [0 0 0]
             rotation* [0 0 0]}
        :as opts}]
   (let [id (if (string? id) (keyword id) id)
         _ (when (db/get-entity id)
             (throw (ex-info "There is already an entity with this id." {:id id})))
         opts* {:id id
                :enabled enabled
                :position position*
                :rotation rotation*
                :scale (when scale* (if (vector? scale*) scale* [scale* scale* scale*]))}
         opts (merge opts opts*)]
     (db/set-in :entities id opts)
     (db/get-entity id))))

(defn- find-nested-entity-ids [id]
  (cons id (mapcat find-nested-entity-ids (db/get-entity id :children))))

(defn remove-entity [id]
  (let [{:keys [scene renderer]} (db/get)
        id (if (string? id) (keyword id) id)]
    (doseq [id (reverse (find-nested-entity-ids id))]
      (let [entity (db/get-entity id)]
        (db/swap! utils/dissoc-in [:entities id])
        (when-let [parent (:parent entity)]
          (db/swap! (fn [db] (setval [:entities parent :children sp/ALL #(= (:id entity) %)] sp/NONE db))))
        (when-let [systems (-> entity :particles vals seq)]
          (doseq [s systems]
            (particles/destroy s)))
        (when-let [obj (-> entity :render :obj)]
          (when (j/get obj :removeFromParent) (j/call obj :removeFromParent))
          (when (j/get obj :geometry) (j/call-in obj [:geometry :dispose]))
          (when (j/get obj :material) (j/call-in obj [:material :dispose]))
          (j/call scene :remove obj))))
    (j/call-in renderer [:renderLists :dispose])))

(defn- create-mesh [type geo-opts material]
  (let [geo (case type
              :cylinder (api/cylinder (or geo-opts {}))
              :plane (api/plane (or geo-opts {}))
              :ring (api/ring (or geo-opts {})))
        material-opts (dissoc material :type)
        material-fn (case (:type material)
                      :lambert api/lambert-material
                      :standard api/standard-material)
        material (material-fn material-opts)]
    (api/mesh geo material)))

(defmulti add-component (fn [entity type opts]
                          type))

(defmethod add-component :render [entity _ {:keys [type
                                                   obj
                                                   asset-path
                                                   cast-shadow?
                                                   receive-shadow?
                                                   skinned?
                                                   bb]
                                            :or {cast-shadow? true
                                                 receive-shadow? true}
                                            :as opts}]
  (when (:render entity)
    (throw (ex-info "Entity can only have one render prop." {:entity entity})))
  (let [obj (if (or (= type :asset) obj)
              (or obj (api/find-model (db/get :assets :models (first asset-path)) (second asset-path) skinned?))
              (create-mesh type (:geometry opts) (:material opts)))
        opts (assoc opts :obj obj
                    :cast-shadow? cast-shadow?
                    :receive-shadow? receive-shadow?)
        scale* (:scale entity)
        props (merge {:name (name (:id entity))} (:props entity))]
    (some->> props (utils/merge! obj))
    (j/assoc! obj :visible (:enabled entity))
    (api/position obj (:position entity))
    (api/rotation obj (:rotation entity))
    (when scale* (apply api/scale (into [obj] scale*)))
    (when (j/get obj :traverse)
      (j/call obj :traverse
              (fn [node]
                (when (j/get node :isMesh)
                  (api/set* node {:castShadow cast-shadow?
                                  :receiveShadow receive-shadow?})))))
    (db/set-entity (:id entity) :render opts)
    (when bb
      (if (map? bb)
        (if-let [multi (:multiply-size-by bb)]
          (let [b (j/call (api/box3) :setFromObject obj)
                pos (j/call b :getCenter (api/vec3))
                size (j/call b :getSize (api/vec3))
                size (j/call size :multiplyScalar multi)]
            (j/call b :setFromCenterAndSize pos size)
            (db/set-in :entities (:id entity) :render :bb-multiply-size-by multi)
            (db/set-in :entities (:id entity) :render :bb b))
          (let [bb-size (api/vec->vec3 (:size bb))
                bb-add (or (some-> bb :add api/vec->vec3) (api/vec3))]
            (db/set-in :entities (:id entity) :render :bb-size bb-size)
            (db/set-in :entities (:id entity) :render :bb-add bb-add)
            (db/set-in :entities (:id entity) :render :bb (j/call (api/box3) :setFromCenterAndSize
                                                                  (api/add (-> entity :position api/vec->vec3) bb-add)
                                                                  bb-size))))
        (db/set-in :entities (:id entity) :render :bb (j/call (api/box3) :setFromObject obj))))
    (when (api/object3d? obj)
      ;; Not doing recursively, unlikely to be problematic - but leaving a comment just in case
      (when-let [parent-obj (db/get-entity (:parent entity) :render :obj)]
        (j/call parent-obj :add obj))
      (when-let [children-objs (seq (keep (fn [id] (db/get-entity id :render :obj)) (:children entity)))]
        (doseq [child-obj children-objs]
          (j/call obj :add child-obj)))
      (api/add-scene (db/get-scene) obj))
    (db/get-entity (:id entity))))

(defmethod add-component :script [entity _ {:keys [init update priority name duration on-end]}]
  (when (and duration (nil? name))
    (throw (ex-info "You need to define name in order to provide duration." {})))
  (let [f update
        entity-id (:id entity)
        path [:entities entity-id :script :update]
        now (js/performance.now)
        f (if duration
            (fn [delta]
              (if (>= (- (js/performance.now) now) duration)
                (do
                  (db/update-in path #(remove (fn [r] (= (:name r) name)) %))
                  (when on-end (on-end entity)))
                (f delta entity)))
            f)
        f (fn [delta] (f delta entity))
        init-fn (fn [] (when init (init entity)))]
    (db/swap! (fn [s]
                (cond-> s
                  update (update-in path (fnil conj []) (merge
                                                          {:fn f :priority priority}
                                                          (when name {:name name})))
                  update (update-in path (partial sort-by :priority))
                  init (update-in (conj (vec (drop-last path)) :init) (fnil conj []) (merge
                                                                                       {:fn init-fn :priority priority}
                                                                                       (when name {:name name}))))))
    (db/get-entity entity-id)))

(defmethod add-component :animation [entity _ {:keys [states
                                                      action-fn
                                                      additional-state-map
                                                      idle-animation
                                                      speed
                                                      root-index]
                                               :or {idle-animation "idle"}
                                               :as opts}]
  (when (:animation entity)
    (throw (ex-info "Entity has already an animation." {:entity entity})))
  (let [entity-id (:id entity)
        obj (db/get-entity entity-id :render :obj)
        current-action (atom (animation/get-action obj idle-animation root-index))
        fsm (merge {::tk/states states
                    ::tk/action! (fn [{::tk/keys [action] :as fsm}]
                                   (when (or (not (:other-player? opts))
                                             (not= (db/get-entity entity-id :prev-state)
                                                   (db/get-entity entity-id :state)))
                                     (let [[action-name duration] (if (vector? action) action [action])
                                           duration (or duration 0.35)]
                                       (when-let [{:keys [speed loop-once? clamp?]
                                                   :or {speed 1}
                                                   :as state} (some #(when (= (::tk/name %) action-name) %) states)]
                                         (let [end-action (animation/get-action obj (name (::tk/name state)) root-index)]
                                           (animation/fade-to-action @current-action end-action speed duration loop-once? clamp?)
                                           (reset! current-action end-action)))))
                                   (if action-fn
                                     (action-fn fsm entity-id)
                                     fsm))
                    ::tk/state :idle}
                   additional-state-map)
        process (atom nil)
        mixer (j/get obj :mixer)
        walk? (fn [new-state]
                (or (some true? (vals (select-keys new-state [:up :down :left :right])))
                    (:target new-state)))]
    (when-not (:other-player? opts)
      (j/call mixer :addEventListener "finished" (fn []
                                                   (when (not= :die (db/get-entity :robot :state))
                                                     (let [new-state (db/get-entity :robot)
                                                           state (if (walk? new-state) :walk :idle)]
                                                       (if (nil? @process)
                                                         (reset! process (tk/apply-signal fsm state))
                                                         (reset! process (tk/apply-signal @process state))))))))
    (if-not states
      (do
        (when speed (j/assoc! @current-action :timeScale speed))
        (j/call @current-action :play))
      (animation/active-actions obj idle-animation (map (comp name ::tk/name) states) root-index))
    (db/set-entity entity-id :animation
                   {:states states
                    :single-animation? (nil? states)
                    ;; TODO redesign here...
                    :trigger-fn (fn [diff new-state]
                                  (when states
                                    (let [s (cond
                                              (:other-player? opts)
                                              (:state new-state)

                                              ((conj (-> common.char/skills keys set) :jump :die) (:state new-state))
                                              (:state new-state)

                                              (walk? new-state)
                                              :walk

                                              :else :idle)]
                                      (if (nil? @process)
                                        (reset! process (tk/apply-signal fsm s))
                                        (reset! process (tk/apply-signal @process s))))))})
    (db/get-entity entity-id)))

(defmethod add-component :particle [entity _ {:keys [name duration]}]
  (let [json (name particles/emitters)
        entity-id (:id entity)
        obj (db/get-entity entity-id :render :obj)
        uuid (str (random-uuid))]
    (particles/add-particle json
                            (fn [system]
                              (particles/add-renderer system obj)
                              (db/set-in :entities entity-id :particles uuid system)
                              (when duration
                                (js/setTimeout (fn []
                                                 (particles/destroy system)
                                                 (db/swap! utils/dissoc-in [:entities entity-id :particles uuid]))
                                               duration))))
    (db/get-entity entity-id)))

(defn get-init-scripts-fns []
  (select [:entities sp/MAP-VALS (sp/must :script :init) sp/ALL :fn] (db/get)))

(defn get-update-scripts-fns []
  (select [:entities sp/MAP-VALS (sp/must :script :update) sp/ALL :fn] (db/get)))

(defn update-script-update-fn
  ([id f]
   (update-script-update-fn id nil f))
  ([id name f]
   (db/swap!
     (fn [db]
       (if name
         (setval [:entities id :script :update sp/ALL #(= (:name %) name) :fn] f db)
         (setval [:entities id :script :update sp/FIRST :fn] f db))))
   f))
