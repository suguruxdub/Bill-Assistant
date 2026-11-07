package com.billassistant.session;

import com.billassistant.auth.AuthClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisSessionStore implements SessionStore {

    public static final String KEY_PREFIX = "bill-assistant:refresh:";
    private static final DefaultRedisScript<Long> SAVE = new DefaultRedisScript<>("""
            redis.call('HSET', KEYS[1], 'uid', ARGV[1], 'current', ARGV[2], 'expiresAt', ARGV[3], 'client', ARGV[4])
            redis.call('EXPIREAT', KEYS[1], ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> ROTATE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            local expiresAt = tonumber(redis.call('HGET', KEYS[1], 'expiresAt'))
            if not expiresAt or expiresAt <= tonumber(ARGV[3]) then
                redis.call('DEL', KEYS[1])
                return 0
            end
            if redis.call('HGET', KEYS[1], 'current') == ARGV[1] then
                redis.call('HSET', KEYS[1], 'current', ARGV[2])
                redis.call('SET', KEYS[2], ARGV[3], 'EXAT', expiresAt)
                return 1
            end
            local usedAt = redis.call('GET', KEYS[2])
            if usedAt then
                if tonumber(ARGV[3]) - tonumber(usedAt) <= tonumber(ARGV[4]) then return 2 end
                redis.call('DEL', KEYS[1])
                return 3
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> REVOKE = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'current') == ARGV[1] or redis.call('EXISTS', KEYS[2]) == 1 then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);
    private final StringRedisTemplate redisTemplate;

    public RedisSessionStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public static String key(String sid) {
        // A shared Redis hash tag keeps rotation keys in the same Redis Cluster slot.
        return KEY_PREFIX + "{" + sid + "}";
    }

    private String usedKey(String sid, String hash) {
        return key(sid) + ":used:" + hash;
    }

    @Override
    public void save(String sid, String tokenHash, long uid, long expiresAt, AuthClient client) {
        redisTemplate.execute(SAVE, List.of(key(sid)), Long.toString(uid), tokenHash, Long.toString(expiresAt), client.value());
    }

    @Override
    public RefreshSession find(String sid) {
        List<Object> values = redisTemplate.opsForHash().multiGet(key(sid), List.of("uid", "expiresAt", "client"));
        try {
            long uid = Long.parseLong(String.valueOf(values.get(0)));
            long expiry = Long.parseLong(String.valueOf(values.get(1)));
            AuthClient client = AuthClient.fromStoredValue((String) values.get(2));
            return uid > 0 ? new RefreshSession(uid, expiry, client) : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @Override
    public Rotation rotate(String sid, String oldHash, String newHash, long now, long graceSeconds) {
        Long result = redisTemplate.execute(ROTATE, List.of(key(sid), usedKey(sid, oldHash)),
                oldHash, newHash, Long.toString(now), Long.toString(graceSeconds));
        if (Long.valueOf(1).equals(result)) return Rotation.SUCCESS;
        if (Long.valueOf(2).equals(result)) return Rotation.CONFLICT;
        if (Long.valueOf(3).equals(result)) return Rotation.REUSED;
        return Rotation.INVALID;
    }

    @Override
    public void revoke(String sid, String tokenHash) {
        redisTemplate.execute(REVOKE, List.of(key(sid), usedKey(sid, tokenHash)), tokenHash);
    }

    @Override
    public void delete(String sid) {
        redisTemplate.delete(key(sid));
    }
}
