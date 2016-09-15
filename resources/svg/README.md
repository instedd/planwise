## Add new icons

1. Search for them (here)[https://design.google.com/icons/]
2. Download them in the icons subfolder as:
   - 24dp
   - black
   - SVG
3. Edit them so it follows the current norm. Add it a title tag, remove the fill attribute from the svg tag, the height and the width.
4. Run `lein build-icons`
5. Draw it with `planwise.client.components.common/icon`.
