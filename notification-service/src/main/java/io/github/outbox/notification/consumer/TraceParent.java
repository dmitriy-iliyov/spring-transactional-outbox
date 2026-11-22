package io.github.outbox.notification.consumer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed W3C {@code traceparent} value — version, trace id, parent span id, flags — as carried in the
 * {@code traceParent} field of every published event by the producing service
 * ({@code CurrentTraceParent} on the order-service side).
 * <p>
 * Parsing never fails: a {@code null} or malformed value yields {@link #EMPTY}, so a consumer never breaks
 * because of the header. All-zero ids are malformed per the W3C spec, as are non-lowercase hex digits.
 */
final class TraceParent {

    static final TraceParent EMPTY = new TraceParent(null, null, false);

    // W3C traceparent: version, trace id (32 hex), parent span id (16 hex), flags (2 hex).
    private static final Pattern TRACE_PARENT =
            Pattern.compile("([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})");

    private final String traceId;
    private final String spanId;
    private final boolean sampled;

    private TraceParent(String traceId, String spanId, boolean sampled) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.sampled = sampled;
    }

    /**
     * @return the parsed value, or {@link #EMPTY} when {@code traceParent} is {@code null} or not a valid W3C
     *         {@code traceparent}
     */
    static TraceParent parse(String traceParent) {
        if (traceParent == null) {
            return EMPTY;
        }
        Matcher matcher = TRACE_PARENT.matcher(traceParent);
        if (!matcher.matches() || isAllZeros(matcher.group(2)) || isAllZeros(matcher.group(3))) {
            return EMPTY;
        }
        // sampled flag is bit 0x01 of the flags byte; other bits (e.g. future flags) may also be set
        boolean sampled = (Integer.parseInt(matcher.group(4), 16) & 0x01) == 0x01;
        return new TraceParent(matcher.group(2), matcher.group(3), sampled);
    }

    boolean isEmpty() {
        return traceId == null;
    }

    /**
     * @return the trace id of the request that caused the event; {@code null} on {@link #EMPTY}
     */
    String traceId() {
        return traceId;
    }

    /**
     * @return the span id of the request that caused the event; {@code null} on {@link #EMPTY}
     */
    String spanId() {
        return spanId;
    }

    /**
     * @return whether the request was sampled; {@code false} on {@link #EMPTY}
     */
    boolean sampled() {
        return sampled;
    }

    private static boolean isAllZeros(String hex) {
        for (int i = 0; i < hex.length(); i++) {
            if (hex.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }
}
