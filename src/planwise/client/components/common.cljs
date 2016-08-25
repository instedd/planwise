(ns planwise.client.components.common)

;; Loading placeholder

(defn loading-placeholder []
  [:div.loading
   [:h3 "Loading..."]])

;; SVG based icons

(defn icon [icon-name & [icon-class]]
  (let [icon-name  (some-> icon-name name)
        icon-class (or icon-class "icon")]
    [:svg {:class icon-class
           ; Support for :xlinkHref is present in React 0.14+, but reagent 0.5.1 is bundled with 0.13
           ; Change the dangerouslySetInnerHTML call to the following content after upgrading
           ; [:use {:xlinkHref (str "#icon-" icon-name)}]])
           :dangerouslySetInnerHTML {:__html (str "<use xlink:href=\"#icon-" icon-name "\" />")}}]))

;; Modal dialog

(defn modal-dialog [{:keys [on-backdrop-click] :as props} & children]
  (let [children (if (map? props) children [props])]
    [:div.modal
     [:div.backdrop]
     (into [:div.modal-container {:on-click
                                  (fn [e] (when (and (= (aget e "target")
                                                        (aget e "currentTarget"))
                                                     on-backdrop-click)
                                            (on-backdrop-click)))}]
           children)]))

(defn close-button [props]
  [:button.mini.close (assoc props :type "button") "\u2716"])

(defn refresh-button [props]
  [:button.mini.refresh (assoc props :type "button") "\u21bb"])
