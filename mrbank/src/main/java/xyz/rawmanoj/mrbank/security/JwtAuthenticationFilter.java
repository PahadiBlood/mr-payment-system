package xyz.rawmanoj.mrbank.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import xyz.rawmanoj.mrbank.exception.response.ErrorResponse;
import xyz.rawmanoj.mrbank.service.impl.JwtTokenServiceImpl;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * JWT Authentication Filter
 * 
 * Responsible for:
 * 1. Extracting JWT token from Authorization header (Bearer prefix)
 * 2. Validating JWT token format and signature
 * 3. Extracting user information from token claims
 * 4. Setting authentication context for the request
 * 5. Logging authentication attempts and failures
 * 
 * Applies only to secured endpoints (/api/v1/secure/**, /secure/**)
 * Allows public endpoints (/api/v1/public/**, /swagger-ui/**, etc.) to bypass
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = HttpHeaders.AUTHORIZATION;

    private final JwtTokenServiceImpl jwtTokenService;
    private final ObjectMapper objectMapper;
    
    static {
        // This runs when class is first loaded
        System.out.println("🔐 JwtAuthenticationFilter class loaded");
    }

    /**
     * Determines if this filter should skip processing for the current request.
     * 
     * This filter only applies to secured endpoints:
     * - /api/v1/secure/**
     * - /secure/**
     * 
     * Public endpoints bypass this filter automatically.
     *
     * @param request HttpServletRequest to check
     * @return true if filter should not process (skip), false to process
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Get path (includes context path /api if configured)
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        String method = request.getMethod();
        
        log.info("🔍 shouldNotFilter() CALLED - method={}, fullPath={}, contextPath={}", 
                 method, path, contextPath);
        
        // Remove context path from the request URI for pattern matching
        String pathWithoutContext = path.substring(contextPath.length());
        
        // Apply filter only for secured endpoints
        boolean shouldApplyFilter = pathWithoutContext.startsWith("/api/v1/secure") || 
                                    pathWithoutContext.startsWith("/secure");
        boolean shouldSkip = !shouldApplyFilter;

        if (shouldSkip) {
            log.info("✅ SKIP JWT filter for public path: {} -> pathWithoutContext: {}", 
                     path, pathWithoutContext);
        } else {
            log.info("🔐 APPLY JWT filter for secured path: {} -> pathWithoutContext: {}", 
                     path, pathWithoutContext);
        }

        return shouldSkip;
    }

    /**
     * Main filter logic - Authenticates requests using JWT tokens.
     *
     * Flow:
     * 1. Extract Bearer token from Authorization header
     * 2. Validate token format and signature
     * 3. Extract subject (user email) from token
     * 4. Set SecurityContext with authentication
     * 5. Continue filter chain or return 401 if invalid
     *
     * @param request HttpServletRequest
     * @param response HttpServletResponse
     * @param filterChain FilterChain to continue
     * @throws ServletException if servlet exception occurs
     * @throws IOException if I/O exception occurs
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestPath = request.getRequestURI();
        String method = request.getMethod();

        log.info("🎯 doFilterInternal() CALLED - method={}, path={}", method, requestPath);
        
        try {
            // Step 1: Extract Bearer token from Authorization header
            String token = extractBearerToken(request);

            if (token == null) {
                log.warn("❌ Missing Authorization header for secured endpoint: {} {}", method, requestPath);
                writeUnauthorizedResponse(response, "Missing or malformed Authorization header", requestPath);
                return;
            }

            // Step 2: Validate token format and signature
            log.debug("Validating JWT token for endpoint: {}", requestPath);
            if (!jwtTokenService.isAccessTokenValid(token)) {
                log.warn("❌ Invalid or expired JWT token for endpoint: {} {}", method, requestPath);
                writeUnauthorizedResponse(response, "Invalid or expired access token", requestPath);
                return;
            }

            // Step 3: Extract subject (user email) from token
            String subject = jwtTokenService.extractSubject(token);
            log.debug("JWT token validated successfully for user: {}", subject);

            // Step 4: Set SecurityContext with authentication
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    subject,
                    null,
                    List.of()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.info("✅ User authenticated successfully: {} | Endpoint: {} {}", subject, method, requestPath);

        } catch (Exception e) {
            log.error("❌ Error during JWT authentication for endpoint: {} {} - {}",
                      method, requestPath, e.getMessage(), e);
            writeUnauthorizedResponse(response, "Authentication failed: " + e.getMessage(), requestPath);
            return;
        }

        // Step 5: Continue filter chain
        try {
            filterChain.doFilter(request, response);
            log.debug("Request processing completed for: {} {}", method, requestPath);
        } catch (Exception e) {
            log.error("❌ Error during filter chain processing for: {} {} - {}",
                      method, requestPath, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Extracts JWT token from Authorization header.
     * 
     * Expected format: "Bearer <jwt-token>"
     * 
     * @param request HttpServletRequest
     * @return JWT token without "Bearer " prefix, or null if not found
     */
    private String extractBearerToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authorizationHeader == null) {
            log.debug("Authorization header not present in request");
            return null;
        }

        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Authorization header does not start with 'Bearer ': {}", 
                     authorizationHeader.substring(0, Math.min(20, authorizationHeader.length())));
            return null;
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length());
        log.debug("Bearer token extracted successfully, token length: {}", token.length());
        return token;
    }

    /**
     * Writes 401 Unauthorized response with error details.
     * 
     * Response format:
     * {
     *   "code": "UNAUTHORIZED",
     *   "message": "{error_message}",
     *   "status": 401,
     *   "path": "{request_path}",
     *   "timestamp": "2026-09-20T...",
     *   "errors": []
     * }
     *
     * @param response HttpServletResponse to write to
     * @param errorMessage Error message to include
     * @param requestPath Request path for logging
     * @throws IOException if writing to response fails
     */
    private void writeUnauthorizedResponse(
            HttpServletResponse response,
            String errorMessage,
            String requestPath
    ) throws IOException {
        log.warn("Returning 401 UNAUTHORIZED for path: {} | Reason: {}", requestPath, errorMessage);

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");

        ErrorResponse errorResponse = new ErrorResponse(
                "UNAUTHORIZED",
                errorMessage,
                HttpStatus.UNAUTHORIZED.value(),
                requestPath,
                Instant.now(),
                List.of()
        );

        try {
            objectMapper.writeValue(response.getWriter(), errorResponse);
            log.debug("Error response written successfully");
        } catch (IOException e) {
            log.error("Failed to write error response: {}", e.getMessage(), e);
            throw e;
        }
    }
}
