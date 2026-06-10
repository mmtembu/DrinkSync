package com.smarteventbar.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying Flyway migrations V6, V7, V8 apply cleanly
 * to a fresh PostgreSQL container and that schema constraints are correct.
 * <p>
 * Uses the "test" profile which configures Testcontainers JDBC URL for PostgreSQL.
 * Flyway runs automatically on application startup, so by the time these tests
 * execute, all migrations have already been applied.
 * <p>
 * Validates: Requirements 10.1, 10.2, 10.3, 10.4, 10.5, 10.6
 */
@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // -----------------------------------------------------------------------
    // V6: WhatsApp columns on orders table
    // -----------------------------------------------------------------------

    @Test
    void v6_ordersTable_hasCustomerPhoneColumn() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, character_maximum_length " +
                "FROM information_schema.columns " +
                "WHERE table_name = 'orders' AND column_name = 'customer_phone'");

        assertEquals(1, columns.size(), "customer_phone column should exist on orders table");
        assertEquals("character varying", columns.get(0).get("data_type"));
        assertEquals(20, columns.get(0).get("character_maximum_length"));
    }

    @Test
    void v6_ordersTable_hasWhatsappOptInColumn() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, column_default, is_nullable " +
                "FROM information_schema.columns " +
                "WHERE table_name = 'orders' AND column_name = 'whatsapp_opt_in'");

        assertEquals(1, columns.size(), "whatsapp_opt_in column should exist on orders table");
        assertEquals("boolean", columns.get(0).get("data_type"));
        assertEquals("NO", columns.get(0).get("is_nullable"));
    }

    @Test
    void v6_ordersTable_hasWhatsappMessageStatusColumn() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, character_maximum_length " +
                "FROM information_schema.columns " +
                "WHERE table_name = 'orders' AND column_name = 'whatsapp_message_status'");

        assertEquals(1, columns.size(), "whatsapp_message_status column should exist on orders table");
        assertEquals("character varying", columns.get(0).get("data_type"));
        assertEquals(20, columns.get(0).get("character_maximum_length"));
    }

    // -----------------------------------------------------------------------
    // V7: WhatsApp columns on session table
    // -----------------------------------------------------------------------

    @Test
    void v7_sessionTable_hasCustomerPhoneColumn() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, character_maximum_length " +
                "FROM information_schema.columns " +
                "WHERE table_name = 'session' AND column_name = 'customer_phone'");

        assertEquals(1, columns.size(), "customer_phone column should exist on session table");
        assertEquals("character varying", columns.get(0).get("data_type"));
        assertEquals(20, columns.get(0).get("character_maximum_length"));
    }

    @Test
    void v7_sessionTable_hasWhatsappOptInColumn() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable " +
                "FROM information_schema.columns " +
                "WHERE table_name = 'session' AND column_name = 'whatsapp_opt_in'");

        assertEquals(1, columns.size(), "whatsapp_opt_in column should exist on session table");
        assertEquals("boolean", columns.get(0).get("data_type"));
        assertEquals("NO", columns.get(0).get("is_nullable"));
    }

    // -----------------------------------------------------------------------
    // V8: notification_log table
    // -----------------------------------------------------------------------

    @Test
    void v8_notificationLogTable_exists() {
        List<Map<String, Object>> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_name = 'notification_log'");

        assertEquals(1, tables.size(), "notification_log table should exist");
    }

    @Test
    void v8_notificationLogTable_hasAllRequiredColumns() {
        List<String> expectedColumns = List.of(
                "id", "order_id", "channel", "message_type", "destination",
                "provider_message_id", "status", "error_message", "sent_at", "updated_at");

        for (String column : expectedColumns) {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(
                    "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_name = 'notification_log' AND column_name = ?", column);
            assertEquals(1, result.size(),
                    "notification_log table should have column: " + column);
        }
    }

    @Test
    void v8_notificationLogTable_hasForeignKeyToOrders() {
        List<Map<String, Object>> fks = jdbcTemplate.queryForList(
                "SELECT tc.constraint_name, kcu.column_name, " +
                "ccu.table_name AS foreign_table_name, ccu.column_name AS foreign_column_name " +
                "FROM information_schema.table_constraints AS tc " +
                "JOIN information_schema.key_column_usage AS kcu " +
                "  ON tc.constraint_name = kcu.constraint_name " +
                "JOIN information_schema.constraint_column_usage AS ccu " +
                "  ON ccu.constraint_name = tc.constraint_name " +
                "WHERE tc.table_name = 'notification_log' " +
                "  AND tc.constraint_type = 'FOREIGN KEY' " +
                "  AND kcu.column_name = 'order_id'");

        assertFalse(fks.isEmpty(), "notification_log should have a foreign key on order_id");
        assertEquals("orders", fks.get(0).get("foreign_table_name"));
        assertEquals("id", fks.get(0).get("foreign_column_name"));
    }

    @Test
    void v8_notificationLogTable_hasDeduplicationUniquePartialIndex() {
        // Verify the unique partial index exists by checking pg_indexes
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
                "SELECT indexname, indexdef FROM pg_indexes " +
                "WHERE tablename = 'notification_log' AND indexname = 'idx_notification_log_dedup'");

        assertEquals(1, indexes.size(), "Deduplication unique partial index should exist");

        String indexDef = (String) indexes.get(0).get("indexdef");
        assertTrue(indexDef.contains("UNIQUE"), "Index should be UNIQUE");
        assertTrue(indexDef.contains("order_id"), "Index should include order_id");
        assertTrue(indexDef.contains("message_type"), "Index should include message_type");
        assertTrue(indexDef.contains("WHERE"), "Index should be a partial index with WHERE clause");
    }

    @Test
    void v8_notificationLogTable_hasOrderIdIndex() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes " +
                "WHERE tablename = 'notification_log' AND indexname = 'idx_notification_log_order_id'");

        assertEquals(1, indexes.size(), "Index on order_id should exist");
    }

    @Test
    void v8_notificationLogTable_hasProviderMessageIdIndex() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes " +
                "WHERE tablename = 'notification_log' AND indexname = 'idx_notification_log_provider_msg_id'");

        assertEquals(1, indexes.size(), "Index on provider_message_id should exist");
    }

    @Test
    void v8_notificationLogTable_statusDefaultIsPending() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_default FROM information_schema.columns " +
                "WHERE table_name = 'notification_log' AND column_name = 'status'");

        assertEquals(1, columns.size());
        String defaultValue = (String) columns.get(0).get("column_default");
        assertNotNull(defaultValue, "status column should have a default value");
        assertTrue(defaultValue.contains("pending"), "status default should be 'pending'");
    }

    @Test
    void flywayMigrations_allAppliedSuccessfully() {
        // Verify Flyway history table shows V6, V7, V8 as successful
        List<Map<String, Object>> migrations = jdbcTemplate.queryForList(
                "SELECT version, description, success FROM flyway_schema_history " +
                "WHERE version IN ('6', '7', '8') ORDER BY installed_rank");

        assertEquals(3, migrations.size(), "V6, V7, V8 migrations should all be recorded");

        for (Map<String, Object> migration : migrations) {
            assertTrue((Boolean) migration.get("success"),
                    "Migration V" + migration.get("version") + " should have succeeded");
        }
    }
}
