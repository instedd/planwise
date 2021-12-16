// goog.provide("leaflet.ext");

// The default SVG renderer will render *all* vector features under a single <g>
// element.

// We use this renderer to set style attributes on the <g> that contains all the
// features from a layer; eg. to set the global opacity of the isochrones.

L.SVG.GroupRenderer = L.SVG.extend({
    _initContainer: function() {
        L.SVG.prototype._initContainer.call(this);

        // set group style preferences
        // TODO: add other applicable style attributes
        var options = this.options;
        if (options.opacity) {
            this._rootGroup.setAttribute("opacity", options.opacity);
        }
    }
});

L.SVG.groupRenderer = function(options) {
    return new L.SVG.GroupRenderer(options);
};

// These functions below are copied from a later version of Leaflet. Once we
// update the dependency we can safely remove them.

L.toPoint = function(x, y, round) {
	if (x instanceof L.Point) {
		return x;
	}
	if (L.Util.isArray(x)) {
		return new L.Point(x[0], x[1]);
	}
	if (x === undefined || x === null) {
		return x;
	}
	if (typeof x === 'object' && 'x' in x && 'y' in x) {
		return new L.Point(x.x, x.y);
	}
	return new L.Point(x, y, round);
};

L.toBounds = function(a, b) {
	if (!a || a instanceof L.Bounds) {
		return a;
	}
	return new L.Bounds(a, b);
};

L.Map.include({
  panInside: function (latlng, options) {
    options = options || {};

    var paddingTL = L.toPoint(options.paddingTopLeft || options.padding || [0, 0]),
        paddingBR = L.toPoint(options.paddingBottomRight || options.padding || [0, 0]),
        pixelCenter = this.project(this.getCenter()),
        pixelPoint = this.project(latlng),
        pixelBounds = this.getPixelBounds(),
        paddedBounds = L.toBounds([pixelBounds.min.add(paddingTL), pixelBounds.max.subtract(paddingBR)]),
        paddedSize = paddedBounds.getSize();

    if (!paddedBounds.contains(pixelPoint)) {
      this._enforcingBounds = true;
      var centerOffset = pixelPoint.subtract(paddedBounds.getCenter());
      var offset = paddedBounds.extend(pixelPoint).getSize().subtract(paddedSize);
      pixelCenter.x += centerOffset.x < 0 ? -offset.x : offset.x;
      pixelCenter.y += centerOffset.y < 0 ? -offset.y : offset.y;
      this.panTo(this.unproject(pixelCenter), options);
      this._enforcingBounds = false;
    }
    return this;
  },
});
