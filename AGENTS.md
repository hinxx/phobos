# AGENTS.md

## Objective
Implement a "Polar Plot" widget in the Phoebus Display Builder.

The implementation must follow the standard architectural patterns used by existing complex widgets, especially `XYPlot`.

## Required reference implementation
Before making changes, analyze these classes in the `app/display` module and use them as the primary blueprint for structure, naming, property patterns, runtime behavior, and thread management:

- Model: `org.csstudio.display.builder.model.widgets.XYPlotWidget`
- Runtime: `org.csstudio.display.builder.runtime.widgets.XYPlotRuntime`
- Representation: `org.csstudio.display.builder.representation.javafx.widgets.JFXXYPlotRepresentation`

## Model requirements
Create `PolarPlotWidget` extending `Widget`.

Follow the same property and serialization patterns used by `XYPlotWidget`, especially how it handles multi-property widget configuration.

Implement at minimum these properties:
- `radius_pv` using `RuntimePV`
- `angle_pv` using `RuntimePV`
- `buffer_size` as integer
- `trace_color` as color
- `max_radius` as double

The implementation should also support radius-based alarm coloring for points farther from the origin.
This may reuse `max_radius` as the threshold reference or introduce an additional idiomatic widget property if needed by the codebase.

Requirements:
- Register all properties in the constructor
- Ensure they serialize correctly to XML
- Match existing Display Builder naming and property conventions
- Prefer existing `WidgetProperty` patterns over inventing new ones

## Runtime requirements
Create `PolarPlotRuntime` extending `RuntimeWidget`.

### PV handling
Use the same approach as `XYPlotRuntime` for attaching listeners to multiple PVs.

Do not manage raw CA/PVA channels directly.
Always use the `RuntimePV` abstraction from the Display Builder framework.

### Synchronization
Maintain current radius and current angle state.

Use a thread-safe fixed-size buffer to store recent polar points.
Default behavior should support the last 100 points unless overridden by `buffer_size`.

When PVs update:
- update the latest radius or angle state
- append a new point only when a complete `(r, theta)` pair is available, or based on the same kind of trigger discipline used by the reference implementation

### Redraw throttling
Do not redraw the UI on every PV event if updates are frequent.

Use the same dirty-flag or throttled-update pattern used by `XYPlotRuntime`.

## JavaFX representation requirements
Create `JFXXYPolarPlotRepresentation` extending `JFXRepresentation`.

A JavaFX `Canvas` is acceptable for the central plot area if it simplifies efficient repainting and fading trails.

### Coordinate system
- Use the widget center as the origin
- Convert polar to Cartesian using:
  - `x = r * cos(theta)`
  - `y = r * sin(theta)`

### Trail rendering
Render buffered points with fading based on age.

Use decreasing opacity for older points.

Render only discrete points.
Do not draw connecting lines between successive points.

As points move farther away from the origin, change their color to indicate entry into an undesired/alarm region.
The visual alarm cue should be based on radius, i.e. distance from `(0,0)`, not on point age.

### Grid rendering
Do not use a rectangular grid.
Render:
- concentric circles
- radial spokes

Grid, background, and related styling should respect Phoebus light/dark theme behavior in the same spirit as `XYPlot`.

## Integration checklist
Complete all required integration work:
- register the widget in `WidgetDescriptorFactory`
- ensure it appears in the editor palette
- ensure properties are editable in the property sheet
- ensure XML serialization/deserialization works
- ensure theme-aware colors behave correctly

## Implementation guidance
Prefer minimal, idiomatic changes that fit the existing codebase.

Do not introduce new architectural patterns when an `XYPlot` pattern already exists.

If behavior is unclear, follow the nearest existing Phoebus widget convention rather than inventing a new one.

## Validation
Before finishing:
- verify the widget builds
- verify it appears in the editor palette
- verify the property sheet works
- verify PV hookup behavior is reasonable
- verify no obvious threading or redraw issues were introduced

## Implementation notes
The final implementation followed the intent of the instructions, but some concrete details were adapted to match the actual Phoebus codebase:

- The reference runtime class in this repo is `org.csstudio.display.builder.runtime.internal.XYPlotWidgetRuntime`, not `org.csstudio.display.builder.runtime.widgets.XYPlotRuntime`.
- The reference JavaFX class in this repo is `org.csstudio.display.builder.representation.javafx.widgets.plots.XYPlotRepresentation`, not `JFXXYPlotRepresentation`.
- `PolarPlotWidget` was implemented by extending `VisibleWidget`, mirroring the existing plot-widget pattern in this repository instead of extending bare `Widget`.
- `radius_pv` and `angle_pv` were implemented as serializable PV-name `WidgetProperty<String>` properties. `RuntimePV` is only used inside the runtime layer, which is how Phoebus handles PV-backed widget properties elsewhere.
- Additional runtime-only properties were added for `radius_value`, `angle_value`, and the buffered point list so the JavaFX representation can repaint from model/runtime state without directly owning PV connections.
- The runtime class extends `WidgetRuntime<PolarPlotWidget>`, which is the runtime base class used by this codebase.
- The fixed-size polar point buffer is maintained in the runtime and published to the representation through a runtime property. Rendering throttling is handled in the JavaFX representation with `DirtyFlag` plus `UpdateThrottle`, which is where the comparable throttling pattern lives for `XYPlot` in this repository.
- Integration was completed via `BaseWidgetsService`, `BaseWidgetRuntimes`, and `BaseWidgetRepresentations`, which are the actual registration points used here instead of a separate `WidgetDescriptorFactory`.
- The widget currently reuses `/icons/xyplot.png` for palette/editor visibility. No dedicated Polar Plot icon was added.
- The representation interprets `theta` in radians because it directly applies `Math.cos(theta)` and `Math.sin(theta)`.
- Validation completed by compiling the affected modules successfully. Palette/property-sheet/runtime behavior was confirmed manually in the running application after the build.
