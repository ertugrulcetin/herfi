(ns herfi.scene.environment
  (:require
    [applied-science.js-interop :as j]
    [cljs.reader :as reader]
    [herfi.common.config :as config]
    [herfi.scene.api :as api]
    [herfi.scene.db :as db]
    [herfi.scene.ecs :as ecs]
    [herfi.scene.engine :as engine]
    [herfi.scene.entity.character :as entity.char]
    [herfi.scene.entity.debug :as debug]
    [herfi.scene.entity.ground :as entity.ground]
    [herfi.scene.entity.mouse :as entity.mouse]
    [herfi.scene.entity.players :as entity.players]
    [herfi.scene.entity.rock :as entity.rock]
    [herfi.scene.entity.tentacle :as entity.tentacle]
    [herfi.scene.entity.tree :as entity.tree]
    [herfi.scene.network :as net])
  (:require-macros
    [shadow.resource :as rc]))

(def environment-map (reader/read-string (rc/inline "environment.edn")))

(defn- create-environment-entities []
  (entity.ground/create-ground)
  (entity.rock/create-rocks environment-map)
  (entity.rock/create-spinning-rock)
  (entity.tree/create-trees environment-map)
  (entity.tentacle/create-tentacles))

(defn- create-main-characters-entities []
  (entity.char/create-character)
  (entity.char/create-chasing-camera)
  (entity.char/create-character-position-info-on-map)
  (entity.players/create-players))

(defn- create-entities []
  (when config/dev?
    (debug/create-renderer-info-entity))
  (create-environment-entities)
  (entity.mouse/create-mouse-movement)
  (entity.mouse/create-selection-indicator)
  (create-main-characters-entities))

(defn render-loop [delta]
  (doseq [f (ecs/get-update-scripts-fns)]
    (f delta)))

(defn init
  ([]
   (init nil))
  ([{:keys [username resolution] :or {resolution :high
                                      username "Cool_Guy"}}]
   (let [resolution (case resolution
                      :high 1
                      :medium 2
                      3)
         eng (engine/init :pixel-size resolution)
         scene (db/get-scene)
         manager (api/loading-manager {:on-start #(db/set :loaded? false)
                                       :on-progress (fn [url items-loaded items-total]
                                                      (js/console.log (str "Started loading file: " url
                                                                           "\nLoaded " items-loaded
                                                                           " of " items-total " files")))
                                       :on-load (fn []
                                                  (db/set :username username)
                                                  (create-entities)
                                                  (doseq [f (ecs/get-init-scripts-fns)]
                                                    (f))
                                                  (db/set :loaded? true)
                                                  (db/set :camera-shake (api/create-camera-shake))
                                                  (println "Loading DONE!")
                                                  (engine/start-animation-loop eng #'render-loop)
                                                  (net/start))})]
     (engine/setup-default-scene scene)
     (engine/load-assets
       manager
       {:id :environment
        :type :gltf
        :url "models/assets.glb"}
       {:id :char
        :type :gltf
        :url "models/char.glb"
        :on-done (fn [assets] (j/assoc! (api/find-clip-by-name assets "run") :name "walk"))}))))

(comment)
