(ns herfi.core
  (:require
    [herfi.common.communication :refer [on]]
    [herfi.common.config :as config]
    [herfi.scene.core :as px3d]
    [herfi.ui.core :as ui]))

(defn main []
  (ui/init)
  (on :init-scene #(px3d/main %))
  #_(if config/dev?
    (px3d/main)
    (on :init-scene #(px3d/main %))))
