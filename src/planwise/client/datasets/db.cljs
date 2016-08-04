(ns planwise.client.datasets.db)

(def empty-datasets-selected
  {:collection nil
                                        ; The ID of the currently selected collection
   :valid?     false
                                        ; If the collection if valid for import
   :fields     nil
                                        ; Fields available for mapping to facility type
   :type-field nil})
                                        ; Field selected for mapping facility type

(def initial-db
  {:state nil
                                        ; :initialising/nil :ready :importing
   :cancel-requested false
   :raw-status       nil
   :facility-count   nil
                                        ; Count of available facilities
   :resourcemap {
                 :authorised?  nil
                                        ; Whether the user has authorised for
                                        ; Resourcemap access
                 :collections  nil}
                                        ; Resourcemap collections

   :selected empty-datasets-selected})
