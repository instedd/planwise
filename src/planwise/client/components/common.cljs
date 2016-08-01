(ns planwise.client.components.common)

;; Loading placeholder

(defn loading-placeholder []
  [:div.loading
   [:h3 "Loading..."]])

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
