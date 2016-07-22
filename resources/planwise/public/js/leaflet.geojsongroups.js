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
	},
});

// L.PathGroup creates an SVG <g> element at the map pathRoot, so all
// paths it contains are included there.
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

// Adds a _pathGroup property to all dependent layers, which is a PathGroup
// element that renders an SVG <g> element to contain all paths
L.GeoJSON.Group = L.GeoJSON.extend({

  initialize: function (geojson, options) {
    var self = this;
    this._pathGroup = L.pathGroup(options.pathGroup);

    var newOptions = L.extend({}, options, {
      onEachFeature: function(geojson, layer) {
        if (options && options.onEachFeature) {
          options.onEachFeature(geojson, layer);
        }
        layer._pathGroup = self._pathGroup;
      }
    });

    L.GeoJSON.prototype.initialize.call(this, geojson, newOptions);
  },

  onAdd: function (map) {
    map.addLayer(this._pathGroup);
    L.GeoJSON.prototype.onAdd.call(this, map);
  },

  onRemove: function (map) {
    L.GeoJSON.prototype.onRemove.call(this, map);
    map.removeLayer(this._pathGroup);
  },

  setPathGroupStyle: function (style) {
    this._pathGroup.setStyle(style);
  },

  bringToFront: function() {
    L.GeoJSON.prototype.bringToFront.call(this);
    this._pathGroup.bringToFront();
  },

  bringToBack: function() {
    L.GeoJSON.prototype.bringToBack.call(this);
    this._pathGroup.bringToBack();
  }

});

L.geoJson.group = function (geojson, options) {
  return new L.GeoJSON.Group(geojson, options);
};

L.pathGroup = function(options) {
  return new L.PathGroup(options);
};
