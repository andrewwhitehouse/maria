(ns maria.editors.prose
  (:require [re-view.core :as v :refer [defview]]
            [re-view-prosemirror.commands :as commands]
            [re-view-prosemirror.commands :refer [apply-command]]
            [maria.views.floating.float-ui :as hint]
            [maria.views.icons :as icons]
            [re-view-prosemirror.core :as pm]
            [re-view-prosemirror.markdown :as markdown]
            [goog.object :as gobj]
            [goog.dom.classes :as classes]
            [maria.blocks.blocks :as Block]
            [commands.exec :as exec]
            [maria.commands.prose :as prose-commands]
            [re-view-routing.core :as r]
            [maria.editors.editor :as Editor]))


(defview link-dropdown [{:keys [href editor]}]
  [:.flex.items-center.pa2.bg-white.br2.shadow-4
   [:.dib.pointer
    {:on-click (fn [e]
                 (.preventDefault e)
                 (.stopPropagation e)
                 (commands/apply-command editor (partial commands/open-link true))
                 (hint/clear-hint!))}
    (-> icons/ModeEdit
        (icons/size 16)
        (icons/class "mr1 o-50"))]
   [:a.pm-link {:href href} href]])

(defn make-editor-state [doc input-rules]
  (.create pm/EditorState
           #js {"doc"     doc
                "schema"  markdown/schema
                "plugins" (to-array [#_(.history pm/history)
                                     #_(pm/keymap pm/keymap-base)
                                     (.inputRules pm/pm
                                                  #js {:rules (->> (.-allInputRules pm/pm)
                                                                   (into input-rules)
                                                                   (to-array))})])}))

(defn make-editor-view [{:keys [before-change editor-props on-dispatch on-selection-activity view/state] :as component}
                        editor-state]
  (pm/EditorView. (v/dom-node component) (->> (merge editor-props
                                                     {:state                   editor-state
                                                      :spellcheck              false
                                                      :attributes              {:class "outline-0 cf"}
                                                      :handleScrollToSelection (fn [view] true)
                                                      :dispatchTransaction     (fn [tr]
                                                                                 (let [^js/pm.EditorView pm-view (get @state :pm-view)
                                                                                       prev-state (.-state pm-view)]
                                                                                   (when before-change (before-change))
                                                                                   (pm/transact! pm-view tr)
                                                                                   (when-not (nil? on-dispatch)
                                                                                     (on-dispatch pm-view prev-state))
                                                                                   (when (and on-selection-activity
                                                                                              (not= (.-selection (.-state pm-view))
                                                                                                    (.-selection prev-state)))
                                                                                     (on-selection-activity pm-view (.-selection (.-state pm-view))))))})
                                              (clj->js))))

(defview ProseEditor
  {:spec/props              {:on-dispatch :Function
                             :input-rules :Object
                             :doc         :Object}
   :view/did-mount          (fn [{:keys [view/state
                                         input-rules
                                         doc] :as this}]
                              (let [editor-view (make-editor-view this (make-editor-state doc input-rules))]
                                (set! (.-reView editor-view) this)
                                (reset! state {:pm-view editor-view})))

   :reset-doc               (fn [{:keys [view/state parse]} doc]
                              (let [view (:pm-view @state)]
                                (.updateState view
                                              (.create pm/EditorState #js {"doc"     doc
                                                                           "schema"  markdown/schema
                                                                           "plugins" (gobj/getValueByKeys view "state" "plugins")}))))

   :view/will-receive-props (fn [{:keys [doc block view/state]
                                  :as   this}]
                              (when (not= doc (.-doc (.-state (:pm-view @state))))
                                (.resetDoc this doc)))

   :pm-view                 #(:pm-view @(:view/state %))

   :view/will-unmount       (fn [{:keys [view/state]}]
                              (pm/destroy! (:pm-view @state)))}
  [this]
  [:.prosemirror-content
   (-> (v/pass-props this)
       (assoc :dangerouslySetInnerHTML {:__html ""}))])
(comment
  (defn Toolbar
    [pm-view]
    (let [[state dispatch] [(.-state pm-view) (.-dispatch pm-view)]]
      (when-not (.. state -selection -empty)
        [:.bg-darken-ligxhtly.h-100
         (->> [toolbar/mark-strong
               toolbar/mark-em
               toolbar/mark-code
               #_toolbar/tab-outdent
               #_toolbar/tab-indent
               #_toolbar/block-code
               #_toolbar/list-bullet
               #_toolbar/list-ordered
               #_toolbar/wrap-quote
               ]

              (map (fn [menu-item] (menu-item state dispatch))))]))))

(defview ProseRow
  {:key               :id
   :get-editor        #(.pmView (:prose-editor-view @(:view/state %)))
   :view/did-mount    Editor/mount
   :view/will-unmount (fn [this]
                        (Editor/unmount this)
                        ;; prosemirror doesn't fire `blur` command when unmounted
                        (when (= (:block-view @exec/context) this)
                          (exec/set-context! {:block/prose nil
                                              :block-view  nil})))}
  [{:keys [view/state block-list block before-change] :as this}]
  [:div
   (ProseEditor {:doc           (Block/state block)
                 :editor-props  {:onFocus #(exec/set-context! {:block/prose true
                                                               :block-view  this})
                                 :onBlur  #(exec/set-context! {:block/prose nil
                                                               :block-view  nil})}
                 :block         block
                 :class         " serif f4 ph3 cb"
                 :input-rules   (prose-commands/input-rules this)

                 :on-click      (fn [e]
                                  (when-let [a (r/closest (.-target e) r/link?)]
                                    (when-not (classes/has a "pm-link")
                                      (do (.stopPropagation e)
                                          (hint/floating-hint! {:rect      (.getBoundingClientRect a)
                                                                :component link-dropdown
                                                                :props     {:href   (.-href a)
                                                                            :editor (.getEditor this)}})))))
                 :ref           #(v/swap-silently! state assoc :prose-editor-view %)
                 :before-change before-change
                 :on-dispatch   (fn [pm-view prev-state]
                                  #_(when (not= (.-selection (.-state pm-view))
                                              (.-selection prev-state))
                                    (bottom-bar/set-bottom-bar! (Toolbar pm-view)))
                                  (when (not= (.-doc (.-state pm-view))
                                              (.-doc prev-state))
                                    (.splice block-list block [(assoc block :doc (.-doc (.-state pm-view)))])))})])

(specify! (.-prototype js/pm.EditorView)

  Editor/IKind
  (kind [this] :prose)

  Editor/IHistory

  (get-selections [this]
    (.. this -state -selection))

  (put-selections! [this selections]
    (commands/apply-command this
                            (fn [state dispatch]
                              (dispatch (.setSelection (.-tr state) selections)))))

  Editor/ICursor

  (cursor-coords [this]
    (pm/cursor-coords this))

  (start [this] (pm/start-$pos (.-state this)))
  (end [this] (pm/end-$pos (.-state this)))
  (get-cursor [this] (pm/cursor-$pos (.-state this)))

  (-focus! [this coords]
    (v/flush!)
    (let [state (.-state this)
          doc (.-doc state)]
      (.focus this)
      (when-let [selection (cond (keyword? coords)
                                 (case coords :start (.atStart pm/Selection doc)
                                              :end (.atEnd pm/Selection doc))

                                 (object? coords)
                                 (pm/coords-selection this coords)
                                 :else nil)]
        (.dispatch this (-> (.-tr state)
                            (.setSelection selection)))
        (Editor/scroll-into-view (Editor/cursor-coords this))))))