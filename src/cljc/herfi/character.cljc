(ns herfi.character)

(def enemy-visibility-distance 100)

(def skills
  {:attack-flame {:index 1
                  :skill :attack-flame
                  :src "img/flame.png"
                  :required-mana 25
                  :duration 6
                  :info "Flame attack causes the most damage..."}
   :attack-ice {:index 2
                :skill :attack-ice
                :src "img/ice2.png"
                :required-mana 30
                :duration 7
                :info "Ice attack might slow the enemy down - by %30 chance"}
   :attack-light {:index 3
                  :skill :attack-light
                  :src "img/el.png"
                  :required-mana 35
                  :duration 8
                  :info "Light attack might freeze the enemy - by %25 chance"}
   :punch {:index 4
           :skill :punch
           :src "img/punch.png"
           :required-mana 10
           :duration 1
           :info "When you're near the enemy you can punch him"}
   :shield {:index 5
            :skill :shield
            :src "img/shield.png"
            :required-mana 20
            :duration (* 60 5)
            :info "Your defence will increase by %30 for 3 minutes"}
   :teleport {:index 6
              :skill :teleport
              :src "img/tt.jpeg"
              :required-mana 20
              :duration (* 60 5)
              :info "Respawns to random location in the map"}
   :hp-potion {:index 7
               :skill :hp-potion
               :src "img/poth.jpeg"
               :duration 1
               :info "Loads 10 health points"}
   :mp-potion {:index 8
               :skill :mp-potion
               :src "img/potm.jpeg"
               :duration 1
               :info "Loads 10 mana points"}})

(def magic-skills #{:attack-flame :attack-ice :attack-light})
