package xyz.rawmanoj.mrbank.service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface RedisCacheService {

    /** Saves a value and keeps it until something deletes it. */
    void set(String key, Object value);

    /** Saves a value and deletes it when the time runs out. */
    void set(String key, Object value, Duration ttl);

    /** Saves a value only when the key is empty. Returns true when this call saved it. */
    Boolean setIfAbsent(String key, Object value, Duration ttl);

    /** Adds one to a counter and starts its lifetime on the first add. */
    long increment(String key, Duration ttl);

    /** Reads a counter. Returns empty when the key is missing. */
    Optional<Long> getLong(String key);

    /** Reads a value as the given type. Returns empty when the key is missing. */
    <T> Optional<T> get(String key, Class<T> type);

    /** Reads a value and deletes it in the same step. */
    <T> Optional<T> getAndDelete(String key, Class<T> type);

    /** Deletes the key only when the stored value is still the one we expect. */
    boolean deleteIfValueEquals(String key, Object expected);

    /** Gives back one count. Deletes the key when the count would hit zero, and keeps the time limit. */
    long releaseIncrement(String key);

    /** When the old key still has the expected value, deletes it and saves the new key. */
    boolean replaceIfValueEquals(String key, Object expected, String newKey, Object newValue, Duration ttl);

    /** Returns true when the key exists. */
    Boolean hasKey(String key);

    /** Deletes one key. */
    Boolean delete(String key);

    /** Deletes every key in the set. */
    Long deleteAll(Set<String> keys);

    /** Deletes keys that match the pattern. */
    Long deleteByPattern(String pattern);

    /** Sets how long a key should live. */
    Boolean expire(String key, Duration ttl);

    /** Returns how many seconds are left on a key. */
    Long getExpire(String key);

    /** Saves one field inside a hash. */
    void hashSet(String key, String hashKey, Object value);

    /** Reads one field from a hash. */
    <T> Optional<T> hashGet(String key, String hashKey, Class<T> type);

    /** Reads every field in a hash. */
    Map<Object, Object> hashGetAll(String key);

    /** Deletes one field from a hash. */
    Boolean hashDelete(String key, String hashKey);
}
