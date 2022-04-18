(ns herfi.scene.entity.tree
  (:require
    [applied-science.js-interop :as j]
    [herfi.scene.api :as api]
    [herfi.scene.db :as db]
    [herfi.scene.ecs :as ecs]))

(defn get-tree-data [environment-map name]
  (->> environment-map :tree (group-by :asset) (#(get % name))))

(defn create-tree-instanced-mesh [environment-map id type child-index]
  (let [obj (api/find-asset type child-index)
        data (get-tree-data environment-map type)
        _ (j/call (j/get obj :geometry) :computeVertexNormals)
        mesh (api/instanced-mesh (j/get obj :geometry)
                                 (j/get obj :material)
                                 (count data))
        matrix (api/mat4)]
    (doseq [tree (map-indexed #(assoc %2 :id %1) data)]
      (let [pos (api/vec->vec3 (:position tree))
            rotation (api/euler [0 0 0])
            scale (api/vec->vec3 [5 5 5])
            q (api/quat)
            _ (api/set-from-euler q rotation)
            bb (api/box3)
            bb-size (if (= type "Tree002") [1 20 2] [6 20 6])]
        (j/call bb :setFromCenterAndSize
                (-> tree :position api/vec->vec3 (api/add (api/vec->vec3 [0.75 0 0])))
                (api/vec->vec3 bb-size))
        (db/update-in-entity id [:bb] (fnil conj []) bb)
        (j/call matrix :compose pos q scale)
        (j/call mesh :setMatrixAt (:id tree) matrix)))
    (j/call-in (db/get :controls) [:colliderMeshes :push] mesh)
    mesh))

(defn create-trees [environment-map]
  (-> (ecs/create-entity :trees)
      (ecs/add-entity (-> (ecs/create-entity :tree-2 {:meta {:type :tree}})
                          (ecs/add-component :render {:obj (create-tree-instanced-mesh
                                                             environment-map
                                                             :tree-2
                                                             "Tree002"
                                                             0)})))
      (ecs/add-entity (-> (ecs/create-entity :tree-2-body {:meta {:type :tree}})
                          (ecs/add-component :render {:obj (create-tree-instanced-mesh
                                                             environment-map
                                                             :tree-2-body
                                                             "Tree002"
                                                             1)})))
      (ecs/add-entity (-> (ecs/create-entity :tree-3 {:meta {:type :tree}})
                          (ecs/add-component :render {:obj (create-tree-instanced-mesh
                                                             environment-map
                                                             :tree-3
                                                             "Tree003"
                                                             0)})))
      (ecs/add-entity (-> (ecs/create-entity :tree-3-body {:meta {:type :tree}})
                          (ecs/add-component :render {:obj (create-tree-instanced-mesh
                                                             environment-map
                                                             :tree-3-body
                                                             "Tree003"
                                                             1)})))))
