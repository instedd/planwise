goog.provide("leaflet.bboxloader");

var throwFn = function(text) {
  throw new Error(text);
};

L.Bounds.prototype.asLatLngBounds = function() {
  var north = this.min.y,
      west = this.min.x,
      south = this.max.y,
      east = this.max.x;

  return L.latLngBounds(L.latLng(south, west),
                        L.latLng(north, east));
};

L.LatLngBounds.prototype.asBounds = function() {
  var top = this._northEast.lat,
      right = this._northEast.lng,
      bottom = this._southWest.lat,
      left = this._southWest.lng;

  return L.bounds(L.point(left, top),
                  L.point(right, bottom));
};

L.GeoJSON.include({
  asLayers: function(geojson, target) {
    var features = L.Util.isArray(geojson) ? geojson : geojson.features,
        layers = target || [],
        i, len, feature;

    if (features) {
      for (i = 0, len = features.length; i < len; i++) {
        // Only add this if geometry or geometries are set and not null
        feature = features[i];
        if (feature.geometries || feature.geometry || feature.features || feature.coordinates) {
          this.asLayers(features[i], layers);
        }
      }
      return layers;
    }

    var options = this.options;

    if (options.filter && !options.filter(geojson)) { return; }

    var layer = L.GeoJSON.geometryToLayer(geojson, options.pointToLayer, options.coordsToLatLng, options);
    layer.feature = L.GeoJSON.asFeature(geojson);

    layer.defaultOptions = layer.options;
    this.resetStyle(layer);

    if (options.onEachFeature) {
      options.onEachFeature(geojson, layer);
    }

    layers.push(layer);
    return layers;
  }
});


L.BBoxLoader = L.Class.extend({

  initialize: function(options) {
    this._callback = options.callback || throwFn("'callback' is required");
    this._layer = options.layer || throwFn("'layer' is required");
    this._levels = options.levels || throwFn("'levels' is required");
    this._idFn = options.idFn || function(x) { return x.id; };
    this._featureFn = options.featureFn || function(x) { return x; };

    this._featuresCurrentLevel = {}; // id -> level

    this._tileCache = {};
    this._featureCache = {}; // level -> id -> [feature]

    for (levelKey in this._levels) {
      this._tileCache[levelKey] = {};
      this._featureCache[levelKey] = {};
    };
  },

  onAdd: function(map) {
    this._map = map;
    map.on({
      'viewreset': this._reset,
      'moveend': this._update
    }, this);

    map.addLayer(this._layer);
    this._update();
    return this;
  },

  onRemove: function(map) {
    map.removeLayer(this._layer);
  },

  addFeature: function(level, featureId, newFeatures) {
    var self = this;

    // If the feature is currently displayed at a lower zoom level than
    // the desired one, or isn't displayed at all, replace it in the layer
    var featureCurrentLevel = this._featuresCurrentLevel[featureId];
    if (featureCurrentLevel && featureCurrentLevel >= level) return;

    var oldFeatures = null;
    if (featureCurrentLevel) {
      oldFeatures = this._featureCache[featureCurrentLevel][featureId];
    }

    newFeatures = L.Util.isArray(newFeatures) ? newFeatures : [newFeatures];
    newFeatures.forEach(function(f) {
      self._layer.addLayer(f);
    });

    this._featureCache[level][featureId] = newFeatures;
    this._featuresCurrentLevel[featureId] = level;

    if (oldFeatures) {
      oldFeatures.forEach(function(f) {
        self._layer.removeLayer(oldFeatures);
      });
    }

    return this;
  },

  _update: function() {
    if (!this._map)
      return;

    var level = this._getCurrentLevel(),
        map = this._map,
        bounds = map.getBounds().pad(0.5).asBounds(),
        tileSize = this._levels[level].tileSize || throwFn("Tile size not set for level " + level),
        tileIndices = L.bounds(
          bounds.min.divideBy(tileSize)._floor(),
          bounds.max.divideBy(tileSize)._floor());

    this._addTiles(level, tileSize, tileIndices);
  },

  _reset: function() {
  },

  _getCurrentLevel: function() {
    var map = this._map || throwFn("Cannot get current level unattached to a map"),
        level = this._map.getZoom();

    for(levelKey in this._levels) {
      var lb = this._levels[levelKey].lb,
          ub = this._levels[levelKey].ub;
      if ((!lb || level >= lb) && (!ub || level < ub)) {
        return levelKey;
      }
    }

    throw new Error("Could not determine layer level for map level " + level);
  },

  _addTiles: function(level, tileSize, tileIndices) {
    var self = this;

    // Check if any tile within the bbox requires to be loaded
    var bounds = L.bounds([]);
    for (var j = tileIndices.min.y; j <= tileIndices.max.y; j++) {
      for (var i = tileIndices.min.x; i <= tileIndices.max.x; i++) {
        var tileKey = (i + ":" + j + ":" + level);
        if (!this._tileCache[tileKey]) {
          bounds.extend(L.point(i, j));
          this._tileCache[tileKey] = true; // Mark as requested
        }
      }
    }

    if (!bounds.isValid()) {
      return;
    }

    // Map tile indices bounds to actual latlng bounds
    var latLngBounds = L.bounds(
                         bounds.min.multiplyBy(tileSize),
                         bounds.max.add(L.point(1,1)).multiplyBy(tileSize)
                       ).asLatLngBounds();

    // Retrieve what features have been already loaded for the current level or greater
    var loadedFeatures = [];
    for (var featureId in this._featuresCurrentLevel) {
      var featureLevel = this._featuresCurrentLevel[featureId];
      if (featureLevel >= level) {
        loadedFeatures.push(featureId);
      }
    }

    // Issue callback to request features
    this._callback(level, latLngBounds.toBBoxString(), loadedFeatures, function(newFeatures) {
      if (newFeatures.length > 0) {
        console.log("Loading " + newFeatures.length + " new features on level " + level);
      }
      newFeatures.forEach(function(f) {
        var newFeature = self._featureFn(f) || throwFn("Could not retrieve feature"),
            featureId = self._idFn(f) || throwFn("Could not retrieve id for feature");
        // If feature is actually plain geojson data, pass it through asLayers before adding it as a feature
        if (typeof(newFeature.onAdd) !== "function" && typeof(self._layer.asLayers) === "function") {
          newFeature = self._layer.asLayers(newFeature);
        }
        self.addFeature(level, featureId, newFeature);
      });
    });
  }
});

L.bboxLoader = function(options) {
  return new L.BBoxLoader(options);
};

leaflet.bboxloader = L.bboxLoader;
