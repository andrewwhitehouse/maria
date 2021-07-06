;; # Let's make some music!

;; This one relies on an external lib, so you need to load that one first, or the rest will not work.

(load-gist "c7d5ab0dad5d4343561cff39b8d5d6c1")

(defonce context (audio-context))

(defn midi->freq [n]
  (* 440 (js/Math.pow 2 (/ (- n 69) 12))))

(defn ping [freq]
  (connect->
    (square freq)         ; Try a sawtooth wave.
    (percussive 0.01 0.4) ; Try varying the attack and decay.
    (gain 0.1)))

(defn do-ping [note]
  (-> (ping (midi->freq note))
      (connect-> destination)
      (run-with context (current-time context) 1.0)))

;; Color piano!

(html [:div.flex (for [i [60 62 64 65 67 69 71 72] ]
                   [:div {:on-click #(do-ping i)}
                    (shapes.core/colorize (shapes.core/hsl (- 300 i) i i)
                                          (shapes.core/circle 20))])])
