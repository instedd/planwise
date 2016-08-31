// goog.provide("leaflet.control.legend");

var formatNumber = function(n, area) {
  var nKm2 = (1000000 / area) * n // 1000000 squared meters in 1 squared kilometer
  var millions, thousands;


  if ((millions = nKm2 / 1000000) > 1) {
    return Math.round(millions) + "M";
  } else if ((thousands = nKm2 / 1000) > 1) {
    return Math.round(thousands) + "K";
  } else {
    return nKm2.toString();
  }
}

L.Control.Legend = L.Control.extend({
  onAdd: function() {
    var container = L.DomUtil.create('div', 'legend');

    var title = L.DomUtil.create("div", "title", container);
    title.innerHTML = "Population / Km<sup>2</sup>";

    var content = L.DomUtil.create("div", "color-scale", container);

    var min = L.DomUtil.create("span", "", content);
    min.innerText = "0";

    var colorBarContainer = L.DomUtil.create("div", "color-bar", content)
    L.DomUtil.create("div", "", colorBarContainer);

    var max = L.DomUtil.create("span", "", content);
    max.innerText = formatNumber(this.options.legendMax, this.options.legendDenominatorM2);

    return container;
  }
})

L.control.legend = function(id, options) {
  return new L.Control.Legend(id, options);
}
