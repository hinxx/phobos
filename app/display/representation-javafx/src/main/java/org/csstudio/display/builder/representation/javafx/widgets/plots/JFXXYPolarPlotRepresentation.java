/*******************************************************************************
 * Copyright (c) 2026 European Spallation Source ERIC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.csstudio.display.builder.representation.javafx.widgets.plots;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.csstudio.display.builder.model.DirtyFlag;
import org.csstudio.display.builder.model.UntypedWidgetPropertyListener;
import org.csstudio.display.builder.model.persist.NamedWidgetColors;
import org.csstudio.display.builder.model.persist.WidgetColorService;
import org.csstudio.display.builder.model.WidgetProperty;
import org.csstudio.display.builder.model.WidgetPropertyListener;
import org.csstudio.display.builder.model.widgets.plots.PolarPlotPoint;
import org.csstudio.display.builder.model.widgets.plots.PolarPlotWidget;
import org.csstudio.display.builder.representation.Preferences;
import org.csstudio.display.builder.representation.javafx.JFXUtil;
import org.csstudio.display.builder.representation.javafx.widgets.RegionBaseRepresentation;
import org.csstudio.javafx.rtplot.Activator;
import org.phoebus.ui.javafx.UpdateThrottle;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

/** JavaFX representation for {@link PolarPlotWidget}.
 *  @author Codex
 */
@SuppressWarnings("nls")
public class JFXXYPolarPlotRepresentation extends RegionBaseRepresentation<Pane, PolarPlotWidget>
{
    private static final double ALARM_RADIUS_FRACTION = 0.8;

    private final DirtyFlag dirty_size = new DirtyFlag();
    private final DirtyFlag dirty_config = new DirtyFlag();
    private final DirtyFlag dirty_points = new DirtyFlag();

    private final UntypedWidgetPropertyListener size_listener = this::markSizeDirty;
    private final UntypedWidgetPropertyListener config_listener = this::markConfigDirty;
    private final WidgetPropertyListener<List<PolarPlotPoint>> points_listener = this::pointsChanged;

    private final UpdateThrottle point_throttle =
        new UpdateThrottle(Preferences.plot_update_delay, TimeUnit.MILLISECONDS, this::markPointsDirty, Activator.thread_pool);

    private Canvas canvas;

    @Override
    protected Pane createJFXNode() throws Exception
    {
        canvas = new Canvas();
        canvas.setManaged(false);
        return new Pane(canvas);
    }

    @Override
    protected void registerListeners()
    {
        super.registerListeners();
        model_widget.propWidth().addUntypedPropertyListener(size_listener);
        model_widget.propHeight().addUntypedPropertyListener(size_listener);
        model_widget.propBackground().addUntypedPropertyListener(config_listener);
        model_widget.propForeground().addUntypedPropertyListener(config_listener);
        model_widget.propTraceColor().addUntypedPropertyListener(config_listener);
        model_widget.propMaxRadius().addUntypedPropertyListener(config_listener);
        model_widget.runtimePropPoints().addPropertyListener(points_listener);
    }

    @Override
    protected void unregisterListeners()
    {
        model_widget.propWidth().removePropertyListener(size_listener);
        model_widget.propHeight().removePropertyListener(size_listener);
        model_widget.propBackground().removePropertyListener(config_listener);
        model_widget.propForeground().removePropertyListener(config_listener);
        model_widget.propTraceColor().removePropertyListener(config_listener);
        model_widget.propMaxRadius().removePropertyListener(config_listener);
        model_widget.runtimePropPoints().removePropertyListener(points_listener);
        super.unregisterListeners();
    }

    private void markSizeDirty(final WidgetProperty<?> property, final Object old_value, final Object new_value)
    {
        dirty_size.mark();
        toolkit.scheduleUpdate(this);
    }

    private void markConfigDirty(final WidgetProperty<?> property, final Object old_value, final Object new_value)
    {
        dirty_config.mark();
        toolkit.scheduleUpdate(this);
    }

    private void pointsChanged(final WidgetProperty<List<PolarPlotPoint>> property, final List<PolarPlotPoint> old_value, final List<PolarPlotPoint> new_value)
    {
        point_throttle.trigger();
    }

    private void markPointsDirty()
    {
        dirty_points.mark();
        toolkit.scheduleUpdate(this);
    }

    @Override
    public void updateChanges()
    {
        super.updateChanges();

        final boolean size_changed = dirty_size.checkAndClear();
        final boolean config_changed = dirty_config.checkAndClear();
        final boolean points_changed = dirty_points.checkAndClear();

        if (size_changed)
        {
            final double width = model_widget.propWidth().getValue();
            final double height = model_widget.propHeight().getValue();
            jfx_node.setPrefSize(width, height);
            canvas.setWidth(width);
            canvas.setHeight(height);
        }

        if (config_changed)
        {
            jfx_node.setBackground(new Background(new BackgroundFill(JFXUtil.convert(model_widget.propBackground().getValue()), CornerRadii.EMPTY, null)));
        }

        if (size_changed || config_changed || points_changed)
            redraw();
    }

    private void redraw()
    {
        final GraphicsContext gc = canvas.getGraphicsContext2D();
        final double width = canvas.getWidth();
        final double height = canvas.getHeight();

        gc.setFill(JFXUtil.convert(model_widget.propBackground().getValue()));
        gc.fillRect(0, 0, width, height);

        if (width <= 1 || height <= 1)
            return;

        final Color foreground = JFXUtil.convert(model_widget.propForeground().getValue());
        final Color trace = JFXUtil.convert(model_widget.propTraceColor().getValue());
        final Color grid = deriveGridColor(foreground, JFXUtil.convert(model_widget.propBackground().getValue()));

        final double center_x = width / 2.0;
        final double center_y = height / 2.0;
        final double plot_radius = Math.max(1.0, Math.min(width, height) / 2.0 - 12.0);
        final double max_radius = Math.max(1e-9, model_widget.propMaxRadius().getValue());

        drawGrid(gc, center_x, center_y, plot_radius, grid, foreground, max_radius);
        drawTrace(gc, center_x, center_y, plot_radius, max_radius, trace);
    }

    private void drawGrid(final GraphicsContext gc, final double center_x, final double center_y,
                          final double plot_radius, final Color grid, final Color foreground, final double max_radius)
    {
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.setStroke(grid);
        gc.setLineWidth(1.0);

        for (int i = 1; i <= 4; ++i)
        {
            final double radius = plot_radius * i / 4.0;
            gc.strokeOval(center_x - radius, center_y - radius, radius * 2.0, radius * 2.0);
        }

        for (int i = 0; i < 12; ++i)
        {
            final double theta = i * (Math.PI / 6.0);
            final double x = center_x + plot_radius * Math.cos(theta);
            final double y = center_y - plot_radius * Math.sin(theta);
            gc.strokeLine(center_x, center_y, x, y);
        }

        gc.setFill(foreground);
        gc.fillText(formatRadius(max_radius), center_x, center_y - plot_radius - 8.0);
    }

    private void drawTrace(final GraphicsContext gc, final double center_x, final double center_y,
                           final double plot_radius, final double max_radius, final Color trace)
    {
        final List<PolarPlotPoint> points = model_widget.runtimePropPoints().getValue();
        final int size = points.size();
        if (size <= 0)
            return;

        final Color alarm = JFXUtil.convert(WidgetColorService.getColor(NamedWidgetColors.ALARM_MAJOR));
        for (int i = 0; i < size; ++i)
        {
            final PolarPlotPoint point = points.get(i);
            final double normalized = Math.max(0.0, Math.min(point.getRadius() / max_radius, 1.0));
            final double distance = normalized * plot_radius;
            final double x = center_x + distance * Math.cos(point.getAngle());
            final double y = center_y - distance * Math.sin(point.getAngle());
            final double alpha = 0.15 + 0.85 * (i + 1.0) / size;
            final Color color = applyAgeAlpha(blendedTraceColor(trace, alarm, normalized), alpha);

            gc.setFill(color);
            gc.fillOval(x - 2.5, y - 2.5, 5.0, 5.0);
        }
    }

    private Color blendedTraceColor(final Color trace, final Color alarm, final double normalized_radius)
    {
        final double blend = Math.max(0.0, (normalized_radius - ALARM_RADIUS_FRACTION) / (1.0 - ALARM_RADIUS_FRACTION));
        return new Color(interpolate(trace.getRed(), alarm.getRed(), blend),
                         interpolate(trace.getGreen(), alarm.getGreen(), blend),
                         interpolate(trace.getBlue(), alarm.getBlue(), blend),
                         1.0);
    }

    private Color applyAgeAlpha(final Color color, final double alpha)
    {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private double interpolate(final double start, final double end, final double factor)
    {
        return start + (end - start) * factor;
    }

    private Color deriveGridColor(final Color foreground, final Color background)
    {
        final double brightness = (background.getRed() + background.getGreen() + background.getBlue()) / 3.0;
        final double alpha = brightness < 0.5 ? 0.4 : 0.25;
        return new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), alpha);
    }

    private String formatRadius(final double max_radius)
    {
        if (Math.rint(max_radius) == max_radius)
            return Long.toString(Math.round(max_radius));
        return Double.toString(max_radius);
    }

    @Override
    public void dispose()
    {
        point_throttle.dispose();
        super.dispose();
    }
}
