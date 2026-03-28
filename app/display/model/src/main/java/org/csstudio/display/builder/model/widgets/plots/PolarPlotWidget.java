/*******************************************************************************
 * Copyright (c) 2026 European Spallation Source ERIC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.csstudio.display.builder.model.widgets.plots;

import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.propBackgroundColor;
import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.propForegroundColor;

import java.util.Collections;
import java.util.List;

import org.csstudio.display.builder.model.Messages;
import org.csstudio.display.builder.model.RuntimeWidgetProperty;
import org.csstudio.display.builder.model.Widget;
import org.csstudio.display.builder.model.WidgetCategory;
import org.csstudio.display.builder.model.WidgetDescriptor;
import org.csstudio.display.builder.model.WidgetProperty;
import org.csstudio.display.builder.model.WidgetPropertyCategory;
import org.csstudio.display.builder.model.WidgetPropertyDescriptor;
import org.csstudio.display.builder.model.persist.NamedWidgetColors;
import org.csstudio.display.builder.model.persist.WidgetColorService;
import org.csstudio.display.builder.model.properties.CommonWidgetProperties;
import org.csstudio.display.builder.model.properties.WidgetColor;
import org.csstudio.display.builder.model.widgets.VisibleWidget;
import org.epics.vtype.VType;

/** Widget that displays incoming radius/angle samples on a polar plot.
 *  @author Codex
 */
@SuppressWarnings("nls")
public class PolarPlotWidget extends VisibleWidget
{
    /** Widget descriptor */
    public static final WidgetDescriptor WIDGET_DESCRIPTOR =
        new WidgetDescriptor("polarplot", WidgetCategory.PLOT,
                             Messages.PolarPlot_Name,
                             "/icons/xyplot.png",
                             Messages.PolarPlot_Description)
        {
            @Override
            public Widget createWidget()
            {
                return new PolarPlotWidget();
            }
        };

    private static final WidgetPropertyDescriptor<String> propRadiusPV =
        CommonWidgetProperties.newPVNamePropertyDescriptor(WidgetPropertyCategory.BEHAVIOR, "radius_pv", Messages.PolarPlot_RadiusPV);

    private static final WidgetPropertyDescriptor<String> propAnglePV =
        CommonWidgetProperties.newPVNamePropertyDescriptor(WidgetPropertyCategory.BEHAVIOR, "angle_pv", Messages.PolarPlot_AnglePV);

    private static final WidgetPropertyDescriptor<Integer> propBufferSize =
        CommonWidgetProperties.newIntegerPropertyDescriptor(WidgetPropertyCategory.BEHAVIOR, "buffer_size", Messages.PolarPlot_BufferSize, 1, Integer.MAX_VALUE);

    private static final WidgetPropertyDescriptor<WidgetColor> propTraceColor =
        CommonWidgetProperties.newColorPropertyDescriptor(WidgetPropertyCategory.DISPLAY, "trace_color", Messages.PolarPlot_TraceColor);

    private static final WidgetPropertyDescriptor<Double> propMaxRadius =
        CommonWidgetProperties.newDoublePropertyDescriptor(WidgetPropertyCategory.BEHAVIOR, "max_radius", Messages.PolarPlot_MaxRadius);

    private static final WidgetPropertyDescriptor<VType> runtimePropRadiusValue =
        CommonWidgetProperties.newRuntimeValue("radius_value", Messages.PolarPlot_RadiusValue);

    private static final WidgetPropertyDescriptor<VType> runtimePropAngleValue =
        CommonWidgetProperties.newRuntimeValue("angle_value", Messages.PolarPlot_AngleValue);

    private static final WidgetPropertyDescriptor<List<PolarPlotPoint>> runtimePropPoints =
        new WidgetPropertyDescriptor<>(WidgetPropertyCategory.RUNTIME, "points", Messages.PolarPlot_Points)
        {
            @Override
            public WidgetProperty<List<PolarPlotPoint>> createProperty(final Widget widget, final List<PolarPlotPoint> initial)
            {
                return new RuntimeWidgetProperty<>(this, widget, initial)
                {
                    @Override
                    @SuppressWarnings("unchecked")
                    public void setValueFromObject(final Object value) throws Exception
                    {
                        if (value instanceof List<?>)
                            setValue(Collections.unmodifiableList((List<PolarPlotPoint>) value));
                        else
                            throw new Exception("Need List<PolarPlotPoint>, got " + value);
                    }
                };
            }
        };

    private volatile WidgetProperty<WidgetColor> foreground;
    private volatile WidgetProperty<WidgetColor> background;
    private volatile WidgetProperty<String> radius_pv;
    private volatile WidgetProperty<String> angle_pv;
    private volatile WidgetProperty<Integer> buffer_size;
    private volatile WidgetProperty<WidgetColor> trace_color;
    private volatile WidgetProperty<Double> max_radius;
    private volatile WidgetProperty<VType> radius_value;
    private volatile WidgetProperty<VType> angle_value;
    private volatile WidgetProperty<List<PolarPlotPoint>> points;

    public PolarPlotWidget()
    {
        super(WIDGET_DESCRIPTOR.getType(), 300, 300);
    }

    @Override
    protected void defineProperties(final List<WidgetProperty<?>> properties)
    {
        super.defineProperties(properties);
        properties.add(foreground = propForegroundColor.createProperty(this, WidgetColorService.getColor(NamedWidgetColors.TEXT)));
        properties.add(background = propBackgroundColor.createProperty(this, WidgetColorService.getColor(NamedWidgetColors.BACKGROUND)));
        properties.add(radius_pv = propRadiusPV.createProperty(this, ""));
        properties.add(angle_pv = propAnglePV.createProperty(this, ""));
        properties.add(buffer_size = propBufferSize.createProperty(this, 100));
        properties.add(trace_color = propTraceColor.createProperty(this, new WidgetColor(0, 160, 230)));
        properties.add(max_radius = propMaxRadius.createProperty(this, 100.0));
        properties.add(radius_value = runtimePropRadiusValue.createProperty(this, null));
        properties.add(angle_value = runtimePropAngleValue.createProperty(this, null));
        properties.add(points = runtimePropPoints.createProperty(this, Collections.emptyList()));
    }

    @Override
    protected String getInitialTooltip()
    {
        return "Radius: $(radius_pv)\nAngle: $(angle_pv)";
    }

    public WidgetProperty<WidgetColor> propForeground()
    {
        return foreground;
    }

    public WidgetProperty<WidgetColor> propBackground()
    {
        return background;
    }

    public WidgetProperty<String> propRadiusPV()
    {
        return radius_pv;
    }

    public WidgetProperty<String> propAnglePV()
    {
        return angle_pv;
    }

    public WidgetProperty<Integer> propBufferSize()
    {
        return buffer_size;
    }

    public WidgetProperty<WidgetColor> propTraceColor()
    {
        return trace_color;
    }

    public WidgetProperty<Double> propMaxRadius()
    {
        return max_radius;
    }

    public WidgetProperty<VType> runtimePropRadiusValue()
    {
        return radius_value;
    }

    public WidgetProperty<VType> runtimePropAngleValue()
    {
        return angle_value;
    }

    public WidgetProperty<List<PolarPlotPoint>> runtimePropPoints()
    {
        return points;
    }
}
