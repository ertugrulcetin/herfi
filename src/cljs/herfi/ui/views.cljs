(ns herfi.ui.views
  (:require
    [applied-science.js-interop :as j]
    [herfi.character :as common.char]
    [herfi.common.communication :refer [on fire]]
    [herfi.common.config :as config]
    [herfi.ui.components :as c]
    [herfi.ui.events :as events]
    [herfi.ui.subs :as subs]
    [re-frame.core :refer [subscribe dispatch dispatch-sync]]
    [reagent.core :as r]))

(defn- add-used-skill-to-msg-box [skill]
  (let [msg (case skill
              :attack-flame "Using Flame attack"
              :attack-ice "Using Ice attack"
              :attack-light "Using Light attack"
              :punch "Punching enemy"
              :shield "Using Shield"
              :teleport "Using Teleport"
              :mp-potion "Using MP Potion"
              :hp-potion "Using HP Potion")]
    (dispatch [::events/add-msg msg])))

(defn- skill-info [show-skill-info? skill-info-text]
  [c/skill-info-container
   [c/skill-info-text (if @show-skill-info?
                        @skill-info-text
                        "Press Key Z to select closest enemy")]])

(defn- login-modal []
  [c/modal-container
   {:variant :login}
   [c/modal-content
    {:variant :login}
    [c/modal-wrapper
     [:input
      {:style {:width 200
               :height 20
               :border-radius 5
               :font-family "monospace"}
       :placeholder "Pick a cool username..."
       :on-key-press (fn [event]
                       (when (and (= "Enter" (.-code event))
                                  (not @(subscribe [::subs/logging?])))
                         (dispatch [::events/enter-world])))
       :on-change #(do
                     (dispatch [::events/clear-error-msg])
                     (dispatch [::events/set-input-username (-> % .-target .-value)]))
       :ref #(some-> % .focus)}]
     (when-let [error-msg @(subscribe [::subs/error-msg])]
       [c/modal-text {:variant :login} error-msg])
     [:div
      {:style {:margin-top 10}}
      [:span [:u "Resolution"]]
      [:div
       {:style {:display :flex
                :flex-direction :row}}
       [:div
        [:input {:type "radio"
                 :name "resolution"
                 :on-change #(dispatch [::events/set-resolution :high])}]
        [:label "High"]]
       [:div
        [:input {:type "radio"
                 :name "resolution"
                 :on-change #(dispatch [::events/set-resolution :medium])}]
        [:label "Medium"]]
       [:div
        [:input {:type "radio"
                 :name "resolution"
                 :on-change #(dispatch [::events/set-resolution :low])}]
        [:label "Low"]]]]
     [c/modal-button
      {:variant :login
       :disabled @(subscribe [::subs/logging?])
       :on-click #(dispatch [::events/enter-world])}
      "Enter"]]]])

(defn- respawn-modal []
  (when (zero? @(subscribe [::subs/hp]))
    [c/modal-container
     [c/modal-content
      [c/modal-wrapper
       [c/modal-text "You died, click OK to respawn in the map."]
       [c/modal-button {:on-click #(fire :respawn true)} "OK"]]]]))

(defn- connection-lost-modal []
  (when @(subscribe [::subs/ws-conn-lost?])
    [c/modal-container
     [c/modal-content
      [c/modal-wrapper
       [c/modal-text "Connection lost! Please click Refresh button to refresh the page."]
       [c/modal-button
        {:variant :connection-lost
         :on-click #(js/location.reload true)}
        "Refresh"]]]]))

(defn- hp-and-mana []
  [:<>
   [c/bar-container
    [c/bar
     {:style {:width (* 2 @(subscribe [::subs/hp]))}}]
    [c/bar-text (str @(subscribe [::subs/hp]) "/100")]]
   [c/bar-container
    {:variant :mana}
    [c/bar {:variant :mana
            :style {:width (* 2 @(subscribe [::subs/mp]))}}]
    [c/bar-text (str @(subscribe [::subs/mp]) "/100")]]])

(defn- renderer-info []
  (when-let [info @(subscribe [::subs/renderer-info])]
    [c/renderer-info
     [:span (str "Calls: " (:calls info))]
     [:span (str "Triangles: " (:triangles info))]
     [:span (str "Frame: " (:frame info))]
     [:span (str "Points: " (:points info))]
     [:span (str "Lines: " (:lines info))]]))

(defn- selected-enemy []
  (when-let [{:keys [name health]} @(subscribe [::subs/selected-character])]
    [c/bar-container
     {:variant :enemy-health}
     [c/bar {:variant :enemy-health
             :style {:width (* 3 health)}}]
     [c/bar-text name]]))

(defn- skill-bar []
  (let [show-skill-info? (r/atom false)
        skill-info-text (r/atom nil)]
    (fn []
      [c/skill-bar
       [skill-info show-skill-info? skill-info-text]
       (doall
         (for [{:keys [skill src duration index loading?]} @(subscribe [::subs/skills])]
           ^{:key index}
           [c/skill-box
            {:css {:grid-column index}
             :on-mouse-over #(do
                               (reset! skill-info-text (-> skill common.char/skills :info))
                               (reset! show-skill-info? true))
             :on-mouse-out #(reset! show-skill-info? false)
             :on-click #(fire :event-key-down (str "Digit" index))}
            [c/skill-img {:src src
                          :variant (when (->> common.char/skills skill :required-mana (< @(subscribe [::subs/mp])))
                                     :not-enough-mana)}]
            (when loading?
              [c/use-skill duration])
            [c/slot-text index]]))])))

(defn- hits-by-enemies []
  [c/right-panel
   (doall
     (for [[idx skill] (map-indexed vector @(subscribe [::subs/damages]))]
       ^{:key idx}
       [c/skill-img
        {:variant :got-hit
         :class c/skill-hit
         :src (-> skill common.char/skills :src)}]))])

(defn- position-and-ping-info []
  [:<>
   (when-let [[x _ z] @(subscribe [::subs/position])]
     [c/position
      [c/position-text (str "(" (int x) ", " (int z) ")")]])
   [c/position {:variant :ping}
    [c/position-text (str "Ping: " @(subscribe [::subs/ping]))]]])

(defn- info-box []
  [c/info-box*
   [c/info-box-container
    (for [[i m] (map-indexed vector @(subscribe [::subs/messages]))]
      ^{:key i}
      [:span
       (when (even? i) {:style {:color "yellow"}})
       m])]])

(defn- create-scoreboard-and-chat-events []
  (.addEventListener js/document "keydown" (fn [e]
                                             (cond
                                               (= "Tab" (.-code e))

                                               (do
                                                 (.preventDefault e)
                                                 (dispatch [::events/set-score-board-visibility :visible]))

                                               (= "Enter" (.-code e))
                                               (do
                                                 (.preventDefault e)
                                                 (dispatch-sync [::events/toggle-chat])
                                                 (fire :chat-enabled? @(subscribe [::subs/chat-enabled?]))
                                                 (dispatch [::events/send-chat-message])))))
  (.addEventListener js/document "keyup" (fn [e]
                                           (when (= "Tab" (.-code e))
                                             (.preventDefault e)
                                             (dispatch [::events/set-score-board-visibility :hidden])))))

(defn- score-board-modal []
  (when @(subscribe [::subs/show-score-board?])
    [c/modal-container
     {:variant :scoreboard}
     [c/modal-content
      {:variant :scoreboard}
      [c/modal-wrapper
       {:variant :scoreboard}
       [c/scoreboard
        [:tbody
         [:tr
          [:th "Username"]
          [:th "Kills"]
          [:th "Deaths"]]]
        (doall
          (for [data @(subscribe [::subs/scoreboard])]
            ^{:key (:username data)}
            [:tr
             (when (= (:username data) @(subscribe [::subs/username]))
               {:style {:background-color "#0000008a"}})
             [:th (:username data)]
             [:th (:kills data)]
             [:th (:deaths data)]]))]]]]))

(defn- killed-by-info []
  [c/right-panel
   {:variant :killed-by-info}
   (for [[idx info] (map-indexed vector @(subscribe [::subs/killed-by-messages]))]
     ^{:key idx}
     [:<>
      [c/killed-by-text
       [:b (:killer info)] " killed " (:slaughtered info)]
      [:br]])])

(defn- chat-messages []
  (let [ref (atom nil)]
    (r/create-class
      {:component-did-update (fn []
                               (when-let [elem @ref]
                                 (j/assoc! elem :scrollTop (j/get elem :scrollHeight))))
       :reagent-render (fn []
                         [c/chat-box
                          {:ref #(reset! ref %)
                           :style {:width "100%"
                                   :height 120
                                   :line-height "1.65em"}}
                          (for [[idx data] (map-indexed vector @(subscribe [::subs/chat]))]
                            ^{:key idx}
                            [:<>
                             [:span
                              {:style {:background-color "#10131dcc"}}
                              [:b (str (:sender data) ": ") (:chat-message data)]]
                             [:br]])])})))

(defn- chat-box []
  [c/info-box*
   {:variant :chat
    :style {:opacity (if @(subscribe [::subs/chat-enabled?]) 0.65 0.5)
            :pointer-events (if @(subscribe [::subs/chat-enabled?]) :all :none)}}
   [c/info-box-container
    {:variant :chat}
    [chat-messages]
    (when @(subscribe [::subs/chat-enabled?])
      [:div
       {:style {:margin-left -3}}
       [c/chat-input
        {:ref #(some-> % .focus)
         :on-change #(dispatch [::events/set-chat-message (-> % .-target .-value)])
         :max-length 40}]])]])

(defn- scene []
  (r/create-class
    {:component-did-mount (fn []
                            (create-scoreboard-and-chat-events)
                            (on :ws-conn-lost? #(dispatch [::events/show-connection-lost-modal]))
                            (on :skill-failed #(dispatch [::events/add-msg "Skill failed"]))
                            (on :enemy-too-far #(dispatch [::events/add-msg "Enemy too far"]))
                            (on :not-enough-mana #(dispatch [::events/add-msg "Not enough mana!"]))
                            (on :not-enough-health-for-tp #(dispatch [::events/add-msg
                                                                      "For teleport you need at least 35 HP"]))
                            (on :not-idle-state-for-tp #(dispatch [::events/add-msg
                                                                   "You should not move while using teleport"]))
                            (on :selected-character #(dispatch [::events/set-selected-character %]))
                            (on :skill-used (fn [skill]
                                              (dispatch [::events/add-loading-skill skill])
                                              (add-used-skill-to-msg-box skill)))
                            (on :char-position #(dispatch [::events/set-position %]))
                            (on :enemy-damage (fn [data]
                                                (dispatch [::events/add-msg
                                                           (str (:enemy data) " got " (:enemy-damage data) " damage")])
                                                (dispatch [::events/set-mp (:mana data)])))
                            (on :got-damage (fn [data]
                                              (let [health (:health data)
                                                    damage (:got-damage data)
                                                    skill (:skill data)
                                                    from (:from data)]
                                                (dispatch [::events/add-msg (str "You got " damage " damage from " from)])
                                                (dispatch [::events/set-hp health])
                                                (when (common.char/magic-skills skill)
                                                  (dispatch [::events/add-damage skill])))))
                            (on :potion (fn [data]
                                          (when-let [health (:health data)]
                                            (dispatch [::events/set-hp health]))
                                          (when-let [mana (:mana data)]
                                            (dispatch [::events/set-mp mana]))))
                            (on :respawn-successful #(dispatch [::events/respawn]))
                            (on :killed-by-info #(dispatch [::events/add-killed-by %]))
                            (on :add-message-to-chat #(dispatch [::events/add-msg-to-chat %]))
                            (on :world #(dispatch-sync [::events/set-world %]))
                            (when config/dev?
                              (on :renderer-info #(dispatch [::events/set-renderer-info %]))))
     :reagent-render (fn []
                       [:<>
                        [score-board-modal]
                        [respawn-modal]
                        [hp-and-mana]
                        [renderer-info]
                        [selected-enemy]
                        [skill-bar]
                        [hits-by-enemies]
                        [killed-by-info]
                        [position-and-ping-info]
                        [info-box]
                        [chat-box]
                        [connection-lost-modal]])}))

(defn main-panel []
  (r/create-class
    {:component-will-mount #(c/global-styles)
     :component-did-mount (fn [] (on :all-ready #(dispatch [::events/close-login-modal])))
     :reagent-render (fn []
                       [c/ui-panel
                        (if (and (not @(subscribe [::subs/login-done?]))
                                 config/multiplayer?)
                          [login-modal]
                          [scene])])}))

(comment
  )
