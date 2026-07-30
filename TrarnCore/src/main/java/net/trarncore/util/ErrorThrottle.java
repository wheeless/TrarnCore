package net.trarncore.util;

import org.slf4j.Logger;

/**
 * Rate-limits repeated error logging.
 *
 * <p>A fault inside a render callback fires every frame. Logging each occurrence produces
 * hundreds of identical stack traces a second, which buries the first one — the only one that
 * matters — and can fill a disk during a long session.
 */
public final class ErrorThrottle {

    private final Logger logger;
    private final long intervalMillis;

    private long lastLogged = 0;
    private long suppressed = 0;

    private ErrorThrottle(Logger logger, long intervalMillis) {
        this.logger = logger;
        this.intervalMillis = intervalMillis;
    }

    public static ErrorThrottle of(Logger logger, long intervalMillis) {
        return new ErrorThrottle(logger, intervalMillis);
    }

    /** Five seconds — the interval every mod here had already settled on independently. */
    public static ErrorThrottle ofDefault(Logger logger) {
        return new ErrorThrottle(logger, 5000);
    }

    /** Logs unless one was logged within the interval; reports how many were swallowed. */
    public void error(String message, Throwable throwable) {
        long now = System.currentTimeMillis();
        if (now - lastLogged < intervalMillis) {
            suppressed++;
            return;
        }
        if (suppressed > 0) {
            logger.error("{} (suppressed {} repeat(s) in the last {}ms)",
                message, suppressed, intervalMillis, throwable);
            suppressed = 0;
        } else {
            logger.error("{} (suppressing repeats for {}ms)", message, intervalMillis, throwable);
        }
        lastLogged = now;
    }
}
