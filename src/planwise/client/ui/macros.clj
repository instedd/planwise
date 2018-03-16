(ns planwise.client.ui.macros)

(def rmwc-tags
  '[
    Button
    Checkbox
    Fab
    FormField
    Icon
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
    Ripple
    Select
    SimpleMenu
    Slider
    Switch
    Tab
    TabBar
    TabIcon
    TabIconText
    TabBarScroller
    TextField
    TextFieldHelperText
    TextFieldIcon
    Theme
    Toolbar
    ToolbarRow
    ToolbarSection
    ToolbarTitle
    Typography])

(defn rmwc-ui-react-import [tname]
  `(def ~tname
     (reagent/adapt-react-class (aget js/rmwc ~(name tname)))))

(defmacro export-rmwc []
  `(do
     ~@(map rmwc-ui-react-import rmwc-tags)))
