// goog.provide("leaflet.control.legend");

(function() {
  var buckets = [
    {class: "idle-capacity",  label: "> 10% over capacity"},
    {class: "at-capacity",  label: "At capacity"},
    {class: "small-unsatisfied",  label: "< 10% unsatisfied"},
    {class: "mid-unsatisfied",  label: "< 30% unsatisfied"},
    {class: "unsatisfied",  label: "> 30% unsatisfied"},
    {class: "selected",  label: "Selected"},
    {class: "not-matching",  label: "Not Matching Filters"}
  ];

  var createExpandedScale = function(buckets, pixelArea, container) {
    var expanededContent = L.DomUtil.create("div", "", container);

    var createItem = function(item, category, parent) {
      var itemContainer = L.DomUtil.create("div", "", categories);
      L.DomUtil.create("div", "leaflet-circle-icon leaflet-circle-for-change leaflet-legend-icon " + item.class, itemContainer);
      L.DomUtil.create("div", "leaflet-legend-label", itemContainer).innerText = item.label;
    };

    var categories = L.DomUtil.create("div", "categories", expanededContent);

    for(var i = 0; i < buckets.length; i++) {
      createItem(buckets[i], "c" + (i+1), categories);
    }
  };

  L.Control.Legend = L.Control.extend({
    onAdd: function() {
      var container = L.DomUtil.create('div', 'legend leaflet-legend-container');

      L.DomUtil.create("div", "title", container).innerHTML = "Satisfied Demand by provider";

      createExpandedScale(buckets, this.options.pixelArea, container);

      return container;
    }
  });

  L.control.legend = function(id, options) {
    return new L.Control.Legend(id, options);
  };
})();
