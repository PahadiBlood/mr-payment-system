package xyz.rawmanoj.mrbank.service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface RedisCacheService {
    void set(String key, Object value);

    void set(String key, Object value, Duration ttl);

    <T> Optional<T> get(String key, Class<T> type);

    Boolean hasKey(String key);

    Boolean delete(String key);

    Long deleteAll(Set<String> keys);

    Long deleteByPattern(String pattern);

    Boolean expire(String key, Duration ttl);

    Long getExpire(String key);

    void hashSet(String key, String hashKey, Object value);

    <T> Optional<T> hashGet(String key, String hashKey, Class<T> type);

    Map<Object, Object> hashGetAll(String key);

    Boolean hashDelete(String key, String hashKey);
}
