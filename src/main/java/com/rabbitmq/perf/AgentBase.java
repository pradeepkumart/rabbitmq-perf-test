// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.perf;

import com.rabbitmq.client.MissedHeartbeatException;
import com.rabbitmq.client.ShutdownSignalException;

import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 *
 */
public abstract class AgentBase {

    // FIXME this is the condition to start connection recovery
    // ensure it's the appropriate condition and get it from the Java client code
    static final Predicate<ShutdownSignalException> CONNECTION_RECOVERY_TRIGGERED =
        e -> !e.isInitiatedByApplication() || (e.getCause() instanceof MissedHeartbeatException);

    protected void delay(long now, AgentState state) {

        long elapsed = now - state.getLastStatsTime();
        //example: rateLimit is 5000 msg/s,
        //10 ms have elapsed, we have published 200 messages
        //the 200 msgs we have actually published should have taken us
        //200 * 1000 / 5000 = 40 ms. So we pause for 40ms - 10ms
        long pause = (long) (state.getRateLimit() == 0.0f ?
            0.0f : (state.getMsgCount() * 1000.0 / state.getRateLimit() - elapsed));
        if (pause > 0) {
            try {
                Thread.sleep(pause);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected boolean isConnectionRecoveryTriggered(ShutdownSignalException e) {
        return CONNECTION_RECOVERY_TRIGGERED.test(e);
    }

    protected void handleShutdownSignalExceptionOnWrite(BooleanSupplier showStopper, ShutdownSignalException e) {
        if (shouldStop(showStopper, e)) {
            throw e;
        }
    }

    protected boolean shouldStop(BooleanSupplier showStopper, ShutdownSignalException e) {
        return !showStopper.getAsBoolean() || !isConnectionRecoveryTriggered(e);
    }

    protected void dealWithWriteOperation(WriteOperation writeOperation, BooleanSupplier showStopper) throws IOException {
        try {
            writeOperation.call();
        } catch (ShutdownSignalException e) {
            handleShutdownSignalExceptionOnWrite(showStopper, e);
            // connection recovery is in progress, we ignore the exception if this point is reached
        }
    }

    protected interface AgentState {

        float getRateLimit();

        long getLastStatsTime();

        int getMsgCount();

        int incrementMessageCount();
    }

    @FunctionalInterface
    interface WriteOperation {
        void call() throws IOException;
    }
}