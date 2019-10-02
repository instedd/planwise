;; Component based on re-com's single-dropdown from
;; https://github.com/Day8/re-com/blob/a7602cc18d1f2babd09e9c6bacd3ef0645344b32/src/re_com/dropdown.cljs
;; Originally distributed under the MIT license
;; Copyright (c) 2015 Michael Thompson

(ns planwise.client.ui.filter-select
  (:require-macros [re-com.core :refer [handler-fn]])
  (:require [re-com.util     :refer [deref-or-value position-for-id item-for-id]]
            [re-com.validate :refer [vector-of-maps? css-style? html-attr? number-or-string?] :refer-macros [validate-args-macro]]
            [clojure.string  :as    string]
            [reagent.core    :as    reagent]))

;;  Inspiration: http://alxlit.name/bootstrap-chosen
;;  Alternative: http://silviomoreto.github.io/bootstrap-select

(defn- move-to-new-choice
  "In a vector of maps (where each map has an :id), return the id of the choice offset posititions away
   from id (usually +1 or -1 to go to next/previous). Also accepts :start and :end"
  [choices id-fn id offset]
  (let [current-index (position-for-id id choices :id-fn id-fn)
        new-index     (cond
                        (= offset :start)    0
                        (= offset :end)      (dec (count choices))
                        (nil? current-index) 0
                        :else                (mod (+ current-index offset) (count choices)))]
    (when (and new-index (pos? (count choices)))
      (id-fn (nth choices new-index)))))


(defn- choices-with-group-headings
  "If necessary, inserts group headings entries into the choices"
  [opts group-fn]
  (let [groups         (partition-by group-fn opts)
        group-headers  (->> groups
                            (map first)
                            (map group-fn)
                            (map #(hash-map :id (gensym) :group %)))]
    [group-headers groups]))


(defn- filter-choices
  "Filter a list of choices based on a filter string using plain string searches (case insensitive). Less powerful
   than regex's but no confusion with reserved characters"
  [choices group-fn label-fn filter-text]
  (let [lower-filter-text (string/lower-case filter-text)
        filter-fn         (fn [opt]
                            (let [group (if (nil? (group-fn opt)) "" (group-fn opt))
                                  label (str (label-fn opt))]
                              (or
                               (>= (.indexOf (string/lower-case group) lower-filter-text) 0)
                               (>= (.indexOf (string/lower-case label) lower-filter-text) 0))))]
    (filter filter-fn choices)))


(defn- filter-choices-regex
  "Filter a list of choices based on a filter string using regex's (case insensitive). More powerful but can cause
   confusion for users entering reserved characters such as [ ] * + . ( ) etc."
  [choices group-fn label-fn filter-text]
  (let [re        (try
                    (js/RegExp. filter-text "i")
                    (catch js/Object e nil))
        filter-fn (partial (fn [re opt]
                             (when-not (nil? re)
                               (or (.test re (group-fn opt)) (.test re (label-fn opt)))))
                           re)]
    (filter filter-fn choices)))


(defn filter-choices-by-keyword
  "Filter a list of choices extra data within the choices vector"
  [choices keyword value]
  (let [filter-fn (fn [opt] (>= (.indexOf (keyword opt) value) 0))]
    (filter filter-fn choices)))


(defn show-selected-item
  [node]
  (let [item-offset-top       (.-offsetTop node)
        item-offset-bottom    (+ item-offset-top (.-clientHeight node))
        parent                (.-parentNode node)
        parent-height         (.-clientHeight parent)
        parent-visible-top    (.-scrollTop parent)
        parent-visible-bottom (+ parent-visible-top parent-height)
        new-scroll-top        (cond
                                (> item-offset-bottom parent-visible-bottom) (max (- item-offset-bottom parent-height) 0)
                                (< item-offset-top parent-visible-top)       item-offset-top)]
    (when new-scroll-top (set! (.-scrollTop parent) new-scroll-top))))


(defn- make-group-heading
  "Render a group heading"
  [m]
  ^{:key (:id m)} [:li.group-result
                   (:group m)])


(defn- choice-item
  "Render a choice item and set up appropriate mouse events"
  [id label on-click internal-model]
  (let [mouse-over? (reagent/atom false)]
    (reagent/create-class
     {:component-did-mount
      (fn [this]
        (let [node (reagent/dom-node this)
              selected (= @internal-model id)]
          (when selected (show-selected-item node))))

      :component-did-update
      (fn [this]
        (let [node (reagent/dom-node this)
              selected (= @internal-model id)]
          (when selected (show-selected-item node))))

      :display-name "choice-item"

      :reagent-render
      (fn
        [id label on-click internal-model]
        (let [selected (= @internal-model id)]
          [:li.mdc-list-item
           (merge {:class         (when selected "mdc-list-item--selected")
                   :tab-index     -1
                   :on-mouse-down (handler-fn (on-click id))}
                  (when selected {:aria-selected true}))
           label]))})))


(defn make-choice-item
  [id-fn render-fn callback internal-model opt]
  (let [id (id-fn opt)
        markup (render-fn opt)]
    ^{:key (str id)} [choice-item id markup callback internal-model]))


(defn- filter-text-box-base
  "Base function (before lifecycle metadata) to render a filter text box"
  [filter-text key-handler drop-showing? set-filter-text]
  [:div.mdc-text-field.mdc-text-field--outlined
   {:style {:width "100%"}}
   [:input.mdc-text-field__input
    {:type          "text"
     :auto-complete "off"
     :placeholder   "Filter choices..."
     :value         @filter-text
     :on-change     (handler-fn (set-filter-text (-> event .-target .-value)))
     :on-key-down   (handler-fn (when-not (key-handler event)
                                  (.preventDefault event))) ;; When key-handler returns false, preventDefault
     :on-blur       (handler-fn (reset! drop-showing? false))}]
   [:div.mdc-text-field__outline
    [:svg
     [:path.mdc-test-field__outline-path]]]
   [:div.mdc-text-field__idle-outline]])


(def ^:private filter-text-box
  "Render a filter text box"
  (with-meta filter-text-box-base
    {:component-did-mount #(let [node (.-firstChild (reagent/dom-node %))]
                             (.focus node))
     :component-did-update #(let [node (.-firstChild (reagent/dom-node %))]
                              (.focus node))}))

(defn- dropdown-top
  "Render the top part of the dropdown, with the clickable area and the up/down arrow"
  []
  (let [ignore-click (atom false)]
    (fn
      [internal-model choices id-fn label-fn tab-index label dropdown-click key-handler drop-showing? title?]
      (let [text (if @internal-model
                   (label-fn (item-for-id @internal-model choices :id-fn id-fn)))
            float-label? (some? text)]
        [:div.mdc-select__surface
         {:tab-index     (or tab-index 0)
          :on-click      (handler-fn
                          (if @ignore-click
                            (reset! ignore-click false)
                            (dropdown-click)))
          :on-mouse-down (handler-fn
                          (when @drop-showing?
                            (reset! ignore-click true)))   ;; TODO: Hmmm, have a look at calling preventDefault (and stopProp?) and removing the ignore-click stuff
          :on-key-down   (handler-fn
                          (key-handler event)
                          (when (= (.-which event) 13)     ;; Pressing enter on an anchor also triggers click event, which we don't want
                            (reset! ignore-click true)))}  ;; TODO: Hmmm, have a look at calling preventDefault (and stopProp?) and removing the ignore-click stuff
         [:div {:class (str "mdc-select__label " (when float-label? "mdc-select__label--float-above"))}
          label]
         [:div.mdc-select__selected-text (when title? {:title text})
          text]
         [:div.mdc-select__bottom-line]]))))

(defn- fn-or-vector-of-maps? ;; Would normally move this to re-com.validate but this is very specific to this component
  [v]
  (or (fn? v)
      (vector-of-maps? v)))

(defn- load-choices*
  "Load choices if choices is callback."
  [choices-state choices text regex-filter?]
  (let [id (inc (:id @choices-state))
        callback (fn [{:keys [result error] :as args}]
                   (when (= id (:id @choices-state))
                     (swap! choices-state assoc
                            :loading? false
                            :error error
                            :choices result)))]
    (swap! choices-state assoc
           :loading? true
           :error nil
           :id id
           :timer nil)
    (choices {:filter-text   text
              :regex-filter? regex-filter?}
             #(callback {:result %})
             #(callback {:error %}))))

(defn- load-choices
  "Load choices or schedule lodaing depending on debounce?"
  [choices-state choices debounce-delay text regex-filter? debounce?]
  (when (fn? choices)
    (when-let [timer (:timer @choices-state)]
      (js/clearTimeout timer))
    (if debounce?
      (let [timer (js/setTimeout #(load-choices* choices-state choices text regex-filter?) debounce-delay)]
        (swap! choices-state assoc :timer timer))
      (load-choices* choices-state choices text regex-filter?))))

;;--------------------------------------------------------------------------------------------------
;; Component: single-dropdown
;;--------------------------------------------------------------------------------------------------

(def single-dropdown-args-desc
  [{:name :choices       :required true                   :type "vector of choices | atom | (opts, done, fail) -> nil" :validate-fn fn-or-vector-of-maps? :description [:span "Each is expected to have an id, label and, optionally, a group, provided by " [:code ":id-fn"] ", " [:code ":label-fn"] " & " [:code ":group-fn"] ". May also be a callback " [:code "(opts, done, fail)"] " where opts is map of " [:code ":filter-text"] " and " [:code ":regex-filter?."]]}
   {:name :model         :required true                   :type "the id of a choice | atom"                                    :description [:span "the id of the selected choice. If nil, " [:code ":placeholder"] " text is shown"]}
   {:name :on-change     :required true                   :type "id -> nil"                     :validate-fn fn?               :description [:span "called when a new choice is selected. Passed the id of new choice"]}
   {:name :id-fn         :required false :default :id     :type "choice -> anything"            :validate-fn ifn?              :description [:span "given an element of " [:code ":choices"] ", returns its unique identifier (aka id)"]}
   {:name :label-fn      :required false :default :label  :type "choice -> string"              :validate-fn ifn?              :description [:span "given an element of " [:code ":choices"] ", returns its displayable label."]}
   {:name :group-fn      :required false :default :group  :type "choice -> anything"            :validate-fn ifn?              :description [:span "given an element of " [:code ":choices"] ", returns its group identifier"]}
   {:name :render-fn     :required false                  :type "choice -> string | hiccup"     :validate-fn ifn?              :description [:span "given an element of " [:code ":choices"] ", returns the markup that will be rendered for that choice. Defaults to the label if no custom markup is required."]}
   {:name :disabled?     :required false :default false   :type "boolean | atom"                                               :description "if true, no user selection is allowed"}
   {:name :regex-filter? :required false :default false   :type "boolean | atom"                                               :description "if true, the filter text field will support JavaScript regular expressions. If false, just plain text"}
   {:name :label         :required false                  :type "string"                        :validate-fn string?           :description "label for the select field"}
   {:name :title?        :required false :default false   :type "boolean"                                                      :description "if true, allows the title for the selected dropdown to be displayed via a mouse over. Handy when dropdown width is small and text is truncated"}
   {:name :tab-index     :required false                  :type "integer | string"              :validate-fn number-or-string? :description "component's tabindex. A value of -1 removes from order"}
   {:name :debounce-delay :required false                 :type "integer"                       :validate-fn number?           :description [:span "delay to debounce loading requests when using callback " [:code ":choices"]]}
   {:name :class         :required false                  :type "string"                        :validate-fn string?           :description "CSS class names, space separated (applies to the outer container)"}
   {:name :style         :required false                  :type "CSS style map"                 :validate-fn css-style?        :description "CSS styles to add or override (applies to the outer container)"}
   {:name :attr          :required false                  :type "HTML attr map"                 :validate-fn html-attr?        :description [:span "HTML attributes, like " [:code ":on-mouse-move"] [:br] "No " [:code ":class"] " or " [:code ":style"] "allowed (applies to the outer container)"]}])

(defn single-dropdown
  "Render a single dropdown component which emulates the bootstrap-choosen style. Sample choices object:
     [{:id \"AU\" :label \"Australia\"      :group \"Group 1\"}
      {:id \"US\" :label \"United States\"  :group \"Group 1\"}
      {:id \"GB\" :label \"United Kingdom\" :group \"Group 1\"}
      {:id \"AF\" :label \"Afghanistan\"    :group \"Group 2\"}]"
  [& {:keys [choices model regex-filter? debounce-delay]
      :or {debounce-delay 250}
      :as args}]
  {:pre [(validate-args-macro single-dropdown-args-desc args "single-dropdown")]}
  (let [external-model (reagent/atom (deref-or-value model))  ;; Holds the last known external value of model, to detect external model changes
        internal-model (reagent/atom @external-model)         ;; Create a new atom from the model to be used internally
        drop-showing?  (reagent/atom false)
        filter-text    (reagent/atom "")
        choices-fn?    (fn? choices)
        choices-state  (reagent/atom {:loading? choices-fn?
                                        ; loading error
                                      :error nil
                                      :choices []
                                        ; request id to ignore handling response when new request was already made
                                      :id 0
                                        ; to debounce requests
                                      :timer nil})
        load-choices    (partial load-choices choices-state choices debounce-delay)
        set-filter-text (fn [text {:keys [regex-filter?] :as args} debounce?]
                          (load-choices text regex-filter? debounce?)
                          (reset! filter-text text))
        position         (reagent/atom {:top 0 :left 0 :width 200})
        update-position-fn (fn [this]
                             (let [node (reagent/dom-node this)]
                               (reset! position {:top (.-offsetTop node)
                                                 :left (.-offsetLeft node)
                                                 :width (.-offsetWidth node)})))]
    (load-choices "" regex-filter? false)
    (reagent/create-class
     {:component-did-mount update-position-fn
      :reagent-render
      (fn [& {:keys [choices model on-change id-fn label-fn group-fn render-fn disabled? regex-filter? label title? tab-index debounce-delay class style attr]
              :or {id-fn :id label-fn :label group-fn :group render-fn label-fn}
              :as args}]
        {:pre [(validate-args-macro single-dropdown-args-desc args "single-dropdown")]}
        (let [choices          (if choices-fn? (:choices @choices-state) (deref-or-value choices))
              disabled?        (deref-or-value disabled?)
              regex-filter?    (deref-or-value regex-filter?)
              latest-ext-model (reagent/atom (deref-or-value model))
              _                (when (not= @external-model @latest-ext-model) ;; Has model changed externally?
                                 (reset! external-model @latest-ext-model)
                                 (reset! internal-model @latest-ext-model))
              changeable?      (and on-change (not disabled?))
              callback         #(do
                                  (reset! internal-model %)
                                  (when (and changeable? (not= @internal-model @latest-ext-model))
                                    (on-change @internal-model))
                                  (swap! drop-showing? not) ;; toggle to allow opening dropdown on Enter key
                                  (set-filter-text "" args false))
              cancel           #(do
                                  (reset! drop-showing? false)
                                  (set-filter-text "" args false)
                                  (reset! internal-model @external-model))
              dropdown-click   #(when-not disabled?
                                  (swap! drop-showing? not))
              filtered-choices (if choices-fn?
                                 choices
                                 (if regex-filter?
                                   (filter-choices-regex choices group-fn label-fn @filter-text)
                                   (filter-choices choices group-fn label-fn @filter-text)))
              press-enter      (fn []
                                 (if disabled?
                                   (cancel)
                                   (callback @internal-model))
                                 true)
              press-escape      (fn []
                                  (cancel)
                                  true)
              press-tab         (fn []
                                  (if disabled?
                                    (cancel)
                                    (do  ;; Was (callback @internal-model) but needed a customised version
                                      (when changeable? (on-change @internal-model))
                                      (reset! drop-showing? false)
                                      (set-filter-text "" args false)))
                                  (reset! drop-showing? false)
                                  true)
              press-up          (fn []
                                  (if @drop-showing?  ;; Up arrow
                                    (reset! internal-model (move-to-new-choice filtered-choices id-fn @internal-model -1))
                                    (reset! drop-showing? true))
                                  true)
              press-down        (fn []
                                  (if @drop-showing?  ;; Down arrow
                                    (reset! internal-model (move-to-new-choice filtered-choices id-fn @internal-model 1))
                                    (reset! drop-showing? true))
                                  true)
              press-home        (fn []
                                  (reset! internal-model (move-to-new-choice filtered-choices id-fn @internal-model :start))
                                  true)
              press-end         (fn []
                                  (reset! internal-model (move-to-new-choice filtered-choices id-fn @internal-model :end))
                                  true)
              key-handler      #(if disabled?
                                  false
                                  (case (.-which %)
                                    13 (press-enter)
                                    27 (press-escape)
                                    9  (press-tab)
                                    38 (press-up)
                                    40 (press-down)
                                    36 (press-home)
                                    35 (press-end)
                                    true)) ;; Use this boolean to allow/prevent the key from being processed by the text box
              filter-box-height 80
              menu-style-fn    (fn []
                                 (let [{:keys [top left width]} @position
                                       page-top    (.-pageYOffset js/window)
                                       page-left   (.-pageXOffset js/window)
                                       page-height (.-innerHeight js/window)]
                                   {:top    (str (- top page-top) "px")
                                    :left   (str (- left page-left) "px")
                                    :width  (str width "px")
                                    :height (str (- page-height (- top page-top) filter-box-height) "px")}))
              menu-style        (menu-style-fn)]
          [:div
           (merge
            {:class (str "mdc-select " (when @drop-showing? "mdc-select--open ") class)
             :role  "listbox"
             :style style}
            attr)          ;; Prevent user text selection
           [dropdown-top internal-model choices id-fn label-fn tab-index label dropdown-click key-handler drop-showing? title?]
           [:div
            {:class (str "mdc-menu mdc-select__menu " (when @drop-showing? "mdc-menu--open "))
             :style (select-keys menu-style [:top :left :width])}
            [filter-text-box filter-text key-handler drop-showing? #(set-filter-text % args true)]
            [:ul.mdc-list.mdc-menu__items
             {:style {:max-height (:height menu-style)}}
             (cond
               (and choices-fn? (:loading? @choices-state))
               [:li.mdc-list-item {:role "option" :aria-disabled true :tab-index -1} (str "Loading...")]
               (and choices-fn? (:error @choices-state))
               [:li.mdc-list-item {:role "option" :aria-disabled true :tab-index -1} (:error @choices-state)]
               (-> filtered-choices count pos?)
               (let [[group-names group-opt-lists] (choices-with-group-headings filtered-choices group-fn)
                     make-a-choice                 (partial make-choice-item id-fn render-fn callback internal-model)
                     make-choices                  #(map make-a-choice %1)
                     make-h-then-choices           (fn [h opts]
                                                     (cons (make-group-heading h)
                                                           (make-choices opts)))
                     has-no-group-names?           (nil? (:group (first group-names)))]
                 (if (and (= 1 (count group-opt-lists)) has-no-group-names?)
                   (make-choices (first group-opt-lists)) ;; one group means no headings
                   (apply concat (map make-h-then-choices group-names group-opt-lists))))
               :else
               [:li.mdc-list-item {:role "option" :aria-disabled true :tab-index -1} (str "No results match \"" @filter-text "\"")])]]]))})))
