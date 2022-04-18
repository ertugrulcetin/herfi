(ns herfi.scene.entity.rock
  (:require
    [applied-science.js-interop :as j]
    [herfi.scene.api :as api]
    [herfi.scene.db :as db]
    [herfi.scene.ecs :as ecs]))

(defn create-rock-instanced-mesh [environment-map id type]
  (let [obj (api/find-asset type)
        data (:rock environment-map)
        _ (j/call (j/get obj :geometry) :computeVertexNormals)
        mesh (api/instanced-mesh (j/get obj :geometry)
                                 (j/get obj :material)
                                 (count data))
        matrix (api/mat4)]
    (doseq [rock (map-indexed #(assoc %2 :id %1) data)]
      (let [pos (api/vec->vec3 (:position rock))
            rotation (api/euler (:rotation rock))
            scale (api/vec->vec3 (:scale rock))
            q (api/quat)
            _ (api/set-from-euler q rotation)
            bb (api/box3)
            s (/ (first (:scale rock)) 4)
            bb-size [s 5 s]]
        (j/call bb :setFromCenterAndSize (-> rock :position api/vec->vec3) (api/vec->vec3 bb-size))
        (db/update-in-entity id [:bb] (fnil conj []) bb)
        (j/call matrix :compose pos q scale)
        (j/call mesh :setMatrixAt (:id rock) matrix)))
    mesh))

(defn create-rocks [environment-map]
  (-> (ecs/create-entity :rocks)
      (ecs/add-entity (-> (ecs/create-entity :rock {:meta {:type :rock}})
                          (ecs/add-component :render {:obj (create-rock-instanced-mesh environment-map :rock "Rock001")})))))

(defn create-spinning-rock []
  (-> (ecs/create-entity :rock-spinning {:position [-25 4 -25] :scale 8})
      (ecs/add-component :render {:type :asset :asset-path [:environment "Rock001"]})
      (ecs/add-component :script {:update (fn []
                                            (let [now (* (.getTime (js/Date.)) 0.0005)]
                                              (ecs/swap! :rock-spinning assoc :position
                                                         [(- (* (js/Math.sin now) 7) 20)
                                                          (+ 5 (* (js/Math.sin (* now 2.33)) 0.5))
                                                          (- (* (js/Math.cos now) 8) 20)])))})))
