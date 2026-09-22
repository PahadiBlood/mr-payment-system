package xyz.rawmanoj.mrbank.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.rawmanoj.mrbank.dto.internal.OtpCacheData;
import xyz.rawmanoj.mrbank.dto.request.SendOtpRequest;
import xyz.rawmanoj.mrbank.exception.ErrorCode;
import xyz.rawmanoj.mrbank.exception.MrBankException;
import xyz.rawmanoj.mrbank.repository.UserRepository;
import xyz.rawmanoj.mrbank.service.EmailService;
import xyz.rawmanoj.mrbank.service.RedisCacheService;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OtpServiceImplTest {

    private static final String EMAIL = "user@example.com";
    private static final String HMAC_SECRET = "test-otp-hmac-secret-0123456789abcd";

    @Mock
    private EmailService emailService;

    @Mock
    private UserRepository userRepository;

    private InMemoryRedis redis;
    private OtpServiceImpl otpService;

    @BeforeEach
    void setUp() {
        redis = new InMemoryRedis();
        otpService = new OtpServiceImpl(emailService, userRepository, redis, 5, HMAC_SECRET);
        org.mockito.Mockito.lenient().when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
    }

    @Test
    void secondSendInsideCooldownDoesNotSendAgain() {
        otpService.sendOtp(new SendOtpRequest(EMAIL));

        assertThatThrownBy(() -> otpService.sendOtp(new SendOtpRequest("User@Example.com")))
                .isInstanceOf(MrBankException.class)
                .extracting(ex -> ((MrBankException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);

        verify(emailService, times(1)).sendOtp(anyString(), anyString());
        assertThat(redis.getLong("otp:send:attempts:" + EMAIL)).contains(1L);
        assertThat(redis.hasKey("otp:" + EMAIL)).isTrue();
        assertThat(redis.hasKey("otp:User@Example.com")).isFalse();
    }

    @Test
    void sixthSendIsRejectedAndDoesNotEmail() {
        for (int attempt = 0; attempt < 5; attempt++) {
            redis.delete("otp:send:cooldown:" + EMAIL);
            redis.delete("otp:" + EMAIL);
            otpService.sendOtp(new SendOtpRequest(EMAIL));
        }

        redis.delete("otp:send:cooldown:" + EMAIL);
        redis.delete("otp:" + EMAIL);
        assertThatThrownBy(() -> otpService.sendOtp(new SendOtpRequest(EMAIL)))
                .isInstanceOf(MrBankException.class)
                .extracting(ex -> ((MrBankException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);

        verify(emailService, times(5)).sendOtp(anyString(), anyString());
        assertThat(redis.getLong("otp:send:attempts:" + EMAIL)).contains(5L);
    }

    @Test
    void parallelSendsReserveTheCooldownOnce() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    otpService.sendOtp(new SendOtpRequest(EMAIL));
                    successes.incrementAndGet();
                } catch (MrBankException ex) {
                    rejected.incrementAndGet();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(successes.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(threads - 1);
        assertThat(redis.getLong("otp:send:attempts:" + EMAIL)).contains(1L);
        verify(emailService, times(1)).sendOtp(anyString(), anyString());
    }

    @Test
    void mailFailureRemovesTheCodeAndDoesNotSpendTheDailyAttempt() {
        doThrow(new RuntimeException("smtp down")).when(emailService).sendOtp(anyString(), anyString());

        assertThatThrownBy(() -> otpService.sendOtp(new SendOtpRequest(EMAIL)))
                .isInstanceOf(MrBankException.class)
                .extracting(ex -> ((MrBankException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);

        assertThat(redis.get("otp:" + EMAIL, OtpCacheData.class)).isEmpty();
        assertThat(redis.hasKey("otp:send:cooldown:" + EMAIL)).isTrue();
        assertThat(redis.getLong("otp:send:attempts:" + EMAIL)).isEmpty();
    }

    @Test
    void laterMailFailureReturnsOnlyThatAttempt() {
        otpService.sendOtp(new SendOtpRequest(EMAIL));
        redis.delete("otp:send:cooldown:" + EMAIL);
        redis.delete("otp:" + EMAIL);
        doThrow(new RuntimeException("smtp down")).when(emailService).sendOtp(anyString(), anyString());

        assertThatThrownBy(() -> otpService.sendOtp(new SendOtpRequest(EMAIL)))
                .isInstanceOf(MrBankException.class)
                .extracting(ex -> ((MrBankException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);

        assertThat(redis.getLong("otp:send:attempts:" + EMAIL)).contains(1L);
        assertThat(redis.hasKey("otp:send:cooldown:" + EMAIL)).isTrue();
        assertThat(redis.get("otp:" + EMAIL, OtpCacheData.class)).isEmpty();
    }

    @Test
    void existingEmailIsRateLimitedAndDoesNotSend() {
        org.mockito.Mockito.when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> otpService.sendOtp(new SendOtpRequest(EMAIL)))
                .isInstanceOf(MrBankException.class)
                .extracting(ex -> ((MrBankException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        verify(emailService, never()).sendOtp(anyString(), anyString());
        assertThat(redis.getLong("otp:send:attempts:" + EMAIL)).contains(1L);
        assertThat(redis.hasKey("otp:send:cooldown:" + EMAIL)).isTrue();
    }

    @Test
    void wrongCodeIsBadRequestAndSixthGuessIsTooManyRequests() {
        otpService.sendOtp(new SendOtpRequest(EMAIL));
        String wrong = differentCode(issuedOtp());

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> otpService.verifyOtp(EMAIL, wrong))
                    .isInstanceOf(MrBankException.class)
                    .extracting(ex -> ((MrBankException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.BAD_REQUEST);
        }
        assertThat(redis.get("otp:" + EMAIL, OtpCacheData.class)).isEmpty();

        assertThatThrownBy(() -> otpService.verifyOtp(EMAIL, wrong))
                .isInstanceOf(MrBankException.class)
                .extracting(ex -> ((MrBankException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    void matchingCodeCanBeUsedOnceAndIsNotStoredInClear() {
        otpService.sendOtp(new SendOtpRequest(EMAIL));
        String otp = issuedOtp();
        OtpCacheData cached = redis.get("otp:" + EMAIL, OtpCacheData.class).orElseThrow();

        assertThat(cached.email()).isEqualTo(EMAIL);
        assertThat(cached.otpHash()).isNotEqualTo(otp).hasSize(64);

        otpService.verifyOtp("User@Example.com", otp);
        assertThat(otpService.consumeEmailVerification(EMAIL)).isTrue();

        assertThatThrownBy(() -> otpService.verifyOtp(EMAIL, otp))
                .isInstanceOf(MrBankException.class)
                .extracting(ex -> ((MrBankException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(otpService.consumeEmailVerification(EMAIL)).isFalse();
        assertThat(redis.getLong("otp:send:attempts:" + EMAIL)).contains(1L);
    }

    @Test
    void correctCodeForARegisteredEmailDoesNotMarkItVerified() {
        otpService.sendOtp(new SendOtpRequest(EMAIL));
        String otp = issuedOtp();
        org.mockito.Mockito.when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> otpService.verifyOtp(EMAIL, otp))
                .isInstanceOf(MrBankException.class)
                .extracting(ex -> ((MrBankException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        assertThat(redis.hasKey("otp:" + EMAIL)).isFalse();
        assertThat(otpService.consumeEmailVerification(EMAIL)).isFalse();
    }

    @Test
    void hashCopiedToAnotherEmailDoesNotVerify() {
        otpService.sendOtp(new SendOtpRequest(EMAIL));
        String otp = issuedOtp();
        OtpCacheData cached = redis.get("otp:" + EMAIL, OtpCacheData.class).orElseThrow();
        String other = "other@example.com";
        redis.set("otp:" + other, new OtpCacheData(other, cached.otpHash()), Duration.ofMinutes(10));

        assertThatThrownBy(() -> otpService.verifyOtp(other, otp))
                .isInstanceOf(MrBankException.class)
                .extracting(ex -> ((MrBankException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void shortHmacSecretIsRejected() {
        assertThatThrownBy(() -> new OtpServiceImpl(emailService, userRepository, redis, 5, "too-short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }

    @Test
    void parallelVerifiesConsumeTheCodeOnce() throws Exception {
        otpService.sendOtp(new SendOtpRequest(EMAIL));
        String otp = issuedOtp();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger missing = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    otpService.verifyOtp(EMAIL, otp);
                    successes.incrementAndGet();
                } catch (MrBankException ex) {
                    if (ex.getErrorCode() == ErrorCode.RESOURCE_NOT_FOUND) {
                        missing.incrementAndGet();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(successes.get()).isEqualTo(1);
        assertThat(missing.get()).isEqualTo(threads - 1);
        assertThat(otpService.consumeEmailVerification(EMAIL)).isTrue();
        assertThat(otpService.consumeEmailVerification(EMAIL)).isFalse();
    }

    private String issuedOtp() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).sendOtp(eq(EMAIL), captor.capture());
        return captor.getValue();
    }

    private static String differentCode(String otp) {
        return "000000".equals(otp) ? "111111" : "000000";
    }

    private static final class InMemoryRedis implements RedisCacheService {
        private final Map<String, Stored> data = new ConcurrentHashMap<>();

        private record Stored(Object value, Instant expiresAt) {
        }

        @Override
        public synchronized void set(String key, Object value) {
            data.put(key, new Stored(value, null));
        }

        @Override
        public synchronized void set(String key, Object value, Duration ttl) {
            data.put(key, new Stored(value, Instant.now().plus(ttl)));
        }

        @Override
        public synchronized Boolean setIfAbsent(String key, Object value, Duration ttl) {
            if (live(key) != null) {
                return false;
            }
            data.put(key, new Stored(value, Instant.now().plus(ttl)));
            return true;
        }

        @Override
        public synchronized long increment(String key, Duration ttl) {
            Stored current = live(key);
            long next = current == null ? 1L : ((Number) current.value).longValue() + 1L;
            Instant expiresAt = current == null || current.expiresAt == null
                    ? Instant.now().plus(ttl)
                    : current.expiresAt;
            data.put(key, new Stored(next, expiresAt));
            return next;
        }

        @Override
        public synchronized Optional<Long> getLong(String key) {
            Stored current = live(key);
            if (current == null) {
                return Optional.empty();
            }
            return Optional.of(((Number) current.value).longValue());
        }

        @Override
        public synchronized <T> Optional<T> get(String key, Class<T> type) {
            Stored current = live(key);
            if (current == null) {
                return Optional.empty();
            }
            return Optional.of(type.cast(current.value));
        }

        @Override
        public synchronized <T> Optional<T> getAndDelete(String key, Class<T> type) {
            Optional<T> current = get(key, type);
            if (current.isPresent()) {
                data.remove(key);
            }
            return current;
        }

        @Override
        public synchronized boolean deleteIfValueEquals(String key, Object expected) {
            Stored current = live(key);
            if (current == null || current.value == null || !current.value.equals(expected)) {
                return false;
            }
            data.remove(key);
            return true;
        }

        @Override
        public synchronized long releaseIncrement(String key) {
            Stored current = live(key);
            if (current == null) {
                return 0L;
            }
            long count = ((Number) current.value).longValue();
            if (count <= 1L) {
                data.remove(key);
                return 0L;
            }
            data.put(key, new Stored(count - 1L, current.expiresAt));
            return count - 1L;
        }

        @Override
        public synchronized boolean replaceIfValueEquals(
                String key,
                Object expected,
                String newKey,
                Object newValue,
                Duration ttl
        ) {
            Stored current = live(key);
            if (current == null || current.value == null || !current.value.equals(expected)) {
                return false;
            }
            data.remove(key);
            data.put(newKey, new Stored(newValue, Instant.now().plus(ttl)));
            return true;
        }

        @Override
        public synchronized Boolean hasKey(String key) {
            return live(key) != null;
        }

        @Override
        public synchronized Boolean delete(String key) {
            return data.remove(key) != null;
        }

        @Override
        public Long deleteAll(Set<String> keys) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Long deleteByPattern(String pattern) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Boolean expire(String key, Duration ttl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public synchronized Long getExpire(String key) {
            Stored current = live(key);
            if (current == null) {
                return -2L;
            }
            if (current.expiresAt == null) {
                return -1L;
            }
            return Math.max(Duration.between(Instant.now(), current.expiresAt).toSeconds(), 0L);
        }

        @Override
        public void hashSet(String key, String hashKey, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Optional<T> hashGet(String key, String hashKey, Class<T> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<Object, Object> hashGetAll(String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Boolean hashDelete(String key, String hashKey) {
            throw new UnsupportedOperationException();
        }

        private Stored live(String key) {
            Stored stored = data.get(key);
            if (stored == null) {
                return null;
            }
            if (stored.expiresAt != null && !Instant.now().isBefore(stored.expiresAt)) {
                data.remove(key);
                return null;
            }
            return stored;
        }
    }
}
