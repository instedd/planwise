(ns planwise.client.components.common)

;; SVG based icons

(defn icon [icon-name & [icon-class]]
  (let [icon-name  (some-> icon-name name)
        icon-class (or icon-class "icon")]
    [:svg {:class icon-class
           ; Support for :xlinkHref is present in React 0.14+, but reagent 0.5.1 is bundled with 0.13
           ; Change the dangerouslySetInnerHTML call to the following content after upgrading
           ; [:use {:xlinkHref (str "#icon-" icon-name)}]])
           :dangerouslySetInnerHTML {:__html (str "<use xlink:href=\"#icon-" icon-name "\" />")}}]))

