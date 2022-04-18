(ns herfi.ui.components
  (:require
    ["@stitches/react" :refer [styled css globalCss keyframes]]
    [applied-science.js-interop :as j]
    [reagent.core])
  (:require-macros
    [herfi.ui.macros :refer [create-comp]]))

(def styled* styled)

(def global-styles
  (globalCss (j/lit {:body {:margin 0}
                     :canvas {:display "block"}
                     :th {:border "1px solid #dddddd"
                          :text-align :center
                          :padding 8}})))

(def ui-panel
  (create-comp :div
               {:user-select :none
                :pointer-events :none
                :position :absolute
                :height "100%"
                :width "100%"
                :top 0
                :left 0}))

(def bar-container
  (create-comp :div
               {:position :absolute
                :text-align :center
                :top 10
                :left 10
                :height 20
                :width 200
                :border-radius 5
                :border "2px solid black"
                :variants
                {:variant
                 {:mana {:top 35}
                  :enemy-health {:width 300
                                 :top 20
                                 :left "calc(50% - 150px);"}}}}))

(def bar
  (create-comp :div
               {:position :relative
                :border-radius "3px"
                :height "101%"
                :width 200
                :background-color "#d91616"
                :variants
                {:variant
                 {:mana {:background-color "#146ebb"}}}}))

(def bar-text
  (create-comp :span
               {:position :relative
                :font-size 16
                :font-family "monospace"
                :color :white
                :padding-top 5
                :top -20
                :cursor :default
                :z-index 999}))

(def renderer-info
  (create-comp :div
               {:display :flex
                :flex-direction :column
                :color :white
                :position :absolute
                :top 120
                :left 13
                :font-family "monospace"}))

(def skill-bar
  (create-comp :div
               {:position :absolute
                :left "calc(50% - 225px);"
                :border "1px solid"
                :border-radius 3
                :bottom 20
                :background-color "#10131ca3"
                :height 55
                :width 450
                :pointer-events :all
                :display :grid
                :gap 3
                :grid-auto-rows "minmax(40px, auto)"
                :grid-auto-columns "minmax(50px, auto)"}))

(def skill-box
  (create-comp :div
               {:position :relative
                :border-radius 3
                :border "3px solid #293c40"}))

(def skill-img
  (create-comp :img
               {:position :absolute
                :top 2
                :left 2
                :width 45
                :height 45
                :variants {:variant {:got-hit {:position "unset"
                                               :padding 3
                                               :padding-top 10}
                                     :not-enough-mana {:filter "grayscale(100%)"}}}}))

(def slot-text
  (create-comp :span
               {:position :absolute
                :right 3
                :top 3
                :color "#bababa"
                :font-size 13
                :font-family "monospace"
                :font-weight :bold
                :line-height "8px"}))

(def text
  (create-comp :span
               {:position :absolute
                :left 90
                :top 32
                :color "white"
                :font-size 13
                :font-family "monospace"
                :font-weight :bold
                :line-height "8px"}))

(def skill-keyframes-rota
  ((keyframes (j/lit {"0%" {:transform "rotate(0deg)"}
                      "100%" {:transform "rotate(360deg)"}}))))

(def skill-keyframes-opa
  ((keyframes (j/lit {"0%" {:opacity "1"}
                      "50%, 100%" {:opacity "0"}}))))

(def skill-keyframes-hit
  ((keyframes (j/lit {"0%, 100%" {:opacity "0"}
                      "50%" {:opacity "1"}}))))

(def skill-loading-wrapper
  (css (j/lit {:background :white
               :opacity 0.5
               :z-index 1
               :width 45
               :height 45
               :position :relative
               :top 2
               :left 2})))

(def skill-loading-pie
  (css (j/lit {:width "50%"
               :height "100%"
               :position :absolute
               :transform-origin "100% 50%"
               :background :grey
               :border "10px solid rgba (0, 0, 0, 0.4)"})))

(defn- skill-loading-spinner [duration]
  ((css (j/lit {:border-radius "100% 0 0 100% / 50% 0 0 50%"
                :z-index 200
                :border-right :none
                :animation (str skill-keyframes-rota " " duration "s linear infinite")}))))

(defn- skill-loading-filler [duration]
  ((css (j/lit {:border-radius "0 100% 100% 0 / 0 50% 50% 0"
                :z-index 100
                :border-left :none
                :animation (str skill-keyframes-opa " " duration "s steps(1, end) infinite reverse")
                :left "50%"
                :opacity 0}))))

(defn skill-loading-mask [duration]
  ((css (j/lit {:width "50%"
                :height "100%"
                :position :absolute
                :z-index 300
                :opacity 1
                :background :inherit
                :animation (str skill-keyframes-opa " " duration "s steps(1, end) infinite")}))))

(defn use-skill [duration]
  [:div {:class (skill-loading-wrapper)}
   [:div {:class [(skill-loading-pie) (skill-loading-spinner duration)]}]
   [:div {:class [(skill-loading-pie) (skill-loading-filler duration)]}]
   [:div {:class (skill-loading-mask duration)}]])

(def right-panel
  (create-comp :div
               {:position :absolute
                :text-align :right
                :margin-right 5
                :top 0
                :right 0
                :bottom 20
                :height 55
                :width 350
                :pointer-events :all
                :variants {:variant {:killed-by-info {:width 500
                                                      :top 100
                                                      :right 20
                                                      :color "white"
                                                      :font-family "monospace"
                                                      :font-size 15
                                                      :pointer-events :none}}}}))

(def skill-hit
  ((css (j/lit {:opacity 1
                :animation (str skill-keyframes-hit " 1s linear infinite")}))))

(def info-box*
  (create-comp :div
               {:position :absolute
                :border-radius 5
                :text-align :left
                :font-size 14
                :font-family "monospace"
                :font-weight "bold"
                :color "white"
                :margin-right 10
                :right 0
                :bottom 20
                :overflow-y :auto
                :padding 10
                :height 150
                :width 350
                :pointer-events :all
                :background "grey"
                :opacity 0.75
                "&::-webkit-scrollbar" {:display :none}
                ;; For Firefox
                :-ms-overflow-style :none
                :scrollbar-width :none
                :variants {:variant {:chat {:width 400
                                            :pointer-events :none
                                            :left 10
                                            :background-color "transparent"}}}}))

(def info-box-container
  (create-comp :div
               {:display :flex
                :flex-direction :column-reverse
                :variants {:variant {:chat {:flex-direction :column}}}}))

(def chat-box
  (create-comp :div
               {:overflow-y :auto
                "&::-webkit-scrollbar" {:display :none}
                ;; For Firefox
                :-ms-overflow-style :none
                :scrollbar-width :none}))

(def tooltip-text
  (create-comp :span
               {:visibility "hidden"
                :width 120
                :background-color "black"
                :color "#fff"
                :text-align "center"
                :border-radius 6
                :padding "5px 0"
                :position "absolute"
                :z-index 1
                :bottom "100%"
                :left "50%"
                :margin-left -60}))

(def skill-info-container
  (create-comp :div
               {:display :flex
                :justify-content :center
                :align-items :center
                :position :absolute
                :text-align :center
                :border-radius 5
                :width 450
                :height 50
                :bottom 52
                :background "#8080807d"}))

(def skill-info-text
  (create-comp :span
               {:font-size 14
                :font-family "monospace"
                :color :white
                :padding 5}))

(def position
  (create-comp :div
               {:position :absolute
                :text-align :start
                :margin-right 5
                :top 10
                :left 215
                :height 55
                :width 350
                :pointer-events :all
                :variants {:variant {:ping {:top 38
                                            :left 218}}}}))

(def position-text
  (create-comp :span
               {:font-size 14
                :font-family "monospace"
                :color :white
                :padding 5}))

(def modal-container
  (create-comp :div
               {:pointer-events :all
                :height 200
                :width 400
                :font-size 15
                :font-family "monospace"
                :background-color "#80808069"
                :border "1px solid #80808069"
                :border-radius 5
                :position :absolute
                :top 0
                :bottom 0
                :left 0
                :right 0
                :margin :auto
                :variants {:variant {:login {:font-size 15}
                                     :scoreboard {:background-color "#808080bf"
                                                  :height 400
                                                  :width 700
                                                  :bottom 150
                                                  :color :white
                                                  :overflow :auto}}}}))

(def modal-content
  (create-comp :div
               {:display :flex
                :justify-content :center
                :text-align :center
                :font-family "monospace"
                :margin-top 80
                :variants {:variant {:login {:margin-top 50}
                                     :scoreboard {:margin-top 20}}}}))

(def modal-wrapper
  (create-comp :div
               {:display :flex
                :flex-direction :column
                :align-items :center
                :variants {:variant {:scoreboard {:overflow :auto}}}}))

(def modal-text
  (create-comp :span
               {:font-size 14
                :variants {:variant
                           {:login {:color :red
                                    :font-size 12}}}}))

(def modal-button
  (create-comp :button
               {:font-family "monospace"
                :width 60
                :margin-top 30
                :font-size 16
                :variants {:variant
                           {:login {:width 70
                                    :margin-top 20}
                            :connection-lost {:width 90}}}}))

(def scoreboard
  (create-comp :table
               {:border-collapse :collapse
                :width 500}))

(def killed-by-info
  ((keyframes (j/lit {"0%" {:opacity 0}
                      "100%" {:opacity 1}}))))

(def killed-by-text
  (create-comp :span
               {:line-height "1.5em"
                :animation (str killed-by-info " " "1s")}))

(def chat-input
  (create-comp :input
               {:width 345
                :height 28
                :font-family "monospace"
                :background-color "#10131dcc"
                :outline :none
                :color :white
                :font-weight :bold
                :font-size 14
                :border "2px solid #10131dcc"
                :border-radius 2}))
