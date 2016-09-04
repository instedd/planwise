## Add new icons

1- Search for them (here)[https://design.google.com/icons/]
2- Bajalos en 24dp, negro, SVG y tiralos en esa carpeta
2- Download them in the icons subfolder as:
   - 24dp
   - black
   - SVG
3- Editá el SVG con tu editor de texto favorito para q quede igual a los demás, ie: agregar un tag de <title>, borrar un fill q pueda haber a nivel del tag <svg>, borrar height y width hardcodeados (pero dejar viewbox)
3- Edit them so it follows the current norm. Add it a title tag, remove the fill attribute from the svg tag, the height and the width.
4- Run `lein build-icons`
5- Draw it with `planwise.client.components.common/icon`.
