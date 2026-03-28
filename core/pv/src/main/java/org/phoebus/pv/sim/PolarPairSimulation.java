/*******************************************************************************
 * Copyright (c) 2026 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.phoebus.pv.sim;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.epics.vtype.Alarm;
import org.epics.vtype.Time;
import org.epics.vtype.VDouble;

/** Shared simulator for paired polar plot PVs. */
@SuppressWarnings("nls")
class PolarPairSimulation
{
    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1, target ->
    {
        final Thread thread = new Thread(target, "PolarPairSimPV");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<Long, PolarPairSimulation> simulations = new ConcurrentHashMap<>();

    static PolarPairSimulation getOrCreate(final long pair_id, final double max_radius, final int steps, final double update_seconds) throws Exception
    {
        final PolarPairSimulation created = new PolarPairSimulation(pair_id, max_radius, steps, update_seconds);
        final PolarPairSimulation existing = simulations.putIfAbsent(pair_id, created);
        if (existing != null)
        {
            existing.validateConfiguration(max_radius, steps, update_seconds);
            return existing;
        }
        created.start();
        return created;
    }

    private final long pair_id;
    private final double max_radius;
    private final int steps;
    private final double update_seconds;

    private PolarPairPV radius_pv;
    private PolarPairPV angle_pv;
    private ScheduledFuture<?> task;
    private long sample = -1;

    private PolarPairSimulation(final long pair_id, final double max_radius, final int steps, final double update_seconds)
    {
        this.pair_id = pair_id;
        this.max_radius = max_radius;
        this.steps = steps;
        this.update_seconds = update_seconds;
    }

    synchronized void register(final PolarPairPV pv)
    {
        if (pv.getComponent() == PolarPairPV.Component.RADIUS)
            radius_pv = pv;
        else
            angle_pv = pv;
    }

    synchronized void unregister(final PolarPairPV pv)
    {
        if (pv == radius_pv)
            radius_pv = null;
        if (pv == angle_pv)
            angle_pv = null;

        if (radius_pv == null && angle_pv == null)
        {
            if (task != null)
                task.cancel(false);
            simulations.remove(pair_id, this);
        }
    }

    private synchronized void start()
    {
        final long milli = Math.round(Math.max(update_seconds, 0.01) * 1000.0);
        task = executor.scheduleAtFixedRate(this::update, milli, milli, TimeUnit.MILLISECONDS);
    }

    private void validateConfiguration(final double configured_max_radius, final int configured_steps, final double configured_update_seconds) throws Exception
    {
        if (Double.compare(max_radius, configured_max_radius) != 0 ||
            steps != configured_steps ||
            Double.compare(update_seconds, configured_update_seconds) != 0)
            throw new Exception("Polar pair simulator " + pair_id + " was already created with different parameters");
    }

    private void update()
    {
        final PolarPairPV radius;
        final PolarPairPV angle;
        final long index;

        synchronized (this)
        {
            radius = radius_pv;
            angle = angle_pv;
            sample = (sample + 1) % steps;
            index = sample;
        }

        if (radius == null && angle == null)
            return;

        final double normalized = steps <= 1 ? 0.0 : (double) index / (steps - 1);
        final double radius_value = normalized * max_radius;
        final double angle_value = normalized * 2.0 * Math.PI;
        final Time timestamp = Time.now();

        if (radius != null)
            radius.publish(VDouble.of(radius_value, Alarm.none(), timestamp, radius.getDisplay()));
        if (angle != null)
            angle.publish(VDouble.of(angle_value, Alarm.none(), timestamp, angle.getDisplay()));
    }
}
