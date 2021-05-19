// goog.provide("leaflet.control.legend");

(function() {
  var buckets = [
    {class: "idle-capacity",  label: "+10%"},
    {class: "at-capacity",  label: "at capacity"},
    {class: "small-unsatisfied",  label: "-10%"},
    {class: "mid-unsatisfied",  label: ""},
    {class: "mid-unsatisfied",  label: "-30%"},
    {class: "unsatisfied",  label: ""},
  ];

  var createExpandedScale = function(buckets, pixelArea, container) {
    var expanededContent = L.DomUtil.create("div", "", container);

    var createItem = function(item, category, parent) {
      var itemContainer = L.DomUtil.create("div", "category", categories);
      if(item.label.length > 0) {
        L.DomUtil.create("div", "leaflet-legend-label", itemContainer).innerText = item.label;
      }
      L.DomUtil.create("div", "leaflet-color " + item.class, itemContainer);
    };

    var categories = L.DomUtil.create("div", "categories", expanededContent);

    for(var i = 0; i < buckets.length; i++) {
      createItem(buckets[i], "c" + (i+1), categories);
    }
  };

  L.Control.Legend = L.Control.extend({
    onAdd: function() {
      var container = L.DomUtil.create('div', 'legend leaflet-legend-container');

      L.DomUtil.create("div", "title", container).innerHTML = this.options.providerUnit + " capacity / population";

      createExpandedScale(buckets, this.options.pixelArea, container);

      return container;
    }
  });

  L.control.legend = function(options) {
    return new L.Control.Legend({...options, position: "bottomright"});
  };
})();
