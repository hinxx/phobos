/*******************************************************************************
 * Copyright (c) 2026 European Spallation Source ERIC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.csstudio.display.builder.runtime.internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.logging.Level;

import org.csstudio.display.builder.model.WidgetPropertyListener;
import org.csstudio.display.builder.model.util.VTypeUtil;
import org.csstudio.display.builder.model.widgets.plots.PolarPlotPoint;
import org.csstudio.display.builder.model.widgets.plots.PolarPlotWidget;
import org.csstudio.display.builder.runtime.PVNameToValueBinding;
import org.csstudio.display.builder.runtime.WidgetRuntime;
import org.epics.vtype.VType;

/** Runtime for the PolarPlotWidget.
 *  @author Codex
 */
@SuppressWarnings("nls")
public class PolarPlotRuntime extends WidgetRuntime<PolarPlotWidget>
{
    private final Object buffer_lock = new Object();
    private final Deque<PolarPlotPoint> points = new ArrayDeque<>();
    private final List<PVNameToValueBinding> bindings = new ArrayList<>(2);

    private volatile Double latest_radius = null;
    private volatile Double latest_angle = null;
    private volatile boolean has_radius = false;
    private volatile boolean has_angle = false;

    private final WidgetPropertyListener<VType> radius_listener = (property, old_value, new_value) -> updateValue(true, new_value);
    private final WidgetPropertyListener<VType> angle_listener = (property, old_value, new_value) -> updateValue(false, new_value);
    private final WidgetPropertyListener<Integer> buffer_listener = (property, old_value, new_value) -> trimBuffer(Math.max(1, new_value));

    @Override
    public void start()
    {
        super.start();
        bindings.add(new PVNameToValueBinding(this, widget.propRadiusPV(), widget.runtimePropRadiusValue()));
        bindings.add(new PVNameToValueBinding(this, widget.propAnglePV(), widget.runtimePropAngleValue()));

        widget.runtimePropRadiusValue().addPropertyListener(radius_listener);
        widget.runtimePropAngleValue().addPropertyListener(angle_listener);
        widget.propBufferSize().addPropertyListener(buffer_listener);

        updateValue(true, widget.runtimePropRadiusValue().getValue());
        updateValue(false, widget.runtimePropAngleValue().getValue());
        trimBuffer(widget.propBufferSize().getValue());
        publishSnapshot();
    }

    private void updateValue(final boolean radius_update, final VType value)
    {
        final Double numeric = toDouble(value);
        if (numeric == null)
            return;

        if (radius_update)
        {
            latest_radius = numeric;
            has_radius = true;
        }
        else
        {
            latest_angle = numeric;
            has_angle = true;
        }

        if (has_radius && has_angle)
            appendPoint(latest_radius, latest_angle);
    }

    private Double toDouble(final VType value)
    {
        if (value == null)
            return null;
        try
        {
            return VTypeUtil.getValueNumber(value).doubleValue();
        }
        catch (Exception ex)
        {
            logger.log(Level.FINER, "Ignoring non-numeric polar plot update", ex);
            return null;
        }
    }

    private void appendPoint(final double radius, final double angle)
    {
        final int limit = Math.max(1, widget.propBufferSize().getValue());
        synchronized (buffer_lock)
        {
            points.addLast(new PolarPlotPoint(radius, angle));
            while (points.size() > limit)
                points.removeFirst();
        }
        publishSnapshot();
    }

    private void trimBuffer(final int limit)
    {
        synchronized (buffer_lock)
        {
            while (points.size() > limit)
                points.removeFirst();
        }
        publishSnapshot();
    }

    private void publishSnapshot()
    {
        final List<PolarPlotPoint> snapshot;
        synchronized (buffer_lock)
        {
            snapshot = List.copyOf(points);
        }
        widget.runtimePropPoints().setValue(snapshot);
    }

    @Override
    public void stop()
    {
        widget.runtimePropRadiusValue().removePropertyListener(radius_listener);
        widget.runtimePropAngleValue().removePropertyListener(angle_listener);
        widget.propBufferSize().removePropertyListener(buffer_listener);

        for (PVNameToValueBinding binding : bindings)
            binding.dispose();
        bindings.clear();

        synchronized (buffer_lock)
        {
            points.clear();
        }
        widget.runtimePropPoints().setValue(List.of());
        super.stop();
    }
}
