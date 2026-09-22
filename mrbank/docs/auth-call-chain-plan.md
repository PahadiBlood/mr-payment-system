# Auth call chain — remaining work

Started from `AuthController` and walked every type it calls. This file is the checkpoint if the session stops.

Update the status column when an item is finished. Do not re-open items under "Already done" unless the code has regressed.

## Call chain

```
AuthController
  sendOtp / verifyOtp
    OtpService -> OtpServiceImpl
      EmailService -> EmailServiceImpl
      UserRepository
      RedisCacheService -> RedisCacheServiceImpl
        RedisConfig (RedisTemplate)
      EmailAddress, OtpCacheData, MrBankException, ErrorCode
  register / login / refreshToken / logout
    AuthServiceImpl
      OtpService.consumeEmailVerification
      UserRepository, PasswordEncoder
      JwtTokenServiceImpl
      CustomResilienceHandlerService   <-- remove from this path
        RefreshTokenServiceImpl
      RefreshTokenServiceImpl (refresh + logout call it directly)
      EmailAddress
      RegisterRequest, LoginRequest, LogoutRequest, TokenRefreshRequest
```

`KycController` also calls `AuthServiceImpl.register`. It is not part of the public auth flow. Leave its route alone.

GlobalExceptionHandler turns `MrBankException` into the HTTP error body, so auth failures land there too.

## Already done — do not redo

These were fixed before this pass. The old notes about them are stale.

| Item | Where | What is true now |
| --- | --- | --- |
| OTP cache type | `OtpServiceImpl.verifyOtp` | Reads `OtpCacheData`, not `String`. A correct code is not rejected as a class cast. |
| OTP at rest | `OtpCacheData.otpHash` | HMAC-SHA256, not the raw code. Compare uses `MessageDigest.isEqual`. |
| Mail failure reported | `EmailServiceImpl` | `sendOtp` is synchronous. It is not `@Async`. SMTP failure reaches `OtpServiceImpl`. |
| Mail failure cleanup | `OtpServiceImpl.deliver` / `sendOtp` | Cached code is deleted, the daily attempt is returned, the 30s cooldown stays, client gets a generic 500 (`Failed to send OTP email`). Provider text that contains the code is not logged. |
| Send limits | `OtpServiceImpl` | 30s cooldown via `SET NX`, 5 sends / 24h, existing email spends the same quota and does not send. |
| Verify limits | `OtpServiceImpl` | 5 wrong guesses / 24h, then the challenge is deleted. Sixth guess is 429. |
| Single use | `deleteIfValueEquals` | Only the caller that still sees that challenge can mark the email verified. |
| Email canonical form | `EmailAddress`, request records | Trim + lowercase before cache keys and lookups. |
| Tests | `OtpServiceImplTest` | Cooldown, daily cap, parallel send, mail refund, existing email, wrong-code lockout, one-time verify, parallel verify. |

Intentional behavior, do not "fix" into a generic success:

- `POST /send-otp` for an email that is already registered returns **409** `Email already exists`. The product reveals that. Do not switch this endpoint to always-200.

## Fix in this pass

| Status | Item | File | What to change |
| --- | --- | --- | --- |
| done | Refresh token never saved | `RefreshTokenServiceImpl.createRefreshToken` | Delete the `1 / 0` block. It throws before `save`, so login never stores a refresh token. |
| done | Login swallows that failure | `CustomResilienceHandlerService` | Retry loop never breaks on success, sleeps on the request thread, empty interrupt catch, and drops the error. Login then returns a refresh token that is not in the database. Call `RefreshTokenService` directly and fail the login if the save fails. Delete the experiment class. |
| done | Login of a non-active user | `AuthServiceImpl.login` | Reject `INACTIVE`, `SUSPENDED`, `BLOCKED`, `DELETED`, and a null status after the password matches. Unknown user and wrong password stay `Invalid email or password`. |
| done | Login timing | `AuthServiceImpl.login` | Run `passwordEncoder.matches` against a real bcrypt hash when the user is missing, so the missing-user path does not return early. |
| done | Refresh reuse | `RefreshTokenServiceImpl.verifyRefreshToken` | Presenting a revoked token deletes every refresh token for that user. The lookup takes a row lock. The user is loaded before that transaction ends. |
| done | Refresh rotation | `AuthServiceImpl.refreshToken` | Same transaction: lock and verify, revoke the presented token, store a new one, return the new pair. A second refresh waits on the lock, sees the revoke, and ends every session. Unexpected failures stay 500. `UnauthorizedException` stays 401. |
| done | Account status on refresh | `AuthServiceImpl.refreshToken` | A non-active user cannot refresh. |
| done | Attempt refund race | `RedisCacheServiceImpl` | Replace get-then-set in `releaseSendAttempt` with a Lua `DECR` that deletes the key at 0 and keeps the TTL. A concurrent send must not be overwritten. |
| done | Verify flag is not atomic | `OtpServiceImpl.verifyOtp` | One Lua script deletes the challenge and sets `otp_verified:{email}` only when the stored value is still that challenge. |
| done | Registered email keeps a live OTP | `OtpServiceImpl.verifyOtp` | After a correct code, if the email is already registered, delete the challenge and return 409. Do not set the verified flag. |
| done | HMAC not bound to the email | `OtpServiceImpl` | HMAC is `email || separator || otp` with the server secret. A copied hash does not verify under another email. |
| done | Short HMAC secret | `OtpServiceImpl` constructor | Reject a secret shorter than 32 bytes. |
| done | Controller depends on the impl | `AuthController`, `KycController` | Depend on `AuthService`. `JwtTokenService` and `RefreshTokenService` are the types used by auth. |
| done | `hashToken` is public | `RefreshTokenServiceImpl` | Make it private. |
| done | Access token claim | `JwtTokenServiceImpl` | Use `claim("userId", ...)`. `Map.of` throws if the id is null. jjwt 0.12 `claims(Map)` adds claims and does not wipe `sub`; this change is so a null id cannot 500 the login. |
| done | Grammar | `OtpServiceImpl` | `Email already exist` -> `Email already exists`. Other OTP sentences stay, so clients matching those strings do not break. |
| done | Parser errors | `GlobalExceptionHandler` | `HttpMessageNotReadableException` returns `Malformed request body`, not the parser text. |
| done | Password length | `RegisterRequest` | `@Size(min = 8, max = 72)` because bcrypt only uses the first 72 bytes. |
| done | Redis typing footgun | `RedisConfig` | Drop the unused `ObjectMapper`. Keep `@class` typing: `OtpCacheData` is a record, and without the hint Redis returns a map and verify 500s. Counters stay plain JSON integers so `INCR` works. Allowlist is the application package plus `java.time` and `java.math`. |
| done | Blank From address | `EmailServiceImpl` | Fail startup if from-address or from-name is blank. |
| done | Tests | `OtpServiceImplTest`, `AuthServiceImplTest`, `RefreshTokenServiceImplTest` | Cover the items above. The OTP tests use an in-memory `RedisCacheService`, so the Lua scripts themselves are not executed against Redis. |

## Leave for a later pass

| Status | Item | Why it is not in this pass |
| --- | --- | --- |
| not started | Same error for a missing OTP and a wrong OTP | `verifyOtp` returns 404 `OTP does not exist or expired` or 400 `OTP does not match`. That tells a caller whether a challenge exists. Tests lock the split. Unifying the message is safer and is an API change. Also HMAC is skipped when the cache is empty, so the two cases differ in timing. |
| not started | `SecurityConfig` | Not called by the controller. `.anyRequest().permitAll()` makes new routes public. `/actuator/**` is public. CORS origin is `*`. The class is full of generated comments (`Comments are generated by AI`, numbered steps, debug logs). Strip the comments and deny by default, with an explicit list of public routes. |
| not started | `JwtAuthenticationFilter` | Not called by the controller. Static `System.out.println`, emoji logs, and page-length comments. The filter only checks the signature and puts the email in the context. It does not load the user or the account status. |
| not started | `KycController` `POST /api/v1/secure/kyc/register` | Duplicate of public register, behind JWT. Leave the route. It now depends on `AuthService`. |
| not started | Log emails in the clear | Auth and OTP logs include the full address. Mask if log access is wider than production operators. |
| not started | `RedisCacheService.deleteByPattern` | Uses `KEYS`, which blocks Redis. Nothing in auth calls it. |
| not started | Refresh-token session cap | Each login inserts another token. Rotation only revokes the presented one. |
| not started | Awkward OTP copy | `Frequent request wait for 30 seconds` and `Too many otp requests...` are unchanged on purpose. |
| not started | `UserService` | Empty class. Nothing in this chain calls it. |
| not started | `CommonUtils.getClientIp` | Nothing in this chain calls it. Trusts `X-Forwarded-For` as written. |
| not started | `application-dev.yaml` | Gitignored local mailbox settings. Do not commit that file or copy its values into docs. |

## Verified

`OtpServiceImplTest`, `AuthServiceImplTest`, and `RefreshTokenServiceImplTest` passed. The OTP tests use an in-memory cache, so the Redis Lua scripts were not executed against Redis. `MrBankApplicationTests` was not run.

## Comments

Keep comments that say why a security choice is easy to "clean up" wrong:

- `OtpServiceImpl`: existing email spends send quota; a provider failure returns the daily attempt and keeps the cooldown; only the current challenge may set the verified flag.
- `EmailServiceImpl`: do not log a provider message that contains the code.
- `RefreshTokenServiceImpl`: a revoked token presented again ends every session for that user.

Remove the `1 / 0` comment with the dead block, and remove `CustomResilienceHandlerService` with its class. Do not add a comment that restates the next line.
