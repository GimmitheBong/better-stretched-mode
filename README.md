# Better Stretched Mode

Independently resize the minimap/orbs, inventory and other side panels, and modern-layout tab bars in RuneLite. Choose smoother or sharper filtering and keep right-click menus beside the visible cursor.

Source: [GimmitheBong/better-stretched-mode](https://github.com/GimmitheBong/better-stretched-mode)

Plugin Hub submission: [runelite/plugin-hub#17942](https://github.com/runelite/plugin-hub/pull/17942). Installation through the Hub becomes available after maintainer approval and merge.

## Version 0.4.1 fixes

- Select only the active layout's widgets, including the full classic inventory frame; inactive modern panels/bars cannot appear over it.
- Keep camera-drag mouse deltas continuous across resized controls and RuneLite Camera button remapping.
- Preserve native GPU inventory transparency and border colors through premultiplied-alpha extraction, filtering and redraw.

## Features

- **Minimap:** resize the map, compass, health/prayer/run/special-attack orbs and nearby buttons together.
- **Inventory / side panel:** resize the active inventory, spellbook, prayer, equipment or other side panel. In classic layout this includes the complete native inventory frame and its classic tab buttons; in modern layout it scales the open content panel. Each has move-left/up controls.
- **Modern layout tab bars:** independently resize and reposition the two rows of tab buttons.
- **Filtering & sharpening:** a separate settings section for the shared scaling filter, sharpening strength and scaling refresh limit.
- **Click-through:** exposed original widget locations do not block scene clicks when a region is shrunk or moved.
- **Cursor-relative menus:** context menus from scaled controls appear at the visible cursor, with matching movement, selection and wheel-input coordinates.

Minimap and side-panel scaling support **Resizable - Classic** and **Resizable - Modern**. Tab-bar scaling supports **Resizable - Modern**. Fixed mode and unavailable/hidden containers are bypassed.

Layout selection uses RuneLite's active top-level interface ID. Cached widgets from another layout are never used as a fallback. Classic mode uses its native `SIDE_MENU` frame and ignores the modern panel and tab-bar settings. Changing layouts restores old input flags and drops the old image, drag/menu and input caches before processing the new root.

## Settings

| Section | Settings |
| --- | --- |
| Minimap | Size 50–250% (default 150%), move left, move down. |
| Modern layout tab bars | Upper/lower sizes 50–250% (default 100%), independent move-left/up controls. |
| Inventory / side panel | Size 50–250% (default 100%), move left, move up. |
| Filtering & sharpening | Scaling filter, sharpening strength, scaling refresh limit. |

100% with zero offsets bypasses additional scaling for that region. The minimap keeps its top-right anchor; side panels and tab bars keep their bottom-right anchors. Size and position are clamped to the game buffer. Large regions can overlap other controls; overlapping scaled regions route input to the topmost one.

For example, lower the overall UI scale in RuneLite's built-in **Stretched Mode**, then set **Inventory / panel size** to **150%**. Global stretching still occurs afterward, while only the panel receives that additional enlargement. Settings are relative to the native game buffer, so the two layers of scaling combine.

Existing minimap, tab-bar and filtering preferences keep their original configuration keys.

## Filtering & sharpening

| Filter | Appearance |
| --- | --- |
| Nearest neighbor (pixel sharp) | Crisp original pixels; more blocky at fractional sizes. |
| Bilinear (smooth) | Softer edges; the former Smooth scaling option. |
| Bicubic | Smoother interpolation that can retain detail better than Bilinear. |
| Sharp Bicubic | Bicubic plus adjustable local-contrast sharpening. |

Try **Sharp Bicubic with 40% sharpening strength**. Increase toward 60% for more contrast or lower toward 20% for softer edges. At 0%, Sharp Bicubic matches Bicubic. High strengths can produce edge halos. Original-size images, including position-only changes, bypass filtering to preserve their pixels.

**Scaling refresh limit** defaults to **60 fps**. It limits expensive resampling, not game FPS or mouse-input processing. Cached images are still drawn every game frame. A newly shown control or size/filter change refreshes immediately. Set 0 for unrestricted resampling or lower the limit to 30 for additional CPU headroom.

## Performance

The shared render path uses direct int-buffer row copies, precomputed separable fixed-point interpolation, cached unchanged filtered images, and reused hit masks. Widget-tree traversal is reused within each client tick and invalidated before scripts can recreate widgets. Mouse-triggered input updates are coalesced.

In an earlier same-machine synthetic test at 150% with Sharp Bicubic, the animated minimap workload fell from about **6.36 to 3.17 ms/frame**, and mostly-static tab bars from **2.74 to 0.16 ms/frame**. The current four-region benchmark measured approximately **3.76 ms/frame** for all regions together and **0.40 ms/frame** for inventory alone. These are CPU capture/filter/input timings with the refresh limit disabled, not in-game FPS. They exclude native widget traversal, scene rendering and GPU costs.

Run it with `gradlew.bat benchmark`. Actual FPS depends on the renderer, sizes, filter, scene and hardware; compare settings in the same scene.

### Inventory transparency

Native GPU UI pixels are handled as premultiplied alpha throughout capture, filtering and redraw, regardless of how the Java image wrapper describes them. The resampler does not multiply those colors by alpha again, and a direct premultiplied source-over blit preserves their native alpha instead of passing them through a mismatched Java2D color model. When possible, extraction separates an already-composited UI background from the captured layer before moving it.

Regression tests cover the translucent interior and opaque border separately in **both classic and modern layouts**, with every filter, RGB/ARGB/premultiplied wrappers, existing translucent UI underneath, and renderer-format changes. The inventory transparency setting is not overridden. Compare 100% and an enlarged/shrunk size in the same scene for live confirmation of the corrected rendering.

## Right-click menus and input

Menus retain their normal size and native actions. Their visible position uses ordinary cursor-relative placement and screen-edge clamping. Their mouse input is translated to the same native bounds, preserving selection, scrolling and the usual close margin. Menus on unscaled controls or the game scene keep their normal behavior.

RuneLite has no public native-menu position setter, so the implementation captures the background before native menu drawing, restores the original location, and redraws the menu at the visible position. Visible submenu pixels move with the root translation. Menu entries and callbacks are not replaced; menus are not resized or filtered.

The side panel's entire resized rectangle stays interactive so empty item slots and transparent controls support dragging and scrolling. Minimap/tab-bar hit masks follow visible coverage. Left-button panel drags use the captured transform until release.

### Camera dragging

A held camera drag keeps a fixed mouse-coordinate translation instead of switching between scene, inventory, tab-bar and minimap mappings. Mouse deltas remain one-to-one in game-buffer coordinates for the gesture, including when it starts over a scaled control. Normal widget/menu routing resumes after release; focus loss or a missed release clears the captured gesture.

The native middle-camera toggle and RuneLite Camera's right-click settings are read on the client thread. The listener runs after button-remapping plugins, including when they are enabled later, and recognizes the native Alt/middle and Meta/right interpretation. Concurrent right-camera/menu input uses the same fixed coordinate space as the displayed menu during the hold, then ordinary cursor-relative menu placement resumes after release.

For the live check, hold the camera button in the scene, cross the inventory, both modern bars and the minimap repeatedly, and release over a scaled control. Also start a drag over a control, test the camera button/remapping you normally use, and check that ordinary item dragging and right-click menus still work afterward.

## Development

Use **JDK 17**. This project compiles Java 11-compatible plugin classes and uses RuneLite's latest release by default. It has no extra runtime dependencies and uses the Plugin Hub's `build=standard` mode.

On Windows, run `run-plugin.bat` to open a development RuneLite client with the plugin loaded. It selects the installed JDK 17 paths on the original development machine; set `MINIMAP_JAVA_HOME` to override them on another machine. Find **Better Stretched Mode** in the plugin list and open its settings.

From PowerShell with `JAVA_HOME` set to JDK 17:

```powershell
.\gradlew.bat build
.\gradlew.bat benchmark
.\gradlew.bat run
```

To test a particular RuneLite version:

```powershell
.\gradlew.bat build "-PruneliteVersion=1.13.1"
```

Plugin JAR: `build/libs/better-stretched-mode-0.4.1.jar`.

Test report: `build/reports/tests/test/index.html`.

Restart the development client after rebuilding; toggling the plugin does not reload compiled classes.

## Compatibility and verification

The scaling controls, click-through, cursor-relative root menus, layout-selection, camera-drag and transparency fixes have been reported working in-game by the user. The build and **all 90 automated tests pass against RuneLite 1.13.1**. Tests cover rendering/translucent interiors and borders, cached inactive-layout widgets, both layout-switch directions, camera gesture continuity/button remapping, coordinate/drag/menu routing, screen-edge placement, cached image/mask alignment, refresh limiting, native input-property restoration and buffer-format fallbacks.

- Separate RuneLite overlays, such as minimap tile markers or item overlays, are not individually rescaled and may be covered or misaligned.
- Native widget dimensions stay unchanged; this is rendered-image scaling with paired input translation.
- The software renderer has no separate UI alpha layer. Background comparison can produce artifacts on matching colors or translucent details. GPU and third-party renderers should be tested individually.
- A dragged icon outside the scaled panel can still be drawn at its native location. Alt-drag widget management and other input-transforming plugins need individual testing.
- Submenu-heavy plugins and menus near screen edges need live checks. Menus retain native submenu layout, with their pixels translated alongside the root.

After changes, verify both resizable layouts, item actions and dragging, spells/prayers/equipment, tab buttons, minimap walking/orbs, exposed original locations, context-menu selection/scrolling, window/layout changes, login/logout, disable/re-enable, and operation with built-in Stretched Mode. For the layout fix, keep non-default modern tab-bar settings, switch to classic, enlarge the inventory frame, and confirm no modern panel/bars appear. Switch back to modern and repeat. Compare FPS with each region separately and together, holding the scene and other settings constant.

## License

Original plugin code is licensed under the full **BSD 2-Clause License**, attributed to **GimmitheBong**. Its text matches the Plugin Hub's license template except for copyright attribution. It is available in [`LICENSE`](LICENSE) and packaged in the plugin JAR as `META-INF/LICENSE`.

The Gradle wrapper keeps its original Apache notices and license. See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) and [`licenses/Apache-2.0.txt`](licenses/Apache-2.0.txt).
