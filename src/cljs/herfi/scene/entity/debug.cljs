(ns herfi.scene.entity.debug
  (:require
    [herfi.common.communication :refer [fire]]
    [herfi.scene.db :as db]
    [herfi.scene.ecs :as ecs]))

(defn create-renderer-info-entity []
  (-> (ecs/create-entity :renderer-info)
      (ecs/add-component :script {:update #(fire :renderer-info (db/get! :renderer :info :render))})))
