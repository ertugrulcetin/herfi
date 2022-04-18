(ns herfi.scene.picker
  (:require
    [applied-science.js-interop :as j]
    [herfi.scene.api :as api]
    [herfi.scene.db :as db]))

(def mouse-down? (atom false))
(def raycaster (api/raycaster))

(let [objs (delay #js [(db/get-entity-obj! :ground) (db/get-entity-obj! :players)])
      pos #js {}]
  (defn mouse-pick [e raycaster container camera]
    (j/assoc! pos
              :x (-> e (j/get :clientX) (/ (j/get container :clientWidth)) (* 2) (- 1))
              :y (-> e (j/get :clientY) (/ (j/get container :clientHeight)) (* -2) (+ 1)))
    (api/set-from-camera raycaster pos camera)
    (api/intersect-objects raycaster @objs true)))

(defn register [callback]
  (let [{:keys [controls camera]} (db/get)
        canvas (db/get! :renderer :domElement)
        ray (fn [e type]
              (when-not (j/get controls :enabled)
                (when-let [picked (first (seq (mouse-pick e raycaster canvas camera)))]
                  (callback picked type))))]
    (.addEventListener canvas "mousemove" (fn [e]
                                            (when (and @mouse-down? (= (j/get e :which) 1))
                                              (ray e :move))))
    (.addEventListener canvas "mousedown" (fn [e]
                                            (when (= (j/get e :which) 1)
                                              (j/assoc! controls :enabled false)
                                              (reset! mouse-down? true)
                                              (ray e :down))))
    (.addEventListener canvas "mouseup" (fn [e]
                                          (when (= (j/get e :which) 1)
                                            (j/assoc! controls :enabled true)
                                            (reset! mouse-down? false))))))
