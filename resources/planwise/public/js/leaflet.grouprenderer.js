// goog.provide("leaflet.geojsongroup");

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

