package xyz.rawmanoj.mrbank.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;
import xyz.rawmanoj.mrbank.service.RedisCacheService;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RedisCacheServiceImpl implements RedisCacheService {
    private static final byte[] INCREMENT_WITH_TTL = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """.getBytes(StandardCharsets.UTF_8);

    private static final byte[] DELETE_IF_VALUE_EQUALS = """
            local current = redis.call('GET', KEYS[1])
            if current == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """.getBytes(StandardCharsets.UTF_8);

    private static final byte[] RELEASE_INCREMENT = """
            local current = redis.call('GET', KEYS[1])
            if not current then
                return 0
            end
            local count = tonumber(current)
            if not count or count <= 1 then
                redis.call('DEL', KEYS[1])
                return 0
            end
            return redis.call('DECR', KEYS[1])
            """.getBytes(StandardCharsets.UTF_8);

    private static final byte[] REPLACE_IF_VALUE_EQUALS = """
            local current = redis.call('GET', KEYS[1])
            if current == ARGV[1] then
                redis.call('DEL', KEYS[1])
                redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])
                return 1
            end
            return 0
            """.getBytes(StandardCharsets.UTF_8);

    private final RedisTemplate<String, Object> redisTemplate;

    /** Saves a value and keeps it until something deletes it. */
    @Override
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /** Saves a value and deletes it when the time runs out. */
    @Override
    public void set(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    /** Saves a value only when the key is empty. Returns true when this call saved it. */
    @Override
    public Boolean setIfAbsent(String key, Object value, Duration ttl) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
    }

    /** Adds one to a counter and starts its lifetime on the first add. */
    @Override
    public long increment(String key, Duration ttl) {
        Long value = redisTemplate.execute((RedisCallback<Long>) connection -> {
            Object result = connection.eval(
                    INCREMENT_WITH_TTL,
                    ReturnType.INTEGER,
                    1,
                    key.getBytes(StandardCharsets.UTF_8),
                    Long.toString(Math.max(ttl.toMillis(), 1)).getBytes(StandardCharsets.UTF_8));
            if (result instanceof Number number) {
                return number.longValue();
            }
            return null;
        });
        if (value == null) {
            throw new IllegalStateException("Redis increment returned no value for key " + key);
        }
        return value;
    }

    /** Reads a counter. Returns empty when the key is missing. */
    @Override
    public Optional<Long> getLong(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Number number) {
            return Optional.of(number.longValue());
        }
        try {
            return Optional.of(Long.parseLong(value.toString()));
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Cached counter at " + key + " is not a number", ex);
        }
    }

    /** Reads a value as the given type. Returns empty when the key is missing. */
    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(type.cast(value));
    }

    /** Reads a value and deletes it in the same step. */
    @Override
    public <T> Optional<T> getAndDelete(String key, Class<T> type) {
        Object value = redisTemplate.opsForValue().getAndDelete(key);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(type.cast(value));
    }

    /** Deletes the key only when the stored value is still the one we expect. */
    @Override
    public boolean deleteIfValueEquals(String key, Object expected) {
        byte[] expectedBytes = serialize(expected);
        Long removed = redisTemplate.execute((RedisCallback<Long>) connection -> {
            Object result = connection.eval(
                    DELETE_IF_VALUE_EQUALS,
                    ReturnType.INTEGER,
                    1,
                    key.getBytes(StandardCharsets.UTF_8),
                    expectedBytes);
            if (result instanceof Number number) {
                return number.longValue();
            }
            return 0L;
        });
        return removed != null && removed > 0;
    }

    /** Gives back one count. Deletes the key when the count would hit zero, and keeps the time limit. */
    @Override
    public long releaseIncrement(String key) {
        Long value = redisTemplate.execute((RedisCallback<Long>) connection -> {
            Object result = connection.eval(
                    RELEASE_INCREMENT,
                    ReturnType.INTEGER,
                    1,
                    key.getBytes(StandardCharsets.UTF_8));
            if (result instanceof Number number) {
                return number.longValue();
            }
            return null;
        });
        if (value == null) {
            throw new IllegalStateException("Redis release returned no value for key " + key);
        }
        return value;
    }

    /** When the old key still has the expected value, deletes it and saves the new key. */
    @Override
    public boolean replaceIfValueEquals(String key, Object expected, String newKey, Object newValue, Duration ttl) {
        Long replaced = redisTemplate.execute((RedisCallback<Long>) connection -> {
            Object result = connection.eval(
                    REPLACE_IF_VALUE_EQUALS,
                    ReturnType.INTEGER,
                    2,
                    key.getBytes(StandardCharsets.UTF_8),
                    newKey.getBytes(StandardCharsets.UTF_8),
                    serialize(expected),
                    serialize(newValue),
                    Long.toString(Math.max(ttl.toMillis(), 1)).getBytes(StandardCharsets.UTF_8));
            if (result instanceof Number number) {
                return number.longValue();
            }
            return null;
        });
        if (replaced == null) {
            throw new IllegalStateException("Redis replace returned no value for key " + key);
        }
        return replaced > 0;
    }

    /** Returns true when the key exists. */
    @Override
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /** Deletes one key. */
    @Override
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    /** Deletes every key in the set. */
    @Override
    public Long deleteAll(Set<String> keys) {
        return redisTemplate.delete(keys);
    }

    /** Deletes keys that match the pattern. */
    @Override
    public Long deleteByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        return redisTemplate.delete(keys);
    }

    /** Sets how long a key should live. */
    @Override
    public Boolean expire(String key, Duration ttl) {
        return redisTemplate.expire(key, ttl);
    }

    /** Returns how many seconds are left on a key. */
    @Override
    public Long getExpire(String key) {
        return redisTemplate.getExpire(key);
    }

    /** Saves one field inside a hash. */
    @Override
    public void hashSet(String key, String hashKey, Object value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    /** Reads one field from a hash. */
    @Override
    public <T> Optional<T> hashGet(String key, String hashKey, Class<T> type) {
        Object value = redisTemplate.opsForHash().get(key, hashKey);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(type.cast(value));
    }

    /** Reads every field in a hash. */
    @Override
    public Map<Object, Object> hashGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    /** Deletes one field from a hash. */
    @Override
    public Boolean hashDelete(String key, String hashKey) {
        return redisTemplate.opsForHash().delete(key, hashKey) > 0;
    }

    /** Turns a value into the bytes Redis stores. */
    private byte[] serialize(Object value) {
        @SuppressWarnings("unchecked")
        RedisSerializer<Object> serializer = (RedisSerializer<Object>) redisTemplate.getValueSerializer();
        if (serializer == null) {
            throw new IllegalStateException("Redis value serializer is not configured");
        }
        byte[] bytes = serializer.serialize(value);
        if (bytes == null) {
            throw new IllegalStateException("Redis value serializer returned no bytes");
        }
        return bytes;
    }
}
