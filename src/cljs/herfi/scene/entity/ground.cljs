(ns herfi.scene.entity.ground
  (:require
    [herfi.scene.ecs :as ecs]))

(defn- get-fragment-shader [shader]
  (str
    "#define ss(a, b, c) smoothstep(a, b, c)
     uniform vec3 selection;
     varying vec3 vPos;\n"
    (.-fragmentShader shader)))

(def circle-shader
  "#include <dithering_fragment>
   // shape
   float dist = distance(selection.xz, vPos.xz);
   float r = 0.25;
   float shape = (ss(r-0.1, r, dist)*0.75 + 0.25) - ss(r, r + 0.1, dist);
   vec3 col = mix(gl_FragColor.rgb, vec3(0, 1, 0.25), shape);
   gl_FragColor = vec4(col, gl_FragColor.a);")

(defn create-ground []
  (-> (ecs/create-entity :ground {:position [0 0 0]
                                  :rotation [(/ js/Math.PI -2) 0 0]})
      (ecs/add-component :render {:type :plane
                                  :geometry {:width 500
                                             :height 500}
                                  :material {:type :lambert
                                             :color 0x637C60}})
      #_(ecs/add-component :script
        (let [uniforms (j/lit {.-selection {.-value (api/vec3)}})
              material (db/get-entity-obj! :ground :material)]
          {:init (fn []
                   (j/assoc! material :onBeforeCompile
                     (fn [shader]
                       (j/assoc-in! shader [:uniforms :selection] (j/get uniforms :selection))
                       (j/assoc! shader :vertexShader
                         (str/replace
                           (str "varying vec3 vPos;\n" (.-vertexShader shader) "\n")
                           "#include <begin_vertex>"
                           "#include <begin_vertex>\nvPos = transformed;\n"))
                       (j/assoc! shader :fragmentShader
                         (str/replace
                           (get-fragment-shader shader)
                           "#include <dithering_fragment>"
                           circle-shader)))))
           :update (fn []
                     (let [ground (db/get-entity-obj! :ground)
                           pos (if-let [char-name (db/get :selected-character)]
                                 (db/get-entity-obj! char-name :position)
                                 (db/get-entity-obj! :robot :position))]
                       (j/call ground :worldToLocal (j/call-in uniforms [:selection :value :copy] pos))))}))))
