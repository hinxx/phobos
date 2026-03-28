/*******************************************************************************
 * Copyright (c) 2026 European Spallation Source ERIC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.csstudio.display.builder.model.widgets.plots;

/** Immutable polar sample point used between runtime and representation.
 *  @author Codex
 */
public class PolarPlotPoint
{
    private final double radius;
    private final double angle;

    public PolarPlotPoint(final double radius, final double angle)
    {
        this.radius = radius;
        this.angle = angle;
    }

    public double getRadius()
    {
        return radius;
    }

    public double getAngle()
    {
        return angle;
    }
}
