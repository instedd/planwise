(ns planwise.client.mapping)

(def mapbox-tile-url "http://api.tiles.mapbox.com/v4/{mapid}/{z}/{x}/{y}.png?access_token={accessToken}")
(def mapbox-mapid "ggiraldez.056e1919")
(def mapbox-access-token "pk.eyJ1IjoiZ2dpcmFsZGV6IiwiYSI6ImNpb2E1Zmh3eDAzOWR2YWtqMTV6eDBma2gifQ.kMQcRBGO5cnrJowATNNHLA")

(def default-base-tile-layer
  [:tile-layer {:url mapbox-tile-url
                :attribution "&copy; Mapbox"
                :maxZoom 18
                :mapid mapbox-mapid
                :accessToken mapbox-access-token}])
