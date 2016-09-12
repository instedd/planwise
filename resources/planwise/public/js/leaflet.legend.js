// goog.provide("leaflet.control.legend");

(function() {
  var buckets = [
    "1 - 5",
    "6 - 25",
    "26 - 250",
    "251 - 1,000",
    "+1,001"
  ];

  var createCollapsedScale = function(buckets, pixelArea, container) {
    var collapsedContent = L.DomUtil.create("div", "color-scale collapsed", container);

    L.DomUtil.create("span", "", collapsedContent).innerText = "1";

    var colorBarContainer = L.DomUtil.create("div", "color-bar", collapsedContent);

    for(var i = 0; i < buckets.length; i++) {
      L.DomUtil.create("div", "category c" + (i+1), colorBarContainer);
    }

    L.DomUtil.create("span", "", collapsedContent).innerText = buckets[buckets.length - 1];
  };

  var createExpandedScale = function(buckets, pixelArea, container) {
    var expanededContent = L.DomUtil.create("div", "color-scale expanded", container);

    var createLi = function(label, category, parent) {
      var li = L.DomUtil.create("li", "", categories);
      L.DomUtil.create("div", "category " + category, li);
      L.DomUtil.create("span", "", li).innerText = label;
    };

    var categories = L.DomUtil.create("ul", "categories", expanededContent);

    for(var i = 0; i < buckets.length; i++) {
      createLi(buckets[i], "c" + (i+1), categories);
    }
  };

  L.Control.Legend = L.Control.extend({
    onAdd: function() {
      var container = L.DomUtil.create('div', 'legend');

      L.DomUtil.create("div", "title", container).innerHTML = "Population / Km<sup>2</sup>";

      createCollapsedScale(buckets, this.options.pixelArea, container);
      createExpandedScale(buckets, this.options.pixelArea, container);

      return container;
    }
  });

  L.control.legend = function(id, options) {
    return new L.Control.Legend(id, options);
  };
})();
