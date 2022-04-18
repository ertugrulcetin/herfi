(ns herfi.scene.particles
  (:refer-clojure :exclude [update])
  (:require
    ["/herfi/vendor/attractor" :as attractor]
    ["canvas-sketch-util/random" :as Random]
    ["three-nebula" :refer [GPURenderer]]
    ["three-nebula$default" :as System]
    ["three/build/three.module.js" :as THREE]
    [applied-science.js-interop :as j]
    [com.rpl.specter :as sp :refer-macros [select]]
    [herfi.scene.api :as api]
    [herfi.scene.db :as db])
  (:require-macros
    [shadow.resource :as rc]))

(attractor/init THREE)

(def emitters
  {:particle (js/JSON.parse (rc/inline "particles/particle.json"))
   :attack-flame (js/JSON.parse (rc/inline "particles/flame.json"))
   :attack-ice (js/JSON.parse (rc/inline "particles/ice.json"))
   :attack-light (js/JSON.parse (rc/inline "particles/light.json"))})

(defn add-particle [json on-load]
  (-> (.fromJSONAsync System json THREE)
      (.then on-load)))

(defn add-renderer [system obj]
  (j/call system :addRenderer (GPURenderer. obj THREE)))

(defn destroy [system]
  (doseq [emitter (j/get system :emitters)]
    (j/call emitter :stopEmit)
    (doseq [p (j/get emitter :particles)]
      (when (j/get emitter :parent) (j/call-in emitter [:parent :dispatch] "PARTICLE_DEAD" p))
      (when (j/get emitter :bindEmitterEvent) (j/call emitter :dispatch "PARTICLE_DEAD" p))
      (j/call-in emitter [:parent :pool :expire] (.reset p)))
    (j/assoc-in! emitter [:particles :length] 0))
  (j/call system :destroy))

(defn update []
  (let [systems (select [:entities sp/MAP-VALS (sp/must :particles) sp/MAP-VALS] (db/get))]
    (doseq [s systems]
      (j/call s :update))))

(let [simulation (fn [] (-> [attractor/dadrasAttractor attractor/aizawaAttractor
                             attractor/arneodoAttractor attractor/dequanAttractor
                             attractor/lorenzAttractor attractor/lorenzMod2Attractor]
                            shuffle
                            first))
      colors ["#fbe555" "#fb9224" "#f45905" "#be8abf" "#ffeed0" "#feff89"]]
  (defn storm-line
    ([]
     (storm-line nil))
    ([duration]
     (let [uuid (str (random-uuid))
           sim (simulation)
           [positions current-position] (attractor/createAttractor 5)
           mesh-line (api/mesh-line)
           mesh-line-mat (api/mesh-line-mat {:transparent true
                                             :lineWidth (Random/range 0.1 0.2)
                                             :color (first (shuffle colors))})
           mesh (api/mesh mesh-line mesh-line-mat)]
       (.setPoints mesh-line positions)
       {:name uuid
        :obj mesh
        :update (fn []
                  (let [next-position (attractor/updateAttractor
                                        current-position
                                        2.2
                                        sim
                                        0.005)
                        _ (.add next-position (api/vec3 0 2.5 0))]
                    (.advance mesh-line next-position)))
        :on-end (fn []
                  (j/call mesh :removeFromParent)
                  (j/call-in mesh [:geometry :dispose])
                  (j/call-in mesh [:material :dispose]))
        :duration duration}))))
