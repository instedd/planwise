(ns planwise.boundary.scenarios)

(defprotocol Scenarios
  "API for manipulating scenarios"

  (list-scenarios [this project-id]
    "Returns the list of the scenarios owned by the project")

  (get-scenario [this scenario-id]
    "Finds a scenario by id")

  (create-initial-scenario [this project]
    "Creates the initial scenario for the given project. Deferred computation will occur.")

  (create-scenario [this project props]
    "Creates an scenario the given project. Deferred computation will occur.")

  (update-scenario [this project props]
    "Updates the given scenario. Deferred computation will occur.")

  (next-scenario-name [this project-id name]
    "Returns a name for the following scenario to be created based on name")

  (reset-scenarios [this project-id]
    "Reset scenarios information of project and clear engine state")

  (get-scenario-for-project [this scenario project]
    "Gicen scenario retrieves same scenario with updated demand information and new field providers-data.
     Providers data is a map of providers:
     each provider with id, capacity and demand information updated to last computation.
     If no computation is registered, providers-data is empty.")

  (export-providers-data [this project scenario]
    "Create CSV file with scenario's demand information for computed and disabled providers")

  (get-provider-suggestion [store project scenario]
    "Get list of providers suggestions.")

  (get-provider-geom [store scenario project provider-id]
    "Retrieves provider coverage geometries")

  (delete-scenario [store scenario-id]
    "Delete scenario by id"))
