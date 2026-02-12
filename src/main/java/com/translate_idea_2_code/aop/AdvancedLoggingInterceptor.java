package com.translate_idea_2_code.aop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Advanced AOP-based logging interceptor with performance monitoring and request correlation.
 * <p>
 * This enhanced version extends basic logging with:
 * - Performance monitoring and execution time tracking
 * - Request correlation using MDC (Mapped Diagnostic Context)
 * - Detailed request/response logging
 * - Configurable slow method detection
 * - JSON serialization for complex objects
 *
 * @author Vinod Bokare
 * @version 2.0
 * @see <a href="https://github.com/vinodbokareme/spring-aop-logging">GitHub Repository</a>
 */
@Aspect
@Component
public class AdvancedLoggingInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(AdvancedLoggingInterceptor.class);
    private static final Logger performanceLogger = LoggerFactory.getLogger("performance");

    // MDC Keys for request correlation
    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_USER_ID = "userId";
    private static final String MDC_SESSION_ID = "sessionId";
    private static final String MDC_CLIENT_IP = "clientIp";

    private final ObjectMapper objectMapper;

    @Value("${logging.performance.slow-threshold-ms:1000}")
    private long slowMethodThresholdMs;

    @Value("${logging.request.log-headers:false}")
    private boolean logHeaders;

    @Value("${logging.request.log-body:false}")
    private boolean logRequestBody;

    /**
     * Creates the interceptor with a default Jackson mapper for structured logs.
     */
    public AdvancedLoggingInterceptor() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Matches methods declared in classes annotated with {@code @RestController}.
     */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void restControllerMethods() {
    }

    /**
     * Matches methods declared in classes annotated with {@code @Service}.
     */
    @Pointcut("within(@org.springframework.stereotype.Service *)")
    public void serviceMethods() {
    }

    /**
     * Around advice with performance monitoring and MDC context management.
     * Tracks execution time and logs performance metrics for slow methods.
     */
    @Around("restControllerMethods()")
    public Object logWithPerformanceTracking(ProceedingJoinPoint joinPoint) throws Throwable {
        Instant startTime = Instant.now();
        String requestId = setupMDCContext();

        String className = getSimpleClassName(joinPoint.getSignature().getDeclaringTypeName());
        String methodName = joinPoint.getSignature().getName();

        try {
            logRequestDetails(joinPoint);

            logger.info("→ [{}] Starting: {}.{}()", requestId, className, methodName);

            Object result = joinPoint.proceed();

            Duration duration = Duration.between(startTime, Instant.now());
            logPerformanceMetrics(className, methodName, duration, true);

            logger.info("← [{}] Completed: {}.{}() in {} ms",
                    requestId, className, methodName, duration.toMillis());

            return result;

        } catch (Exception e) {
            Duration duration = Duration.between(startTime, Instant.now());
            logPerformanceMetrics(className, methodName, duration, false);

            logger.error("✗ [{}] Failed: {}.{}() after {} ms - {}",
                    requestId, className, methodName, duration.toMillis(), e.getMessage());
            throw e;

        } finally {
            clearMDCContext();
        }
    }

    /**
     * Service layer advice with performance tracking (disabled by default).
     */
    // @Around("serviceMethods()")
    public Object logServiceWithPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        Instant startTime = Instant.now();

        String className = getSimpleClassName(joinPoint.getSignature().getDeclaringTypeName());
        String methodName = joinPoint.getSignature().getName();

        logger.debug("Service → {}.{}()", className, methodName);

        try {
            Object result = joinPoint.proceed();

            Duration duration = Duration.between(startTime, Instant.now());
            logger.debug("Service ← {}.{}() completed in {} ms",
                    className, methodName, duration.toMillis());

            return result;
        } catch (Exception e) {
            Duration duration = Duration.between(startTime, Instant.now());
            logger.error("Service ✗ {}.{}() failed after {} ms",
                    className, methodName, duration.toMillis());
            throw e;
        }
    }

    /**
     * Enhanced exception logging with MDC context.
     */
    @AfterThrowing(pointcut = "restControllerMethods()", throwing = "exception")
    public void logExceptionWithContext(JoinPoint joinPoint, Throwable exception) {
        String requestId = MDC.get(MDC_REQUEST_ID);
        String className = getSimpleClassName(joinPoint.getSignature().getDeclaringTypeName());
        String methodName = joinPoint.getSignature().getName();

        Map<String, String> errorContext = new HashMap<>();
        errorContext.put("requestId", requestId);
        errorContext.put("method", className + "." + methodName);
        errorContext.put("exceptionType", exception.getClass().getName());
        errorContext.put("userId", MDC.get(MDC_USER_ID));
        errorContext.put("clientIp", MDC.get(MDC_CLIENT_IP));

        logger.error("Exception Context: {}", toJsonString(errorContext));
        logger.error("Stack trace:", exception);
    }

    /**
     * Sets up MDC context with request correlation data.
     *
     * @return the generated request ID
     */
    private String setupMDCContext() {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(MDC_REQUEST_ID, requestId);

        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();

                // Extract and store correlation data
                MDC.put(MDC_CLIENT_IP, getClientIpAddress(request));
                MDC.put(MDC_SESSION_ID, request.getSession(false) != null ?
                        request.getSession().getId() : "no-session");

                // Extract user from security context or header
                String userId = extractUserId(request);
                if (userId != null) {
                    MDC.put(MDC_USER_ID, userId);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to setup full MDC context: {}", e.getMessage());
        }

        return requestId;
    }

    /**
     * Clears MDC context after request processing.
     */
    private void clearMDCContext() {
        MDC.remove(MDC_REQUEST_ID);
        MDC.remove(MDC_USER_ID);
        MDC.remove(MDC_SESSION_ID);
        MDC.remove(MDC_CLIENT_IP);
    }

    /**
     * Logs performance metrics and identifies slow methods.
     */
    private void logPerformanceMetrics(String className, String methodName,
                                       Duration duration, boolean success) {
        long executionTimeMs = duration.toMillis();

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("class", className);
        metrics.put("method", methodName);
        metrics.put("executionTimeMs", executionTimeMs);
        metrics.put("success", success);
        metrics.put("requestId", MDC.get(MDC_REQUEST_ID));
        metrics.put("timestamp", Instant.now().toString());

        if (executionTimeMs > slowMethodThresholdMs) {
            metrics.put("slow", true);
            performanceLogger.warn("SLOW METHOD DETECTED: {}", toJsonString(metrics));
        } else {
            performanceLogger.info("Performance: {}", toJsonString(metrics));
        }
    }

    /**
     * Logs detailed request information.
     */
    private void logRequestDetails(ProceedingJoinPoint joinPoint) {
        if (!logHeaders && !logRequestBody) {
            return;
        }

        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();

                Map<String, Object> requestDetails = new HashMap<>();
                requestDetails.put("method", request.getMethod());
                requestDetails.put("uri", request.getRequestURI());
                requestDetails.put("queryString", request.getQueryString());

                if (logHeaders) {
                    Map<String, String> headers = new HashMap<>();
                    request.getHeaderNames().asIterator()
                            .forEachRemaining(name -> headers.put(name, request.getHeader(name)));
                    requestDetails.put("headers", headers);
                }

                if (logRequestBody && joinPoint.getArgs().length > 0) {
                    requestDetails.put("arguments", formatArguments(joinPoint.getArgs()));
                }

                logger.debug("Request Details: {}", toJsonString(requestDetails));
            }
        } catch (Exception e) {
            logger.warn("Failed to log request details: {}", e.getMessage());
        }
    }

    /**
     * Extracts user ID from request (customize based on your auth mechanism).
     */
    private String extractUserId(HttpServletRequest request) {
        // Option 1: From custom header
        String userId = request.getHeader("X-User-Id");
        if (userId != null) {
            return userId;
        }

        // Option 2: From Spring Security (uncomment if using Spring Security)
        /*
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        */

        // Option 3: From JWT token or session
        return "anonymous";
    }

    /**
     * Extracts the real client IP address from the request.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED"
        };

        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }

    /**
     * Formats method arguments for logging.
     */
    private String formatArguments(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");

            if (args[i] == null) {
                sb.append("null");
            } else {
                sb.append(args[i].getClass().getSimpleName());
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Converts object to JSON string for structured logging.
     */
    private String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }

    /**
     * Extracts the simple class name from a fully qualified class name.
     *
     * @param fullClassName fully qualified class name
     * @return simple class name
     */
    private String getSimpleClassName(String fullClassName) {
        return fullClassName.substring(fullClassName.lastIndexOf('.') + 1);
    }
}