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
    "Sets configuration of scenario for current project")

  (export-providers-data [this scenario-id]
    "Create CSV file with scenario providers data and computed demand")

  (get-provider-suggestion [store project scenario]
    "Get list of providers suggestions."))

