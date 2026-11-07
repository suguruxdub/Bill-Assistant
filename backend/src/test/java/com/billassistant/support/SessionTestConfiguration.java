package com.billassistant.support;

import com.billassistant.session.SessionStore;
import com.billassistant.auth.AuthClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@TestConfiguration(proxyBeanMethods = false)
public class SessionTestConfiguration {

    @Bean
    @Primary
    public InMemorySessionStore testSessionStore() {
        return new InMemorySessionStore();
    }

    public static class InMemorySessionStore implements SessionStore {
        private static class Entry {
            long uid;
            long expiresAt;
            String hash;
            AuthClient client;
            Map<String, Long> used = new HashMap<>();
            Entry(long uid, long expiresAt, String hash, AuthClient client) {
                this.uid = uid; this.expiresAt = expiresAt; this.hash = hash;
                this.client = client;
            }
        }
        private final Map<String, Entry> entries = new HashMap<>();
        private boolean failReads;
        private boolean failWrites;

        @Override
        public synchronized void save(String sid, String hash, long uid, long expiresAt, AuthClient client) {
            if (failWrites) throw new DataAccessResourceFailureException("Simulated Redis outage");
            entries.put(sid, new Entry(uid, expiresAt, hash, client));
        }

        @Override
        public synchronized RefreshSession find(String sid) {
            if (failReads) throw new DataAccessResourceFailureException("Simulated Redis outage");
            Entry entry = entries.get(sid);
            if (entry == null) return null;
            if (entry.expiresAt <= Instant.now().getEpochSecond()) { entries.remove(sid); return null; }
            return new RefreshSession(entry.uid, entry.expiresAt, entry.client);
        }

        @Override
        public synchronized Rotation rotate(String sid, String oldHash, String newHash, long now, long graceSeconds) {
            if (failWrites) throw new DataAccessResourceFailureException("Simulated Redis outage");
            Entry entry = entries.get(sid);
            if (entry == null || entry.expiresAt <= now) return Rotation.INVALID;
            if (entry.hash.equals(oldHash)) {
                entry.hash = newHash;
                entry.used.put(oldHash, now);
                return Rotation.SUCCESS;
            }
            Long usedAt = entry.used.get(oldHash);
            if (usedAt == null) return Rotation.INVALID;
            if (now - usedAt <= graceSeconds) return Rotation.CONFLICT;
            entries.remove(sid);
            return Rotation.REUSED;
        }

        @Override
        public synchronized void revoke(String sid, String hash) {
            if (failWrites) throw new DataAccessResourceFailureException("Simulated Redis outage");
            Entry entry = entries.get(sid);
            if (entry != null && (entry.hash.equals(hash) || entry.used.containsKey(hash))) entries.remove(sid);
        }

        @Override
        public synchronized void delete(String sid) { entries.remove(sid); }

        public synchronized void expire(String sid) {
            if (entries.containsKey(sid)) entries.get(sid).expiresAt = 0;
        }

        public synchronized void ageUsedToken(String sid, String hash) {
            entries.get(sid).used.put(hash, 0L);
        }

        public synchronized String currentHash(String sid) { return entries.get(sid).hash; }
        public synchronized int size() { return entries.size(); }
        public void setFailReads(boolean fail) { failReads = fail; }
        public void setFailWrites(boolean fail) { failWrites = fail; }
    }
}
