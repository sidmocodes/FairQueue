package com.fairqueue.queue.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeartbeatMonitorTest {

    @Mock
    private QueueManager queueManager;

    private MeterRegistry meterRegistry;
    private HeartbeatMonitor heartbeatMonitor;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        heartbeatMonitor = new HeartbeatMonitor(queueManager, meterRegistry);
    }

    @Test
    void checkHeartbeats_shouldExecuteWithoutErrors() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            heartbeatMonitor.checkHeartbeats();
        });
    }

    @Test
    void constructor_shouldRegisterTimeoutCounter() {
        // Arrange & Act
        HeartbeatMonitor monitor = new HeartbeatMonitor(queueManager, meterRegistry);

        // Assert
        Counter counter = meterRegistry.find("fairqueue.queue.heartbeat.timeout").counter();
        assertNotNull(counter, "Heartbeat timeout counter should be registered");
    }

    @Test
    void checkHeartbeats_canBeCalledMultipleTimes() {
        // Act
        heartbeatMonitor.checkHeartbeats();
        heartbeatMonitor.checkHeartbeats();
        heartbeatMonitor.checkHeartbeats();

        // Assert - no exceptions thrown
        assertTrue(true);
    }
}
