(ns herfi.scene.api
  (:refer-clojure :exclude [clone])
  (:require
    ["/herfi/vendor/camera-shake" :as camera-shake]
    ["three-spritetext" :default SpriteText]
    ["three.meshline" :refer [MeshLine MeshLineMaterial]]
    ["three/build/three.module.js" :as THREE]
    ["three/examples/jsm/geometries/RoundedBoxGeometry.js" :as RoundedBoxGeometry]
    ["three/examples/jsm/utils/SkeletonUtils.js" :as SkeletonUtils]
    [applied-science.js-interop :as j]
    [clojure.string :as str]
    [clojure.walk :as walk]
    [herfi.scene.db :as db])
  (:require-macros
    [herfi.scene.macros :as m]))

(camera-shake/init THREE)

(def clock (THREE/Clock.))

(def double-side THREE/DoubleSide)

(defn add-scene
  ([x]
   (.add (db/get :scene) x))
  ([scene x]
   (.add scene x)))

(defn axes-helper [size]
  (THREE/AxesHelper. size))

(defn arrow-helper [dir origin len color]
  (THREE/ArrowHelper. dir origin len color))

(defn vec3
  ([]
   (vec3 0 0 0))
  ([x y z]
   (THREE/Vector3. x y z)))

(defn vec3->vec [v]
  [(.-x v) (.-y v) (.-z v)])

(defn vec->vec3 [v]
  (vec3 (or (nth v 0) 0)
        (or (nth v 1) 0)
        (or (nth v 2) 0)))

(defn clone [x]
  (.clone x))

(defn clone-skinned [x]
  (SkeletonUtils/clone x))

(defn copy [^THREE/Vector3 v1 v2]
  (.copy v1 v2))

(defn add [v1 v2]
  (.add v1 v2))

(defn sub [v1 v2]
  (.sub v1 v2))

(defn normalize [v]
  (.normalize v))

(defn negate [^THREE/Vector3 v]
  (.negate v))

(defn multiply-scalar [^THREE/Vector3 v s]
  (.multiplyScalar v s))

(defn length [v]
  (.length v))

(defn apply-quaternion [^THREE/Vector3 v q]
  (.applyQuaternion v q))

(defn quat []
  (THREE/Quaternion.))

(defn distance-to [^THREE/Vector3 v1 v2]
  (.distanceTo v1 v2))

(defn set* [obj opts]
  (doseq [[k v] opts]
    (let [ks (map keyword (str/split (name k) #"-"))]
      (j/assoc-in! obj ks v)))
  obj)

(defn mesh
  ([]
   (THREE/Mesh.))
  ([geo mat]
   (THREE/Mesh. geo mat)))

(defn instanced-mesh
  ([]
   (THREE/InstancedMesh.))
  ([geo mat count]
   (THREE/InstancedMesh. geo mat count)))

(defn object []
  (THREE/Object3D.))

(defn mat4 []
  (THREE/Matrix4.))

(defn cylinder [{:keys [radius-top
                        radius-bottom
                        height
                        radial-segments
                        height-segments
                        open-ended?
                        theta-start
                        theta-length]
                 :or {radius-top 1.0
                      radius-bottom 1.0
                      height 1.0
                      radial-segments 8
                      height-segments 1
                      open-ended? false
                      theta-start 0
                      theta-length (* 2 js/Math.PI)}}]
  (THREE/CylinderGeometry. radius-top
                           radius-bottom
                           height
                           radial-segments
                           height-segments
                           open-ended?
                           theta-start
                           theta-length))

(defn sphere [radius]
  (THREE/SphereGeometry. radius))

(defn ring [{:keys [inner-rad outer-rad theta-segs]}]
  (THREE/RingGeometry. inner-rad outer-rad theta-segs))

(defn plane [{:keys [width
                     height
                     width-segments
                     height-segments]
              :or {width 1
                   height 1
                   width-segments 1
                   height-segments 1}}]
  (THREE/PlaneGeometry. width height width-segments height-segments))

(defn lambert-material [opts]
  (THREE/MeshLambertMaterial. (clj->js opts)))

(defn standard-material [opts]
  (THREE/MeshStandardMaterial. (clj->js opts)))

(defn basic-material [opts]
  (THREE/MeshBasicMaterial. (clj->js opts)))

(defn normal-material
  ([]
   (normal-material nil))
  ([opts]
   (THREE/MeshNormalMaterial. (clj->js opts))))

(defn look-at
  ([^THREE/Mesh mesh vec]
   (.lookAt mesh vec))
  ([^THREE/Mesh mesh x y z]
   (.lookAt mesh x y z)))

(defn rotate-x [obj rad]
  (.rotateX obj rad))

(defn rotate-y [obj rad]
  (.rotateY obj rad))

(defn rotate-z [obj rad]
  (.rotateZ obj rad))

(defn euler
  ([[x y z]]
   (euler x y z "XYZ"))
  ([x y z]
   (euler x y z "XYZ"))
  ([x y z order]
   (THREE/Euler. x y z order)))

(defn set-from-euler [q rotation]
  (j/call q :setFromEuler rotation))

(defn scale
  ([obj n]
   (scale obj n n n))
  ([obj x y z]
   (when (j/get obj :scale)
     (set* obj {:scale-x x
                :scale-y y
                :scale-z z}))))

(defn rotation [obj v]
  (when (j/get obj :rotation)
    (set* obj {:rotation-x (or (nth v 0) 0)
               :rotation-y (or (nth v 1) 0)
               :rotation-z (or (nth v 2) 0)
               :rotation-order "XYZ"})))

(defn position [obj v]
  (when (j/get obj :position)
    (j/call-in obj [:position :set] (or (nth v 0) 0) (or (nth v 1) 0) (or (nth v 2) 0))
    obj))

(defn color [c]
  (THREE/Color. c))

(defn raycaster
  ([]
   (THREE/Raycaster.))
  ([origin dir near far]
   (THREE/Raycaster. origin dir near far)))

(defn set-from-camera [^THREE/Raycaster raycaster coords cam]
  (.setFromCamera raycaster coords cam))

(defn intersect-object [^THREE/Raycaster raycaster obj recursive?]
  (.intersectObject raycaster obj recursive?))

(defn intersect-objects [^THREE/Raycaster raycaster objs recursive?]
  (.intersectObjects raycaster objs recursive?))

(defn get-world-direction [obj v3]
  (j/call obj :getWorldDirection v3)
  v3)

(defn apply-axis-angle [^THREE/Vector3 v axis angle]
  (.applyAxisAngle v axis angle))

(defn deg->rad [deg]
  (THREE/MathUtils.degToRad deg))

(defn get-elapsed-time []
  (.getElapsedTime clock))

(defn get-delta []
  (.getDelta clock))

(defn bounding-box-helper [obj color]
  (THREE/BoundingBoxHelper. obj color))

(defn box [w h d]
  (THREE/BoxGeometry. w h d))

(defn box3 []
  (THREE/Box3.))

(defn intersects-box [b1 b2]
  (j/call b1 :intersectsBox b2))

(defn box3-helper [b]
  (THREE/Box3Helper. b))

(defn group []
  (THREE/Group.))

(defn rounded-box [width height depth segments radius]
  (RoundedBoxGeometry/RoundedBoxGeometry. width height depth segments radius))

(defn curve [points]
  (THREE/CatmullRomCurve3. points))

(defn loading-manager [{:keys [on-start on-load on-progress on-error]}]
  (cond-> (THREE/LoadingManager.)
    on-start (j/assoc! :onStart on-start)
    on-load (j/assoc! :onLoad on-load)
    on-progress (j/assoc! :onProgress on-progress)
    on-error (j/assoc! :onError on-error)))

(defn mesh-line []
  (MeshLine.))

(defn mesh-line-mat [opts]
  (MeshLineMaterial. (clj->js opts)))

(defn animation-mixer [mesh]
  (THREE/AnimationMixer. mesh))

(defn find-object-by-name [o name]
  (j/call o :getObjectByName name))

;; TODO not every model has animation!
(defn find-model [assets name skinned?]
  (let [clone-fn (if skinned? clone-skinned clone)
        mesh (clone-fn (j/call-in assets [:scene :getObjectByName] name))]
    (j/assoc! mesh :mixer (animation-mixer mesh))
    (j/assoc! mesh :animations (.-animations assets))
    mesh))

(defn find-asset
  ([name]
   (find-asset name nil))
  ([name child-idx]
   (if child-idx
     (j/get-in (find-object-by-name (db/get! :assets :models :environment :scene) name) [:children child-idx])
     (find-object-by-name (db/get! :assets :models :environment :scene) name))))

(defn obj->vec [obj k]
  (let [o (j/get obj k)
        x (j/get o :x)
        y (j/get o :y)
        z (j/get o :z)]
    [x y z]))

(defn look-point [mesh target]
  (let [y (j/get target :y)
        mesh-y (j/get-in mesh [:position :y])
        mesh-pos (j/get mesh :position)]
    (.setY mesh-pos y)
    (look-at mesh target)
    (.setY mesh-pos mesh-y)))

(defn sprite-text [text opts]
  (set* (SpriteText. text) opts))

(defn find-clip-by-name [animations name]
  (THREE/AnimationClip.findByName animations name))

(defn create-camera-shake []
  (camera-shake/CameraShake. (db/get :controls) 2000 10 1.5))

;; (defn three? [x]
;;  (m/three? x))
;;
;; (defn pp
;;  ([id]
;;   (pp id nil))
;;  ([id keys]
;;   (let [keys (into [:rotation :position :scale :name :type :castShadow :receiveShadow :visible] keys)
;;         obj (db/get-entity-obj! id)]
;;     (->> (js->clj (js/Object.entries obj))
;;          (map
;;            (fn [[k v]]
;;              [k (if (three? v)
;;                   (if (or (= THREE/Vector3 (.-constructor v))
;;                           (= THREE/Euler (.-constructor v)))
;;                     (into {} (js->clj (js/Object.entries v)))
;;                     v)
;;                   v)])
;;            (js->clj (js/Object.entries obj)))
;;          (into {})
;;          (walk/keywordize-keys)
;;          (#(select-keys % keys))))))

(defn object3d? [o]
  (instance? THREE/Object3D o))
