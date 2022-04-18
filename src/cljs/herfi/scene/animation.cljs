(ns herfi.scene.animation
  (:require
    ["three/build/three.module.js" :as THREE]
    [applied-science.js-interop :as j]
    [herfi.scene.api :as api]))

(defn stop-clip [^THREE/Mesh mesh]
  (-> mesh
      (j/get :mixer)
      (j/call :setTime 0)
      (j/call :stopAllAction)))

(defn get-action
  ([mesh animation-name]
   (get-action mesh animation-name nil))
  ([mesh animation-name root-index]
   (let [animations (j/get mesh :animations)
         clip (api/find-clip-by-name animations animation-name)
         mixer (j/get mesh :mixer)
         optional-root (when root-index (j/get-in mesh [:children root-index]))]
     (j/call mixer :clipAction clip optional-root))))

(defn- set-weight [action weight]
  (j/assoc! action :enabled true)
  (j/call action :setEffectiveTimeScale 1)
  (j/call action :setEffectiveWeight weight))

(defn execute-crossfade [start-action end-action duration]
  (set-weight end-action 1)
  (j/assoc! end-action :time 0)
  (j/call start-action :crossFadeTo end-action duration true))

(defn- on-loop-finished [event start-action end-action duration loop-done?]
  (when (= (j/get event :action) start-action)
    (reset! loop-done? true)
    (execute-crossfade start-action end-action duration)))

(defn synchronize-crossfade [mixer start-action end-action duration]
  (let [loop-done? (atom false)
        on-loop-finished* #(on-loop-finished % start-action end-action duration loop-done?)]
    (add-watch loop-done? :watcher
               (fn []
                 (j/call mixer :removeEventListener "loop" on-loop-finished*)
                 (remove-watch loop-done? :watcher)))
    (j/call mixer :addEventListener "loop" on-loop-finished*)))

(defn prepare-cross-fade [{:keys [mixer start-action end-action duration idle-animation-name]}]
  (if (= (j/get (j/call start-action :getClip) :name) idle-animation-name)
    (execute-crossfade start-action end-action duration)
    (synchronize-crossfade mixer start-action end-action duration)))

(defn active-actions
  ([obj idle-animation-name action-names]
   (active-actions obj idle-animation-name action-names nil))
  ([obj idle-animation-name action-names root-index]
   (doseq [animation-name action-names]
     (let [action (get-action obj animation-name root-index)]
       (set-weight action (if (= animation-name idle-animation-name) 1 0))
       (j/call action :play)))))

(defn fade-to-action [start-action end-action speed duration loop-once? clamp?]
  (j/call start-action :fadeOut duration)
  (j/assoc! end-action :clampWhenFinished (boolean? clamp?))
  (doto ^THREE/AnimationAction end-action
    (.reset)
    (.setLoop (if loop-once? THREE/LoopOnce THREE/LoopRepeat))
    (.setEffectiveTimeScale speed)
    (.setEffectiveWeight 1)
    (.fadeIn duration)
    (.play)))
