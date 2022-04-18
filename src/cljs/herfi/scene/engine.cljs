(ns herfi.scene.engine
  (:require
    ["camera-controls" :default CameraControls]
    ["three/build/three.module.js" :as THREE]
    ["three/examples/jsm/libs/stats.module.js" :as Stats]
    ["three/examples/jsm/loaders/DRACOLoader.js" :as DRACOLoader]
    ["three/examples/jsm/loaders/FBXLoader.js" :as FBXLoader]
    ["three/examples/jsm/loaders/GLTFLoader.js" :as GLTFLoader]
    [applied-science.js-interop :as j]
    [herfi.scene.api :as api :refer [set*]]
    [herfi.scene.db :as db]
    [herfi.scene.particles :as particles]))

(j/call CameraControls :install #js {:THREE THREE})

(defn on-window-resize [pixel-size ^THREE/Camera camera renderer]
  (set! (.-aspect camera) (/ (.-innerWidth js/window) (.-innerHeight js/window)))
  (.updateProjectionMatrix camera)
  (.setSize renderer (/ (.-innerWidth js/window) pixel-size) (/ (.-innerHeight js/window) pixel-size))
  (set! (.. renderer -domElement -style -width) (str (.-innerWidth js/window) "px"))
  (set! (.. renderer -domElement -style -height) (str (.-innerHeight js/window) "px")))

(defn add-default-controls [camera renderer]
  (let [controls (CameraControls. camera (.-domElement renderer))]
    (set* controls
          {:enabled true
           :minPolarAngle 0.1
           :maxPolarAngle (/ js/Math.PI 2)
           :minDistance 1
           :maxDistance 30
           :azimuthRotateSpeed 0.3
           :polarRotateSpeed -0.2
           :draggingDampingFactor 1})
    (j/assoc-in! controls [:mouseButtons :right] (j/get-in CameraControls [:ACTION :ROTATE]))
    controls))

(defn init
  [& {:keys [pixel-size] :or {pixel-size 1}}]
  (let [container (.createElement js/document "div")
        _ (.setAttribute container "id" "scene")
        scene (THREE/Scene.)
        camera (THREE/PerspectiveCamera. 70 (/ (.-innerWidth js/window) (.-innerHeight js/window)) 1 1000)
        renderer (THREE/WebGLRenderer. #js {:antialias false})
        stats (Stats/default.)]
    (j/assoc-in! stats [:dom :style :top] "65px")
    (j/assoc-in! stats [:dom :style :left] "12px")
    (.appendChild (.-body js/document) container)
    (.set (.-position camera) 10 10 (- 10))
    (set! (.-background scene) (THREE/Color. 0xf0f0f0))
    (.setPixelRatio renderer (.-devicePixelRatio js/window))
    (.setSize renderer (/ (.-innerWidth js/window) pixel-size) (/ (.-innerHeight js/window) pixel-size))
    (set! (.. renderer -shadowMap -enabled) true)
    (set! (.. renderer -shadowMap -type) THREE/PCFShadowMap)
    (set! (.-gammaOutput renderer) true)
    (.appendChild container (.-domElement renderer))
    (.appendChild (.-body js/document) (.-dom stats))
    (.addEventListener js/window "resize" (partial on-window-resize pixel-size camera renderer) false)
    (on-window-resize pixel-size camera renderer)
    (db/init
      {:scene scene
       :controls (add-default-controls camera renderer)
       :camera camera
       :renderer renderer
       :stats stats
       :render-loop-fns []})))

(defn remove-all [scene]
  (let [meshes (atom [])]
    (j/call scene :traverse
            (fn [node]
              (when (and (not= scene node) (j/get node :isMesh))
                (swap! meshes conj node))))
    (doseq [mesh @meshes]
      (j/call mesh :removeFromParent)
      (j/call-in mesh [:geometry :dispose])
      (j/call-in mesh [:material :dispose])
      (j/call scene :remove mesh))))

(defn animate [^THREE/Clock clock render-loop-fn]
  (let [delta (.getDelta clock)
        {:keys [camera scene renderer controls stats]} (db/get)
        req-id (js/requestAnimationFrame (partial animate clock render-loop-fn))]
    (db/set :req-id req-id)

    (render-loop-fn delta)
    (particles/update)

    (when scene
      (j/call scene :traverse
              (fn [obj]
                (let [m (j/get obj :mixer)]
                  (when m (.update m delta))))))
    (.update controls delta)
    (.render renderer scene camera)
    (.update stats)
    true))

(defn start-animation-loop [eng game-loop-var]
  (let [clock (THREE/Clock.)]
    (animate clock game-loop-var))
  eng)

(defn add-default-lights [scene]
  (.add scene (THREE/AmbientLight. 0xffffff 1.0))
  (let [light (THREE/DirectionalLight. 0xffffff 0.5)
        camera-range 400
        d camera-range
        -d (- camera-range)]
    (set* light
          {:position (api/vec->vec3 [100 100 100])
           :intensity 1
           :castShadow true
           :shadow-bias -0.001
           :shadow-mapSize-width 4096
           :shadow-mapSize-height 4096
           :shadow-camera-left -d
           :shadow-camera-right d
           :shadow-camera-top d
           :shadow-camera-bottom -d})
    (.set (.-position light) 100 100 50)
    (.add scene light)))

(defn set-background [scene color]
  ;; add fog
  (aset scene "fog" (THREE/FogExp2. color 0.0128 10))
  (aset scene "background" (THREE/Color. color)))

(defn setup-default-scene [scene]
  (set-background scene 0x20AAF3)
  (add-default-lights scene))

(defn load-assets [manager & assets]
  (let [loader (memoize #(case %
                           :gltf (GLTFLoader/GLTFLoader. manager)
                           :fbx (FBXLoader/FBXLoader. manager)
                           :draco (let [loader (GLTFLoader/GLTFLoader. manager)
                                        draco-loader (doto (DRACOLoader/DRACOLoader.)
                                                       (j/call :setDecoderPath "libs/draco/")
                                                       (j/call :setDecoderConfig #js {:type "js"})
                                                       (j/call :preload))]
                                    (j/call loader :setDRACOLoader draco-loader)
                                    loader)))]
    (doseq [{:keys [type url on-done id]} assets]
      (let [loader (loader type)]
        (.load loader url #(do
                             (when on-done (on-done %))
                             (db/set-in :assets :models id %)))))))
