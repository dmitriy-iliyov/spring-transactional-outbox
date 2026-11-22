package io.github.outbox.notification.consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TraceParentUnitTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";

    @Test
    @DisplayName("UT parse() when the value is a sampled W3C traceparent should read trace id, span id and the sampled flag")
    void parse_whenSampledTraceParent_shouldReadTraceIdSpanIdAndSampledFlag() {
        // when
        TraceParent parsed = TraceParent.parse("00-" + TRACE_ID + "-" + SPAN_ID + "-01");

        // then
        assertThat(parsed.isEmpty()).isFalse();
        assertThat(parsed.traceId()).isEqualTo(TRACE_ID);
        assertThat(parsed.spanId()).isEqualTo(SPAN_ID);
        assertThat(parsed.sampled()).isTrue();
    }

    @Test
    @DisplayName("UT parse() when the sampled flag is not set should read the value as not sampled")
    void parse_whenFlagIsNotSampled_shouldReadTheValueAsNotSampled() {
        // when
        TraceParent parsed = TraceParent.parse("00-" + TRACE_ID + "-" + SPAN_ID + "-00");

        // then
        assertThat(parsed.isEmpty()).isFalse();
        assertThat(parsed.traceId()).isEqualTo(TRACE_ID);
        assertThat(parsed.spanId()).isEqualTo(SPAN_ID);
        assertThat(parsed.sampled()).isFalse();
    }

    @Test
    @DisplayName("UT parse() when other flag bits are set but not the sampled bit should read the value as not sampled")
    void parse_whenOnlyOtherFlagBitsAreSet_shouldReadTheValueAsNotSampled() {
        // when
        TraceParent parsed = TraceParent.parse("00-" + TRACE_ID + "-" + SPAN_ID + "-02");

        // then
        assertThat(parsed.sampled()).isFalse();
    }

    @Test
    @DisplayName("UT parse() when the sampled bit is set among other flag bits should read the value as sampled")
    void parse_whenSampledBitIsSetAmongOtherBits_shouldReadTheValueAsSampled() {
        // when
        TraceParent parsed = TraceParent.parse("00-" + TRACE_ID + "-" + SPAN_ID + "-03");

        // then
        assertThat(parsed.sampled()).isTrue();
    }

    @Test
    @DisplayName("UT parse() when the value is null should return the empty value")
    void parse_whenValueIsNull_shouldReturnEmpty() {
        // when
        TraceParent parsed = TraceParent.parse(null);

        // then
        assertThat(parsed).isSameAs(TraceParent.EMPTY);
        assertThat(parsed.isEmpty()).isTrue();
        assertThat(parsed.traceId()).isNull();
        assertThat(parsed.spanId()).isNull();
        assertThat(parsed.sampled()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "4bf92f3577b34da6a3ce929d0e0e4736",
            "00-4bf92f3577b34da6-00f067aa0ba902b7-01",
            "00-" + TRACE_ID + "-00f067aa0ba902b7-0",
            "00-" + TRACE_ID + "-00f067aa0ba902b7-01-extra",
            "00-4BF92F3577B34DA6A3CE929D0E0E4736-00F067AA0BA902B7-01",
            "zz-" + TRACE_ID + "-" + SPAN_ID + "-01",
            "00-00000000000000000000000000000000-" + SPAN_ID + "-01",
            "00-" + TRACE_ID + "-0000000000000000-01"
    })
    @DisplayName("UT parse() when the value is not a valid W3C traceparent should return the empty value")
    void parse_whenValueIsNotValid_shouldReturnEmpty(String traceParent) {
        // when
        TraceParent parsed = TraceParent.parse(traceParent);

        // then
        assertThat(parsed).isSameAs(TraceParent.EMPTY);
        assertThat(parsed.isEmpty()).isTrue();
        assertThat(parsed.traceId()).isNull();
        assertThat(parsed.spanId()).isNull();
        assertThat(parsed.sampled()).isFalse();
    }
}
