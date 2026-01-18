package com.translate_idea_2_code.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Basic AOP-based logging interceptor for Spring Boot applications.
 * <p>
 * This aspect provides centralized logging for REST controllers and services,
 * eliminating the need for repetitive logging code in each method.
 * <p>
 * Features:
 * - Automatic method entry/exit logging
 * - Exception logging with proper context
 * - Configurable pointcuts for different layers
 *
 * @author Vinod Bokare
 * @version 1.0
 */
@Aspect
@Component
public class BasicLoggingInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(BasicLoggingInterceptor.class);

    /**
     * Pointcut that matches all methods in classes annotated with @RestController
     */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void restControllerMethods() {
        // Pointcut definition - no implementation needed
    }

    /**
     * Pointcut that matches all methods in classes annotated with @Service
     */
    @Pointcut("within(@org.springframework.stereotype.Service *)")
    public void serviceMethods() {
        // Pointcut definition - no implementation needed
    }

    /**
     * Pointcut that matches all public methods in the repository layer
     */
    @Pointcut("within(@org.springframework.stereotype.Repository *)")
    public void repositoryMethods() {
        // Pointcut definition - no implementation needed
    }

    /**
     * Around advice for REST controller methods.
     * Logs method entry and exit with method signature.
     *
     * @param joinPoint the join point representing the intercepted method
     * @return the result of the method execution
     * @throws Throwable if the intercepted method throws an exception
     */
    @Around("restControllerMethods()")
    public Object logAroundController(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();

        logger.info("→ Entering: {}.{}()", getSimpleClassName(className), methodName);

        try {
            Object result = joinPoint.proceed();
            logger.info("← Exiting: {}.{}()", getSimpleClassName(className), methodName);
            return result;
        } catch (Exception e) {
            logger.error("✗ Exception in: {}.{}() - {}",
                    getSimpleClassName(className), methodName, e.getMessage());
            throw e;
        }
    }

    /**
     * Around advice for service layer methods (disabled by default).
     * Uncomment the @Around annotation to enable service layer logging.
     *
     * @param joinPoint the join point representing the intercepted method
     * @return the result of the method execution
     * @throws Throwable if the intercepted method throws an exception
     */
    // @Around("serviceMethods()")
    public Object logAroundService(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();

        logger.debug("Service - Entering: {}.{}()", getSimpleClassName(className), methodName);

        Object result = joinPoint.proceed();

        logger.debug("Service - Exiting: {}.{}()", getSimpleClassName(className), methodName);

        return result;
    }

    /**
     * After-throwing advice for REST controller methods.
     * Logs detailed exception information when methods throw exceptions.
     *
     * @param joinPoint the join point representing the intercepted method
     * @param exception the exception thrown by the method
     */
    @AfterThrowing(pointcut = "restControllerMethods()", throwing = "exception")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable exception) {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();

        logger.error("Exception thrown in {}.{}(): {} - {}",
                getSimpleClassName(className),
                methodName,
                exception.getClass().getSimpleName(),
                exception.getMessage(),
                exception);
    }

    /**
     * Extracts the simple class name from a fully qualified class name.
     *
     * @param fullClassName the fully qualified class name
     * @return the simple class name
     */
    private String getSimpleClassName(String fullClassName) {
        return fullClassName.substring(fullClassName.lastIndexOf('.') + 1);
    }

    /**
     * Logs method arguments (helper method for future enhancements).
     * Can be used to log request parameters in a formatted way.
     *
     * @param args the method arguments
     * @return formatted string representation of arguments
     */
    protected String formatArguments(Object[] args) {
        if (args == null || args.length == 0) {
            return "no arguments";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(args[i] != null ? args[i].getClass().getSimpleName() : "null");
        }
        return sb.toString();
    }
}