(ns planwise.client.sources.views
  (:require [re-frame.core :as rf]
            [planwise.client.asdf :as asdf]
            [planwise.client.ui.common :as ui]
            [planwise.client.components.common2 :as common2]
            [planwise.client.components.common :as common]
            [planwise.client.modal.modal :as modal]))

;; ----------------------------------------------------------------------------
;; Sources list

(defn empty-list-view
  []
  [:div.empty-list-container
   [:div.empty-list
    [common/icon :box]
    [:p "You have no sources yet"]]])

(defn source-card
  [props source]
  (let [name (:name source)]
    [ui/card {:title name}]))

(defn list-view
  [sources]
  (if (empty? sources)
    [empty-list-view]
    [ui/card-list {:class "dataset-list"}
     (for [source sources]
       [source-card {:key (:id source)} source])]))

(defn new-source-view
  []
  (let [new-source (rf/subscribe [:sources.new/data])]
    (fn []
      (let [name (:name @new-source)
            csv-file (:csv-file @new-source)]
        [:form.vertical
         [common2/text-field {:label "Name"
                              :value name
                              :on-change #(rf/dispatch [:sources.new/update {:name (-> % .-target .-value)}])}]

         [:label.file-input-wrapper
          [:div "Import sources from CSV"]
          [:input {:id "file-upload"
                   :type "file"
                   :class "file-input"
                   :value ""
                   :on-change  #(rf/dispatch [:sources.new/update {:csv-file (-> (.-currentTarget %) .-files (aget 0))}])}]
          (when (some? csv-file)
            (println csv-file)
            [:span (.-name csv-file)])]

       ;[:a {:href (routes/download-sample)
       ;     :data-trigger "false"} "Download samples sites"]
       ;[m/Select {:label "Coverage algorithm"
       ;           :value @coverage
       ;           :options @algorithms
       ;           :on-change #(rf/dispatch [:datasets2/new-dataset-update
       ;                                     :coverage (-> % .-target .-value)])}]

       ;(when-let [last-error @(rf/subscribe [:datasets2/last-error])]
       ;  [:div.error-message
       ;   (str last-error)])
]))))

(defn sources-page
  []
  (let [r-sources (rf/subscribe [:sources/list-filtered-by-type-points])
        btn-new (ui/main-action {:icon "add"
                                 :on-click #(rf/dispatch [:modal/show])})]
    (fn []
      (let [sources @r-sources]
        (if (nil? sources)
          [common2/loading-placeholder]
          [ui/fixed-width (assoc (common2/nav-params)
                                 :action btn-new)
           [list-view sources]
           [modal/modal-view {:title "New source"
                              :accept-label "Create"
                              :accept-fn (fn [] (rf/dispatch [:sources.new/create]))
                              :cancel-fn (fn [] (println "cancel!"))
                              :accept-disabled? false}
            [new-source-view]]])))))
