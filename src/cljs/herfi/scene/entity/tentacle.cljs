(ns herfi.scene.entity.tentacle
  (:require
    [herfi.scene.ecs :as ecs]
    [herfi.scene.procgen :as procgen]))

(defn create-tentacles []
  (dotimes [i 5]
    (-> (str "tentacle-" i)
        (ecs/create-entity
          {:rotation [0 (* (js/Math.random) js/Math.PI 2) 0]})
        (ecs/add-component :render {:type :asset
                                    :asset-path [:environment "Tentacle"]})
        (ecs/add-component :animation
                           {:idle-animation (procgen/choice ["Wave1" "Wave2"])
                            :speed (+ (* (js/Math.random) 0.25) 0.1)})
        (ecs/add-component :particle {:name :particle}))))
