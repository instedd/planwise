(ns planwise.client.ui.macros)

(def rmwc-tags
  '[
    Button
    Checkbox
    Fab
    FormField
    List
    ListItem
    ListItemGraphic
    ListItemMeta
    ListItemSecondaryText
    ListItemText
    Menu
    MenuAnchor
    MenuItem
    Radio
    Select
    SimpleMenu
    Slider
    Switch
    TextField
    TextFieldHelperText
    TextFieldIcon])

(defn rmwc-ui-react-import [tname]
  `(def ~tname
     (reagent/adapt-react-class (aget js/rmwc ~(name tname)))))

(defmacro export-rmwc []
  `(do
     ~@(map rmwc-ui-react-import rmwc-tags)))
