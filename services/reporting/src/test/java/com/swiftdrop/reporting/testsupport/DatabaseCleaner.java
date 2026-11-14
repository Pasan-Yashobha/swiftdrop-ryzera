package com.swiftdrop.reporting.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatabaseCleaner {

    private final JdbcTemplate jdbc;

    public DatabaseCleaner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void clean() {
        List<String> tables = jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'PUBLIC' and table_type = 'BASE TABLE'
                  and table_name <> 'flyway_schema_history'
                """, String.class);
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        tables.forEach(table -> jdbc.execute("DELETE FROM " + table));
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }
}
