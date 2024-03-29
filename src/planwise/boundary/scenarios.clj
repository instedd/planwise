(ns planwise.boundary.scenarios)

(defprotocol Scenarios
  "API for manipulating scenarios"

  (list-scenarios [this project]
    "Returns the list of the scenarios owned by the project")

  (get-scenario [this scenario-id]
    "Finds a scenario by id")

  (create-initial-scenario [this project]
    "Creates the initial scenario for the given project. Deferred computation will occur.")

  (create-scenario [this project props]
    "Creates an scenario the given project. Deferred computation will occur.")

  (update-scenario [this project props]
    "Updates the given scenario. Deferred computation will occur.")

  (next-scenario-name [this scenario]
    "Returns a name for a scenario to be created based on another one")

  (reset-scenarios [this project-id]
    "Reset scenarios information of project and clear engine state")

  (get-scenario-for-project [this scenario project]
    "Gicen scenario retrieves same scenario with updated demand information and new field providers-data.
     Providers data is a map of providers:
     each provider with id, capacity and demand information updated to last computation.
     If no computation is registered, providers-data is empty.")

  (export-providers-data [this project scenario]
    "Create CSV file with scenario's demand information for computed and disabled providers")

  (export-sources-data [this project scenario]
    "Create CSV file with scenario's demand information for point sources. Fails if the project is of type raster.")

  (get-suggestions-for-new-provider-location [store project scenario]
    "Get list of locations suggested for creating new provider")

  (get-provider-geom [store project scenario provider-id]
    "Retrieves provider coverage geometries")

  (get-suggestion-geom [store project scenario iteration]
    "Retrieves suggested location coverage for iteration")

  (delete-scenario [store scenario-id]
    "Delete scenario by id.
     Delete scenario's created files.")

  (get-suggestions-for-improving-providers [store project scenario]
    "Get list of providers suggested for improvements"))
