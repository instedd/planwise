// goog.provide("leaflet.geojsongroup");

// Modify L.Path to check for _pathGroup before _map in all relevant methods
// Instead of adding/removing itself from a _map, do it from its _pathGroup
L.Path.include({

  bringToFront: function () {
    var root = (this._pathGroup || this._map)._pathRoot,
        path = this._container;

    if (path && root.lastChild !== path) {
      root.appendChild(path);
    }
    return this;
  },

  bringToBack: function () {
    var root = (this._pathGroup || this._map)._pathRoot,
        path = this._container,
        first = root.firstChild;

    if (path && first !== path) {
      root.insertBefore(path, first);
    }
    return this;
  },

  onAdd: function (map) {
    this._map = map;

    if (!this._container) {
      this._initElements();
      this._initEvents();
    }

    this.projectLatlngs();
    this._updatePath();

    if (this._container) {
      var pathRoot = (this._pathGroup || this._map)._pathRoot;
      pathRoot.appendChild(this._container);
    }

    this.fire('add');

    map.on({
      'viewreset': this.projectLatlngs,
      'moveend': this._updatePath
    }, this);
  },

  onRemove: function (map) {
		(this._pathGroup || map)._pathRoot.removeChild(this._container);

		this.fire('remove');
    this._pathGroup = null;
		this._map = null;

		if (L.Browser.vml) {
			this._container = null;
			this._stroke = null;
			this._fill = null;
		}

		map.off({
			'viewreset': this.projectLatlngs,
			'moveend': this._updatePath
		}, this);
	}
});

/**
 * Creates an SVG <g> element at the map pathRoot, so all
 * paths it contains are included there.
 * @constructor
 */
L.PathGroup = L.Class.extend({
  includes: [L.Mixin.Events],

  initialize: function (style) {
    L.setOptions(this, style);
  },

  onAdd: function (map) {
    this._map = map;

    if (!this._pathRoot) {
      this._map._initPathRoot();
      this._pathRoot = L.Path.prototype._createElement('g');
    }

    this._map._pathRoot.appendChild(this._pathRoot);
    this._updateStyle();

    this.fire('add');
  },

  onRemove: function (map) {
    map._pathRoot.removeChild(this._pathRoot);

    this.fire('remove');
    this._map = null;
  },

  bringToFront: function () {
    var root = this._map._pathRoot,
        container = this._pathRoot;

    if (container && root.lastChild !== container) {
      root.appendChild(container);
    }
    return this;
  },

  bringToBack: function () {
    var root = this._map._pathRoot,
        container = this._pathRoot,
        first = root.firstChild;

    if (container && first !== container) {
      root.insertBefore(container, first);
    }
    return this;
  },

  setStyle: function(style) {
    if (typeof style === 'function') {
      style = style(this);
    }
    L.setOptions(this, style);
    this._updateStyle();
  },

  _updateStyle: function() {
    var style = this.options;
    if (style.opacity) {
      this._pathRoot.setAttribute('opacity', style.opacity);
    } else {
      this._pathRoot.removeAttribute('opacity');
    }
  }
});

// Modify featureGroup to relay bringToFront and bringToBack actions
// to its _pathGroup, if there is any
var _featureGroup_base = L.extend({}, L.FeatureGroup.prototype);
L.FeatureGroup.include({

  bringToFront: function() {
    _featureGroup_base.bringToFront.call(this);
    if (this._pathGroup) {
      this._pathGroup.bringToFront();
    }
  },

  bringToBack: function() {
    _featureGroup_base.bringToBack.call(this);
    if (this._pathGroup) {
      this._pathGroup.bringToBack();
    }
  }
});

// Modify GeoJSON to initialize a pathGroup if requested, since
// it does not invoke its ancestor (LayerGroup) constructor
L.GeoJSON.addInitHook(function () {
  if (this.options.pathGroup) {
    this._pathGroup = L.pathGroup(this.options.pathGroup);
  }
});

// Modify LayerGroup to create a pathGroup if requested, and register
// all its layers in it, so they are created within the path group
var _layerGroup_base = L.extend({}, L.LayerGroup.prototype);
L.LayerGroup.include({

  initialize: function (layers, options) {
    if (options && options.pathGroup) {
      this._pathGroup = L.pathGroup(options.pathGroup);
    }
    _layerGroup_base.initialize.call(this, layers);
  },

  onAdd: function (map) {
    if (this._pathGroup) {
      map.addLayer(this._pathGroup);
    }
    _layerGroup_base.onAdd.call(this, map);
  },

  onRemove: function (map) {
    _layerGroup_base.onRemove.call(this, map);
    if (this._pathGroup) {
      map.removeLayer(this._pathGroup);
    }
  },

  setPathGroupStyle: function (style) {
    if (this._pathGroup) {
      this._pathGroup.setStyle(style);
    }
  },

  addLayer: function(layer) {
    if (this._pathGroup) {
      layer._pathGroup = this._pathGroup;
    }
    _layerGroup_base.addLayer.call(this, layer);
  },

  removeLayer: function(layer) {
    if (this._pathGroup && layer._pathGroup == this._pathGroup) {
      layer._pathGroup = null;
    }
    _layerGroup_base.removeLayer.call(this, layer);
  }
});

L.geoJson.group = function (geojson, options) {
  return new L.GeoJSON(geojson, L.extend({pathGroup: {}}, options));
};

L.layerGroup.withPathGroup = function (layers, options) {
  return new L.LayerGroup(layers, L.extend({pathGroup: {}}, options));
};

L.pathGroup = function(options) {
  return new L.PathGroup(options);
};

// leaflet.geojsongroup = L.geoJson.group;
