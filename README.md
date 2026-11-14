# MCStats+

A client-only Fabric performance HUD for **Minecraft 26.3**, with independent glass widgets and a rendering-smoothness score called **FrameHealth**. No server installation is required.

## Build and test

Use **JDK 25** and the committed **Gradle 9.8.2** wrapper:

```sh
./gradlew build
./gradlew coreTest
./gradlew coreBenchmark
./gradlew runClient
```

The installable jar is `build/libs/mcstats-1.0.0.jar`. Install it with Fabric Loader **0.20.1** and Fabric API **0.166.2+26.3**. The target versions have not been substituted. Minecraft 26.3 uses official unobfuscated names; no additional mappings dependency is needed. The GUI uses `GuiGraphicsExtractor` and Fabric's HUD element registry.

`build` runs 15 dependency-free core regression cases through `check`. `coreBenchmark` measures ring-buffer writes and scoring only; it is **not** a Minecraft FPS-overhead benchmark. Behind an HTTPS proxy, Java needs explicit HTTP/HTTPS proxy host/port properties and the environment's trusted CA store. Keep TLS and checksum verification enabled.

## Controls

The title screen also has an **MCStats+** button to configure the HUD before entering a world.

All bindings can be changed in Minecraft's Controls menu under **MCStats+**:

| Default | Action |
| --- | --- |
| F7 | Toggle HUD and suspend its monitoring when no dashboard is open |
| F8 | Open the dashboard |
| F9 | Open HUD Studio |
| Escape | Close and save |

The default HUD is a single FrameHealth pill. For a tiny **FPS-only** HUD, open Profiles, select **FPS only**, and Apply preset. Minimal, Essential, Streamer, Performance and Full Dashboard presets are also included.

### HUD Studio

Select a widget, drag it to move, or drag its lower-right handle to resize. Arrow keys nudge one GUI pixel. Snap aligns to screen edges and center lines, with guides while dragging. Coordinates are fractions of the available screen area: left/right and top/bottom anchoring survive resolution and GUI-scale changes. Widgets assigned the same nonzero group move together; group 0 is independent.

Add up to 32 widgets, remove any individually, change their metric, and choose text, pill, card, graph or gauge presentation. Graphs are offered for FPS, frame time, heap and health; the gauge is for FrameHealth. Appearance controls include four bundled DejaVu TrueType fonts (sans, medium, mono and serif), scale, padding, corner radius, opacity and border. Copy a six-digit hex color and use **Widget accent** to color the selection.

Appearance contains dark/light/custom themes, custom background/text/accent colors, optional reflections, reduced motion, FPS target, and Eco/Balanced/Responsive polling. The HUD uses a translucent fallback rather than a gameplay framebuffer blur. Optional blur applies to the expanded dashboard only.

### Profiles

Five on-disk slots can be saved and loaded. Clipboard export/import carries the complete layout and appearance. Imports are versioned, bounded to 64 KiB, and validated before replacing the current layout. Saves use an atomic replacement where supported. Files live in `config/mcstats/`.

Exported Java-properties profiles also expose exact numeric settings: `targetFps`, `widget.N.x/y`, `scale`, `width/height`, `padding`, `radius`, `opacity`, `font` (0–3), `accent` (decimal RGB), `group`, `border`, and the global `excellentColor`, `goodColor`, `fairColor`, `poorColor`, `background`, `textColor`. Use clipboard import to apply edits. Invalid values are rejected rather than silently converted.

## What is measured

- Frame intervals at the beginning of each client `runTick`; these include the prior frame's CPU work, presentation and limiter wait. They are not GPU execution times.
- Minecraft's current FPS counter, exponentially smoothed reciprocal frame time, rolling average FPS (`1000 / mean frame milliseconds`), and 1% low (`1000 / mean of the slowest ceil(1%) durations`).
- Heap used/max and cumulative JVM garbage-collection count/time.
- Process/system CPU load where the platform MXBean supplies it.
- Player-list latency where available, and configured render distance.
- GPU load, VRAM and temperature are **unavailable**. No hardware telemetry is invented.

The 30-second ring has a hard limit of 65,536 frames. Above roughly 2,184 FPS it covers less than 30 seconds; the dashboard reports actual sample count and observed duration. Collection resets across pauses and world/menu boundaries. History contains up to 120 actual scoring samples; gaps are not interpolated into fabricated telemetry. FrameHealth runs once per second. JVM polling runs on one daemon worker every 2 s / 1 s / 500 ms for Eco / Balanced / Responsive. No blocking hardware query runs on the render thread. Hidden/disabled HUDs stop unnecessary collection, except when the dashboard is open.

## FrameHealth formula

This is an **initial model requiring calibration**, not a validated benchmark. Let `m` be the median frame time in milliseconds and `T` the observed window duration in seconds:

| Component | Health in [0, 1] | Weight |
| --- | --- | --- |
| Consistency | `clamp(1 - population standard deviation / mean / 0.5)` over ordinary frames | 35% |
| Stutters | `clamp(1 - stutter count / T / 2)` | 30% |
| FPS target | `clamp((1000 / m) / targetFPS)` | 25% |
| Severe stalls | `clamp(1 - severe stall milliseconds / (T * 1000) / 0.1)` | 10% |

An ordinary frame is below `max(50 ms, 2m)`. A stutter lies between that threshold and `max(250 ms, 5m)`. A severe stall is at or above the latter. These event classes are disjoint; stutters/stalls are excluded from ordinary-frame variance. Target attainment uses the median to avoid counting isolated stalls again. The weighted sum is multiplied by 100. This favors stable delivery; because attainment contributes only 25 points, a consistently low frame rate can still receive a high score. Choose an appropriate target and read the component breakdown rather than treating the score as a replacement for FPS.

Initial categories: Excellent ≥90, Good ≥75, Fair ≥50, Poor <50. After initialization, a two-point hysteresis band around each threshold prevents label flutter. Scores remain unavailable until at least one second and two frames have been observed. Network latency is excluded. Displayed values animate toward measurements; scoring and graph samples use raw data.

## Architecture and current limits

`metrics/` owns collection, bounded history, telemetry and scoring; `animation/` contains an exact critically damped spring; `config/` handles profiles; `ui/` contains native glass geometry, HUD rendering, dashboard and editor. Spring interpolation drives panel opening/closing, widget position/size/opacity, score, status color and button hover, with an immediate reduced-motion mode. Text labels are cached between presentation updates. Fonts include their own redistribution license in the font resource directory; the project code remains CC0.

This implementation prioritizes the working core. Graphs scroll measured samples without inventing interpolated values. Full widget-to-dashboard morphing, arbitrary widget-to-widget magnetic guides, per-widget background blur, and optional native GPU integrations remain follow-up work. Sodium/Iris compatibility and the <1% total FPS-overhead goal require gameplay benchmarking on supported hardware; neither is assumed from a successful build. No heavyweight UI framework or new runtime dependency is introduced.
