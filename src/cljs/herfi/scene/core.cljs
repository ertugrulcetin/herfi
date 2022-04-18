(ns herfi.scene.core
  (:refer-clojure :exclude [clone])
  (:require
    [applied-science.js-interop :as j]
    [herfi.common.communication :as comm]
    [herfi.common.config :as config]
    [herfi.scene.assets :as assets]
    [herfi.scene.db :as db]
    [herfi.scene.ecs :as ecs]
    [herfi.scene.engine :as engine]
    [herfi.scene.environment :as environment]))

(js/console.log "assets" assets/checksum)

(defn reset []
  (js/cancelAnimationFrame (db/get :req-id))
  (doseq [id (keys (db/get :entities))]
    (ecs/remove-entity id))
  (engine/remove-all (db/get-scene))
  (j/call-in (db/get :renderer) [:renderLists :dispose])
  (j/call (db/get :renderer) :dispose)
  (db/reset-db!)
  (some-> (js/document.getElementById "scene") .remove)
  (environment/init))

(defn ^:dev/before-load before-load []
  (js/console.clear)
  (comm/terminate-all-chs))

(defn ^:dev/after-load after-load []
  #_(if config/multiplayer?
      (js/location.reload)
      (reset)))

(defn main
  ([]
   (main nil))
  ([data]
   (when config/dev?
     (println "Development profile active"))
   (environment/init data)))
