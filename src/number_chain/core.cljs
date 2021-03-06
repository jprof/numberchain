(ns number-chain.core
  (:require [reagent.core :as reagent :refer [atom]]
            [number-chain.numbers :refer [generate-numbers generate-target wrap-numbers]]
            [number-chain.timer :refer [start-timer! timer-component count-down timer-state stop-timer! pause-timer!]]
            [number-chain.score :refer [score high-score save-high-score! load-high-score score-component]]))

(declare init! game-over! attach-touch-listeners! set-numbers-and-target! remove-touch-listeners!)

;; Fuschia, Purple, Green, Red, Cyan
(def selection-colors (atom (cycle ["#d800a8" "#7400b7" "#62d100" "#ff0047" "#00e1e9"])))

;; For now, this is our initial game state. On init! we will reset our app-state atom back to this.
(def initial-game-state {:app-name "Number Chain"
                         :numbers {}
                         :target nil
                         :selected #{}
                         :selection-color "#000000"
                         :play-state :new-game})

(def app-state (atom initial-game-state))

(defn get-element-by-id [id]
  (.getElementById js/document id))

(defn get-elements-by-class-name [cn]
  (.getElementsByClassName js/document cn))

(defn panel-cell [contents attrs]
  (let [default-attrs {:style {:border "1px solid #d3d3d3" :background "#009b77"}}]
    [div-grid-col-panel (if attrs (merge default-attrs attrs) default-attrs)
     contents])
  )
(defn game-panel []
  [
    (panel-cell (if (= :active (:play-state @app-state)) @score " "))
    (panel-cell (if (= :active (:play-state @app-state)) (:target @app-state) " "))
    (panel-cell (if (= :active (:play-state @app-state)) @count-down " "))
    (panel-cell "P" {:on-click toggle-pause-game!})
    ]
  )

(defn my-app
  "For now, this will display the current fields in our game state.
   Nice for debugging/visualizing the app-state."
  []
  [:div
   [:div (str (:app-name @app-state))]
   ])

(defn start-game! []
  ;(reset! app-state initial-game-state)
  (set-numbers-and-target!)
  (reset! count-down 10)
  (swap! app-state assoc :play-state :active :selected #{} :selection-color (first @selection-colors))
  (swap! selection-colors rest)
  (start-timer!)
  (js/setTimeout #(attach-touch-listeners!) 50)) ; This is bad. I don't know what to do about it though

(defn game-over! []
  (reset! score 0)
  (swap! app-state assoc :play-state :game-over))

(defn toggle-pause-game! []
  (let [play-state (:play-state @app-state)]
    (condp = play-state
      :active (do (pause-timer!) (swap! app-state assoc :play-state :paused))
      :paused (do (start-timer!) (swap! app-state assoc :play-state :active) (js/setTimeout #(attach-touch-listeners!) 50))
      nil
      )))

;; We need the symbols defined in this ns to be called when the countdown runs out.
(swap! timer-state assoc :time-up-fn game-over!)


(defn target-component
  []
  [:div.target (str "Target: " (:target @app-state))])

(defn check-win
  "Check our win conditition. Sum up the currently selected cells in our grid
   and compare to the :target in our app-state. Currently it also resets the
   game by calling init!"
  []
  (let [selected-nums (map
                        #(:value %)
                        (filter
                          #((:selected @app-state) (% :id))
                          (:numbers @app-state)))
        sum (apply + selected-nums)]
    (when (= sum (:target @app-state))
      (remove-touch-listeners!)
      (reset! score (+ @score (* (count selected-nums) 10)))
      (when (> @score @high-score)
        (reset! high-score @score)
        (save-high-score! @score))
      (stop-timer!)
      (js/setTimeout #(swap! app-state assoc :play-state :next-level) 500))))

(defn toggle-selected
  "Called when a click is recieved on one of our game cells. Adds or removes
   the numbers :id to the :selected app-state set, then checks the win
   condition."
  [e]
  (let [target (-> e (.-currentTarget) (.getAttribute "data-id") int)]
    (if (get (:selected @app-state) target)
      (swap! app-state assoc :selected (disj (:selected @app-state) target))
      (swap! app-state assoc :selected (conj (:selected @app-state) target)))
    (check-win)))

(def div-grid-col :div.grid-cell)
(def div-grid-col-panel :div.grid-cell-panel)
(def div-grid-container :div#game-grid)
(def div-grid-row :div.grid-row)

(defn small-cell
  "Takes a single value from the :numbers app-state value and produces the cell
   that represents that number. Properly sets the bg color and data-id tag
   fields."
  [{:keys [value id]}]
  [div-grid-col {:style {:border "1px solid #d3d3d3"
                         :background (if ((:selected @app-state) id)
                                       (:selection-color @app-state)
                                       "#009bcc")}
                 :data-id id
                 :id (str "grid-" id)
                 :on-click toggle-selected}
   value])

(defn empty-cell
  []
  [div-grid-col {:style {:border "1px solid #00baf5"
                         :background "#009bcc"}}
   \X])


(defn empty-grid
  "Create an grid of empty squares and an overlay to render into."
  [contents]
  (into (into [div-grid-container [:div#overlay contents]]
        (for [x (range 0 20)]
          (small-cell " ")))
        (game-panel)))

(defn pause-button-component
  []
  (let [play-state (:play-state @app-state)
        btn-txt (condp = play-state
                  :paused "Unpause"
                  :active "Pause"
                  "")]
    (if (not= btn-txt "")
      [:button {:on-click toggle-pause-game!} btn-txt])))

(defn pause-component
  []
  (empty-grid [:div.filler-container "Paused!"]))

(defn start-game-component
  []
  (empty-grid [:div.filler-container "Click numbers that sum up to the target number. The more you select, the higher your score " [:p [:button {:on-click start-game!} "Start game?"]]]))

(defn next-level-component
  []
  (empty-grid [:div.filler-container [:button {:on-click start-game!} "You won! Next Level!"]]))

(defn game-over-component
  []
  (empty-grid [:div.filler-container [:button {:on-click start-game!} "Game Over! Retry?"]]))


; We could clean this up, but it should work for now.
(defn build-game
  "Reagent component that contains our game grid."
  []
  (let [numbers (:numbers @app-state)]
    (into
      (into [div-grid-container]
            (for [x (range 0 20)]
              (small-cell (nth numbers x))))
      (game-panel))))

(defn game-component
  []
  (condp = (:play-state @app-state)
    :new-game (start-game-component)
    :next-level (next-level-component)
    :active (build-game)
    :game-over (game-over-component)
    :paused (pause-component)
    :else (empty-grid)))

(defn set-numbers-and-target!
  "Set the state of the numbers and the sum target -- call this one"
  []
  (let [num-list (generate-numbers)
        target   (generate-target num-list)]
    (swap! app-state assoc :numbers (wrap-numbers (shuffle num-list)) :target target)))

(defn attach-touch-listeners!
  "Add the touch listeners to all of the grid-cell divs in our game. Needs
   to happen after each rendering of the game grid."
  []
  (let [els (get-elements-by-class-name "grid-cell")
        size (.-length els)]
    (doseq [e (range size)]
      (.addEventListener (aget els e) "touchstart" toggle-selected))))

(defn remove-touch-listeners!
  "Remove the touch listeners to all of the grid-cell divs in our game. Needs
   to happen after each rendering of the game grid."
  []
  (let [els (get-elements-by-class-name "grid-cell")
        size (.-length els)]
    (doseq [e (range size)]
      (.removeEventListener (aget els e) "touchstart" toggle-selected))))


(defn init! []
  (reagent/render-component [my-app] (get-element-by-id "app-name"))
  (reagent/render-component [game-component] (get-element-by-id "game"))
  )

(init!)

(comment
  (-> (generate-numbers)
      (generate-target))
  (set-numbers-and-target!)
  (js/alert "Hello Justin!"))


