package com.mservice.shared.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/***
 * @author uyen.tran
 */
public class LogUtils {
    private static final Logger logger = LoggerFactory.getLogger(LogUtils.class);

    public static void init() {
        // SLF4J is auto-configured by Spring Boot
    }

    public static void info(String serviceCode, Object object) {
        logger.info("[{}]: {}", serviceCode, object);
    }

    public static void info(Object object) {
        logger.info("{}", object);
    }

    public static void debug(Object object) {
        logger.debug("{}", object);
    }

    public static void error(Object object) {
        logger.error("{}", object);
    }

    public static void warn(Object object) {
        logger.warn("{}", object);
    }
}
