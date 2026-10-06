# Better Stretched Mode

An **experimental RuneLite plugin** for independently resizing the minimap cluster and the two tab button bars in modern layout. The minimap cluster includes the map, compass, health/prayer/run/special-attack orbs, and nearby buttons.

Stretched Mode scales the entire game image. This plugin captures individual interface containers, redraws them at configurable sizes, and translates mouse events back into their native coordinates. The contents of the inventory, spellbook, other side panels, and chat keep their normal sizes.

## Version 0.3 changes

- Renamed **Minimap Resize** to **Better Stretched Mode**. Existing size, position, enablement and filtering preferences use the same internal keys.
- Replaced the Smooth scaling checkbox with a **Scaling filter** dropdown: Nearest neighbor, Bilinear, Bicubic, and Sharp Bicubic.
- Added **Sharpening strength** for Sharp Bicubic. Try **Sharp Bicubic at 40%** for smoother scaling with more defined text/icons, then adjust to taste.
- The old smooth preference maps to Bilinear; the old non-smooth preference maps to Nearest neighbor until you select another filter.

### Previous version's click-through fix

- Removed the off-screen mouse redirection that blocked the old minimap rectangle when shrinking or moving it.
- Exposed original locations and transparent gaps now pass through at their actual screen coordinates. The native controls' click blockers, menu operations and special minimap input handler are temporarily disabled when they are not the intended scaled target, so invisible controls cannot steal those clicks.
- Added separate scaling and position controls for both modern-layout tab bars.
- The input changes preserve widget visibility and timer/stat listeners, allowing orb values to continue updating. Original properties are restored before scripts/rendering and on shutdown or a layout change.

**Restart the development client through `run-plugin.bat` to load this version.** Toggling a plugin in an already-running client does not reload its compiled classes.

## Try it

1. Double-click **`run-plugin.bat`** in this folder. This starts a separate development RuneLite client with the plugin loaded.
2. In the game's display settings, choose **Resizable - Classic layout** or **Resizable - Modern layout**.
3. Search RuneLite's plugin list for **Better Stretched Mode** and enable it.
4. Open its settings. **Minimap size** defaults to **150%**; try 125%, 150%, or 200%.
5. For the tab bars, choose **Resizable - Modern layout**, expand **Modern layout tab bars** in the plugin settings, and adjust **Upper tab bar size** and **Lower tab bar size** independently. Both default to 100%.
6. Leave **Stretched Mode off** if you want the rest of the interface at its normal size.

The launcher selects one of the installed JDK 17 paths on this machine. On another machine, set `JAVA_HOME` to a JDK 17 installation, or set `MINIMAP_JAVA_HOME` to explicitly override the launcher's selection. The first run requires internet access for Gradle and RuneLite dependencies.

This is a development plugin, not an installed Plugin Hub entry. The regular RuneLite launcher will not discover it just by placing its JAR in a folder.

## Settings

| Setting | Effect |
| --- | --- |
| Minimap size | 50–250% of the native minimap cluster; 100% with zero offsets is an exact bypass. |
| Move left | Offset from the original cluster's right edge, in game pixels. |
| Move down | Offset from the original cluster's top edge, in game pixels. |
| Scaling filter | Nearest neighbor, Bilinear, Bicubic, or Sharp Bicubic; applies to the minimap and both scaled tab bars. |
| Sharpening strength | 0–100%, used by Sharp Bicubic only; defaults to 40%. |
| Upper tab bar size | 50–250% for the movable bar containing combat, inventory, prayer, spellbook, etc. Modern layout only. |
| Upper bar: move left / up | Reposition the upper/movable bar in game pixels. |
| Lower tab bar size | 50–250% for the static bar containing social/settings and other tabs. Modern layout only. |
| Lower bar: move left / up | Reposition the lower/static bar in game pixels. |

The minimap keeps its original top-right anchor. Each tab bar keeps its original bottom-right anchor and follows the game's placement of that bar (the two bars may be side-by-side in wider layouts). Size and position are clamped to the game canvas. Large scaled regions can cover other interface elements; increase the window size or lower the scale. Scaled regions are drawn above the ordinary interface, below right-click menus, and overlapping scaled regions route input to the topmost one.

100% with zero offsets bypasses scaling and input changes for that individual region.

## Choosing a filter

| Filter | Appearance |
| --- | --- |
| Nearest neighbor (pixel sharp) | Crisp original pixels, but more blocky/jagged at fractional sizes. |
| Bilinear (smooth) | The previous Smooth scaling option. Softer edges, often noticeably blurry on text. |
| Bicubic | A wider interpolation filter that can retain detail better than Bilinear, depending on size and artwork. |
| Sharp Bicubic | Bicubic followed by an adjustable local-contrast sharpening pass; recommended starting point for less blur. |

Start with **Sharp Bicubic / 40%**. Try 60% if it is still too soft, or 20% if edges look harsh. At 0%, Sharp Bicubic is identical to plain Bicubic. Very high strength may create bright/dark edge halos, so more is not always better. Filtering cannot recover detail that the original small sprites did not contain.

The sharpening pass preserves transparency and does not change click hitboxes. Image buffers are reused across frames. It costs more CPU work than the plain filters; if needed, choose Bicubic or reduce the scaled size. When an image stays at its original dimensions (including position-only changes), filtering/sharpening is bypassed to preserve the original pixels.

## Current status and limitations

- **Compiled against RuneLite 1.13.1; all 32 automated tests pass.** The minimap rendering, click-through fix and tab bars were reported working in-game by the user. The new filters need live visual comparison; tests cover filter contrast/transparency, input routing, native property restoration, layout restrictions, and rendering coordination.
- **Resizable layouts only.** Minimap scaling supports classic and modern; tab bar scaling supports modern only. Fixed mode, the login screen, and unavailable/hidden containers are bypassed.
- The native widget dimensions stay unchanged. This is a rendered-image scaling prototype, not a new native widget-scaling feature.
- **Right-click menus stay normal-sized and open near the original widget location.** Input translation pauses while a menu is open so its entries can be selected normally.
- Separate RuneLite minimap overlays (for example, some tile markers) are not scaled and may be covered or misaligned. Other overlays or interfaces overlapping the captured source region may be included in the cutout. Start with those disabled for the first visual check.
- The software renderer has no separate minimap alpha layer. This implementation compares the before/after images to remove the scene background. The map interior is kept opaque; matching colors or translucent details elsewhere can still produce edge artifacts. GPU and third-party renderers also need live checks.
- Transparent gaps and exposed original areas pass through to whatever is actually behind them. Other visible interfaces, such as an open inventory panel, still intercept clicks normally.
- Use the plugin's position settings for initial testing. RuneLite's Alt-drag widget controls and other input-transforming plugins have not been verified with it.
- Stretched Mode's coordinate conversion runs first; this plugin's conversion is additional. If Stretched Mode is enabled, it still scales the rest of the game as usual.

Disable the plugin to return to normal rendering. It does not persist changes to the native widgets or Stretched Mode configuration.

## Build and test

From PowerShell in this folder:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17.0.19+10'
.\gradlew.bat build
```

Artifacts:

- Plugin JAR: `build\libs\better-stretched-mode-0.3.0.jar`
- Test report: `build\reports\tests\test\index.html`

Launch from a terminal:

```powershell
.\run-plugin.bat
```

The plugin uses RuneLite's latest release by default, as recommended for Plugin Hub submissions. To explicitly build against an older RuneLite version for compatibility checks:

```powershell
.\gradlew.bat build -PruneliteVersion=1.12.29
```

## In-game verification

1. In both resizable layouts, compare 100%, 150%, and 200%. Check that the full minimap/orb cluster appears and the inventory/chat remain their original size.
2. Click several minimap destinations, the compass, run/prayer orbs, and world-map button. Check hover states and mouse-wheel minimap zoom.
3. Open and select right-click menu entries. Menus should use normal coordinates while open.
4. At 50% minimap size, click and right-click NPCs/objects in the exposed original minimap area, including transparent corners. Those scene interactions should work at the cursor's actual location.
5. In modern layout, shrink each tab bar to 50–75%. Verify every tab opens its usual panel, then close the panel and click the scene exposed at each bar's old location. Also test offsets and both bars together.
6. Check that health, prayer and run energy still update while the cursor stays away from the minimap; test tab hotkeys as well as clicks.
7. Resize the window, switch layout, log out/in, and disable/re-enable the plugin. Look for stale images, native input flags or mouse offsets.
8. Compare software and GPU rendering, then test with Stretched Mode and other minimap overlays individually.
9. Compare all four filters at your preferred scale. For Sharp Bicubic, compare 0%, 40% and 60% strength on orb numbers, compass edges and tab icons. Check that transparent borders remain clean and old locations still click through.

## Implementation

- `MinimapResizePlugin.java`: lifecycle, layout detection, render coordination.
- `MinimapCaptureOverlay.java`: snapshot beneath widgets.
- `WidgetRegion.java`: minimap and modern upper/lower tab-container definitions.
- `MinimapScaleOverlay.java`: extract each container at its native layer hook.
- `ScaledWidgetsOverlay.java`: draw the scaled cutouts after extraction, before right-click menus.
- `MinimapCompositor.java`: cutout extraction and original-background restoration.
- `ScalingFilter.java`, `WidgetImageScaler.java`: filtering modes, reusable premultiplied-alpha image buffers, and transparency-aware sharpening.
- `MinimapTransform.java`, `MinimapInputFrame.java`, `InputRoute.java`, `MinimapMouseListener.java`: scale geometry, visible-pixel hit testing, and input routing.
- `WidgetInputGate.java`, `InputGateOverlay.java`: temporary native input-property changes and restoration without stopping widget stat/timer listeners.
- `src/test/java`: automated checks and the development client launcher.

The RuneLite source in `C:\Projects\runelite` was used to inspect the built-in Stretched Mode plugin, minimap containers, and rendering/input APIs. This project uses only RuneLite's runtime dependencies and is marked `build=standard` for the Plugin Hub.
