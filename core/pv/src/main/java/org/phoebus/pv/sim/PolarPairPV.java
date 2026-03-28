/*******************************************************************************
 * Copyright (c) 2026 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.phoebus.pv.sim;

import java.text.NumberFormat;
import java.util.List;

import org.epics.util.stats.Range;
import org.epics.util.text.NumberFormats;
import org.epics.vtype.Display;
import org.epics.vtype.VType;
import org.phoebus.pv.PV;

/** Simulated polar plot pair member.
 *
 *  <p>Use matching {@code sim://polarradius(...)} and {@code sim://polarangle(...)}
 *  PVs with the same pair id to receive values from one shared simulator and timestamp.
 */
@SuppressWarnings("nls")
public class PolarPairPV extends PV
{
    enum Component
    {
        RADIUS,
        ANGLE
    }

    private static final double DEFAULT_PAIR_ID = 1.0;
    private static final double DEFAULT_MAX_RADIUS = 100.0;
    private static final double DEFAULT_STEPS = 120.0;
    private static final double DEFAULT_UPDATE_SECONDS = 0.1;
    private static final NumberFormat angle_format = NumberFormats.precisionFormat(3);

    static PolarPairPV forParameters(final String name, final Component component, final List<Double> parameters) throws Exception
    {
        final ParsedParameters parsed = ParsedParameters.from(parameters);
        final PolarPairSimulation simulation = PolarPairSimulation.getOrCreate(parsed.pair_id,
                                                                               parsed.max_radius,
                                                                               parsed.steps,
                                                                               parsed.update_seconds);
        return new PolarPairPV(name, component, parsed.max_radius, simulation);
    }

    private final Component component;
    private final PolarPairSimulation simulation;
    private final Display display;

    private PolarPairPV(final String name, final Component component, final double max_radius, final PolarPairSimulation simulation)
    {
        super(name);
        this.component = component;
        this.simulation = simulation;
        display = component == Component.RADIUS
                ? SimulatedDoublePV.createDisplay(0.0, max_radius)
                : Display.of(Range.of(0.0, 2.0 * Math.PI),
                             Range.undefined(),
                             Range.undefined(),
                             Range.of(0.0, 2.0 * Math.PI),
                             "rad", angle_format);
        notifyListenersOfPermissions(true);
        simulation.register(this);
    }

    Display getDisplay()
    {
        return display;
    }

    Component getComponent()
    {
        return component;
    }

    void publish(final VType value)
    {
        notifyListenersOfValue(value);
    }

    @Override
    protected void close()
    {
        simulation.unregister(this);
        super.close();
    }

    private static class ParsedParameters
    {
        final long pair_id;
        final double max_radius;
        final int steps;
        final double update_seconds;

        private ParsedParameters(final long pair_id, final double max_radius, final int steps, final double update_seconds)
        {
            this.pair_id = pair_id;
            this.max_radius = max_radius;
            this.steps = steps;
            this.update_seconds = update_seconds;
        }

        static ParsedParameters from(final List<Double> parameters) throws Exception
        {
            if (parameters.size() > 4)
                throw new Exception("sim://polar(radius|angle) needs no parameters or (pair_id, max_radius, steps, update_seconds)");

            final long pair_id = parameters.size() >= 1 ? asPairId(parameters.get(0)) : asPairId(DEFAULT_PAIR_ID);
            final double max_radius = parameters.size() >= 2 ? parameters.get(1) : DEFAULT_MAX_RADIUS;
            final int steps = parameters.size() >= 3 ? asSteps(parameters.get(2)) : asSteps(DEFAULT_STEPS);
            final double update_seconds = parameters.size() >= 4 ? parameters.get(3) : DEFAULT_UPDATE_SECONDS;

            if (max_radius <= 0.0)
                throw new Exception("max_radius must be > 0");
            if (update_seconds <= 0.0)
                throw new Exception("update_seconds must be > 0");

            return new ParsedParameters(pair_id, max_radius, steps, update_seconds);
        }

        private static long asPairId(final double value) throws Exception
        {
            final long rounded = Math.round(value);
            if (Math.abs(value - rounded) > 1e-9)
                throw new Exception("pair_id must be an integer");
            return rounded;
        }

        private static int asSteps(final double value) throws Exception
        {
            final long rounded = Math.round(value);
            if (Math.abs(value - rounded) > 1e-9 || rounded < 2)
                throw new Exception("steps must be an integer >= 2");
            return (int) rounded;
        }
    }
}
