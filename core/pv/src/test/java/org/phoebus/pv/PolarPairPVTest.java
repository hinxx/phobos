/*******************************************************************************
 * Copyright (c) 2026 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.phoebus.pv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.epics.vtype.Time;
import org.epics.vtype.VType;
import org.junit.jupiter.api.Test;

import io.reactivex.rxjava3.disposables.Disposable;

@SuppressWarnings("nls")
public class PolarPairPVTest
{
    @Test
    public void pairedPolarSimulationUsesMatchingTimestamps() throws Exception
    {
        final PV radius = PVPool.getPV("sim://polarradius(17, 50, 20, 0.05)");
        final PV angle = PVPool.getPV("sim://polarangle(17, 50, 20, 0.05)");

        final CountDownLatch updates = new CountDownLatch(2);
        final AtomicReference<Instant> radius_time = new AtomicReference<>();
        final AtomicReference<Instant> angle_time = new AtomicReference<>();

        final Disposable radius_sub = radius.onValueEvent().subscribe(value ->
        {
            radius_time.compareAndSet(null, timestampOf(value));
            updates.countDown();
        });
        final Disposable angle_sub = angle.onValueEvent().subscribe(value ->
        {
            angle_time.compareAndSet(null, timestampOf(value));
            updates.countDown();
        });

        assertTrue(updates.await(5, TimeUnit.SECONDS), "Expected paired simulator updates");
        assertEquals(radius_time.get(), angle_time.get());

        radius_sub.dispose();
        angle_sub.dispose();
        PVPool.releasePV(radius);
        PVPool.releasePV(angle);
    }

    private Instant timestampOf(final VType value)
    {
        return Time.timeOf(value).getTimestamp();
    }
}
