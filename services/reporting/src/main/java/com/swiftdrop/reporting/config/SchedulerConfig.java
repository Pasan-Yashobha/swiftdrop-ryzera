package com.swiftdrop.reporting.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's @Scheduled task execution.
 * The nightly reconciliation job is declared in
 * {@link com.swiftdrop.reporting.scheduler.ReconciliationScheduler}.
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
    // No beans needed – annotation does the work.
}
