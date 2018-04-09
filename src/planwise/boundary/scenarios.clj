(ns planwise.boundary.scenarios)

(defprotocol Scenarios
  "API for manipulating scenarios"

  (list-scenarios [this project-id]
    "Returns the list of the scenarios owned by the project")

  (get-scenario [this scenario-id]
    "Finds a scenario by id")

  (create-initial-scenario [this project-id]
    "Creates the initial scenario for the given project. Deferred computation will occur.")

  (create-scenario [this project-id props]
    "Creates an scenario the given project. Deferred computation will occur.")

  (update-scenario [this scenario-id props]
    "Updates the given scenario. Deferred computation will occur.")

  (next-scenario-name [this project-id name]
    "Returns a name for the following scenario to be created based on name"))
