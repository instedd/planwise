(ns planwise.boundary.maps)

(defprotocol Maps
  "Mapping utilities"

  (mapserver-url [service]
    "Retrieve the demographics tile URL template"))
