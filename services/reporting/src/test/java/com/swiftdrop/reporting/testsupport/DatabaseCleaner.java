package com.swiftdrop.reporting.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseCleaner {

    private final JdbcTemplate jdbc;

    public DatabaseCleaner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void clean() {
        jdbc.execute("""
            TRUNCATE TABLE
                processed_event,
                parcel_fact,
                daily_delivery_stats,
                courier_daily_stats,
                zone_daily_stats,
                merchant_daily_stats,
                courier_state
            CASCADE
            """);
    }
}
