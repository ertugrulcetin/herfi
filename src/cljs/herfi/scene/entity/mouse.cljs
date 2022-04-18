(ns herfi.scene.entity.mouse
  (:require
    [applied-science.js-interop :as j]
    [herfi.scene.api :as api]
    [herfi.scene.db :as db]
    [herfi.scene.ecs :as ecs]))

(defn create-mouse-movement []
  (-> (ecs/create-entity :mouse-movement)
      (ecs/add-component :script (let [page-x (atom nil)
                                       controls (db/get :controls)
                                       canvas (db/get! :renderer :domElement)]
                                   {:init (fn []
                                            (.addEventListener canvas "mousemove"
                                                               (fn [event]
                                                                 (reset! page-x (.-pageX event)))
                                                               false)
                                            (.addEventListener canvas "auxclick"
                                                               (fn [event]
                                                                 (when (= (j/get event :which) 2)
                                                                   (j/call controls :rotate (api/deg->rad 180) 0 true))))
                                            (.addEventListener canvas "contextmenu" #(.preventDefault %))
                                            (.addEventListener canvas "keydown"
                                                               (fn [event]
                                                                 (when (= "ControlLeft" (.-code event))
                                                                   (j/assoc! controls :enabled true)))
                                                               false)
                                            (.addEventListener canvas "keyup"
                                                               (fn [event]
                                                                 (when (= "ControlLeft" (.-code event))
                                                                   (j/assoc! controls :enabled false)))
                                                               false))
                                    :update (fn []
                                              (let [width js/window.innerWidth]
                                                (cond
                                                  (and @page-x (<= @page-x 4))
                                                  (j/call controls :rotate (api/deg->rad 2) 0 false)

                                                  (<= (- width 5) @page-x (+ width 2))
                                                  (j/call controls :rotate (api/deg->rad -2) 0 false))))}))))

(defn create-selection-indicator []
  (-> (ecs/create-entity :selection {:rotation [(/ js/Math.PI  2) 0 0]
                                     :enabled false})
      (ecs/add-component :render {:type :ring
                                  :geometry {:inner-rad 0.7
                                             :outer-rad 1
                                             :theta-segs 8}
                                  :material {:type :lambert
                                             :side api/double-side}
                                  :cast-shadow? false})
      (ecs/add-component :script {:update (fn []
                                            (let [selected-char (db/get :selected-character)
                                                  target (or (some-> selected-char (db/get-entity-obj! :position))
                                                             (db/get-entity :robot :target))]
                                              (if selected-char
                                                (j/call-in (db/get-entity-obj! :selection :material) [:color :set] "red")
                                                (j/call-in (db/get-entity-obj! :selection :material) [:color :set] "white"))
                                              (if target
                                                (do
                                                  (ecs/swap! :selection assoc :enabled true)
                                                  (j/call (db/get-entity-obj! :selection :position) :copy target))
                                                (ecs/swap! :selection assoc :enabled false))))})))
