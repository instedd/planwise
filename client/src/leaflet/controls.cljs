(ns leaflet.controls
  (:require [crate.core :as crate]))


(def ^:private MapboxLogo
  (js/L.Control.extend
   #js {:onAdd #(crate/html [:a.mapbox-logo
                             {:href   "https://www.mapbox.com/about/maps"
                              :target "_blank"}
                             "MapBox"])}))

(defn- mapbox-logo
  [options]
  (new MapboxLogo (clj->js (merge {:position "topright"} options))))

(defn- reference-table-content
  [{:keys [hide-actions?] :as options}]
  (crate/html
   [:div.map-reference-table
    (when-not hide-actions?
      [:div
       [:h1 "Actions"]
       [:ul
        [:li [:i.material-icons "domain"] "New provider"]
        [:li [:i.material-icons "arrow_upward"] "Upgrade provider"]
        [:li [:i.material-icons "add"] "Increase capacity"]]
       [:hr]])
    [:h1 "Provider capacity"]
    [:ul
     [:li [:div.leaflet-circle-icon.idle-capacity] "Excess"]
     [:li [:div.leaflet-circle-icon.at-capacity] "Covered"]
     [:li [:div.leaflet-circle-icon.unsatisfied] "Shortage"]]
    [:hr]
    [:h1 "Unsatisfied demand"]
    [:ul.scale
     [:li.q1 "Q1"]
     [:li.q2 "Q2"]
     [:li.q3 "Q3"]
     [:li.q4 "Q4"]]]))

(def ^:private ReferenceTable
  (js/L.Control.extend
   #js {:onAdd (fn []
                 (let [options (js->clj (.-options (js-this)) :keywordize-keys true)]
                   (reference-table-content options)))}))

(defn- reference-table
  [options]
  (new ReferenceTable (clj->js (merge {:position "bottomright"} options))))


(defn create-control
  [control-def]
  (let [[type props] (if (vector? control-def) control-def [control-def {}])]
    (case type
      :zoom            (.zoom js/L.control)
      :attribution     (.attribution js/L.control #js {:prefix false})
      :mapbox-logo     (mapbox-logo props)
      :reference-table (reference-table props)
      :legend          (.legend js/L.control #js {:pixelMaxValue (:pixel-max-value props)
                                                  :pixelArea     (:pixel-area props)
                                                  :providerUnit  (:provider-unit props)})
      (throw (ex-info "Invalid control type" control-def)))))
