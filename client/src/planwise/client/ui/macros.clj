(ns planwise.client.ui.macros)

(def rmwc-tags
  '[Button
    Checkbox
    Chip
    ChipIcon
    ChipSet
    ChipText
    Dialog
    DialogSurface
    DialogHeader
    DialogHeaderTitle
    DialogBody
    DialogFooter
    DialogFooterButton
    DialogBackdrop
    Elevation
    Fab
    FormField
    Grid
    GridCell
    GridInner
    GridList
    GridTile
    GridTileIcon
    GridTilePrimary
    GridTilePrimaryContent
    GridTileSecondary
    GridTileTitle
    Icon
    List
    ListDivider
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
    SimpleDialog
    SimpleMenu
    Slider
    Switch
    Tab
    TabBar
    TabIcon
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
     (reagent/adapt-react-class ~(symbol (str "rmwc/" (name tname))))))

(defmacro export-rmwc []
  `(do
     ~@(map rmwc-ui-react-import rmwc-tags)))
