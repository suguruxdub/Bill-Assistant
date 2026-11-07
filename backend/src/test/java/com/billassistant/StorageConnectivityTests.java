package com.billassistant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Opt-in checks against the actual local stores: mvn -Dstorage.integration=true test. */
@SpringBootTest
@EnabledIfSystemProperty(named = "storage.integration", matches = "true")
class StorageConnectivityTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void connectsToMysql() {
        assertEquals(1, jdbcTemplate.queryForObject("SELECT 1", Integer.class));
        assertEquals("bill_assistant", jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));
    }

    @Test
    void redisRoundTrip() {
        String key = "bill-assistant:connectivity-test:" + UUID.randomUUID();
        try {
            redisTemplate.opsForValue().set(key, "ok", Duration.ofSeconds(30));
            assertEquals("ok", redisTemplate.opsForValue().get(key));
        } finally {
            redisTemplate.delete(key);
        }
    }
}
