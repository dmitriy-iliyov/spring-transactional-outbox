package io.github.outbox.order.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdFilterUnitTest {

    private static final String HEADER = OrderController.CORRELATION_ID_HEADER;

    private final CorrelationIdFilter tested = new CorrelationIdFilter();
    private final AtomicReference<String> mdcInChain = new AtomicReference<>();
    private final AtomicReference<String> headerInChain = new AtomicReference<>();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("UT doFilter() when the client sends a correlation id should log the request under it")
    void doFilter_whenClientSendsCorrelationId_shouldLogRequestUnderIt() throws Exception {
        // given
        MockHttpServletRequest request = request("checkout-1");

        // when
        tested.doFilter(request, new MockHttpServletResponse(), recordingChain());

        // then
        assertThat(mdcInChain.get()).isEqualTo("checkout-1");
        assertThat(headerInChain.get()).isEqualTo("checkout-1");
    }

    @Test
    @DisplayName("UT doFilter() when no correlation id is sent should give the request a new one, header and MDC")
    void doFilter_whenNoCorrelationIdIsSent_shouldGiveRequestNewOneAsHeaderAndInMdc() throws Exception {
        // when
        tested.doFilter(request(null), new MockHttpServletResponse(), recordingChain());

        // then
        assertThat(UUID.fromString(mdcInChain.get())).isNotNull();
        assertThat(headerInChain.get()).isEqualTo(mdcInChain.get());
    }

    @Test
    @DisplayName("UT doFilter() when the correlation id is blank should replace it with a new one")
    void doFilter_whenCorrelationIdIsBlank_shouldReplaceItWithNewOne() throws Exception {
        // when
        tested.doFilter(request("  "), new MockHttpServletResponse(), recordingChain());

        // then
        assertThat(UUID.fromString(headerInChain.get())).isNotNull();
        assertThat(mdcInChain.get()).isEqualTo(headerInChain.get());
    }

    @Test
    @DisplayName("UT doFilter() when the correlation id is too long should leave it to validation and not log it")
    void doFilter_whenCorrelationIdIsTooLong_shouldLeaveItToValidationAndNotLogIt() throws Exception {
        // given
        String tooLong = "x".repeat(CorrelationIdFilter.MAX_LENGTH + 1);

        // when
        tested.doFilter(request(tooLong), new MockHttpServletResponse(), recordingChain());

        // then
        assertThat(mdcInChain.get()).isNull();
        assertThat(headerInChain.get()).isEqualTo(tooLong);
    }

    @Test
    @DisplayName("UT doFilter() when the client sends a correlation id should echo it as the response header")
    void doFilter_whenClientSendsCorrelationId_shouldEchoItInTheResponseHeader() throws Exception {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        tested.doFilter(request("checkout-1"), response, recordingChain());

        // then
        assertThat(response.getHeader(HEADER)).isEqualTo("checkout-1");
    }

    @Test
    @DisplayName("UT doFilter() when no correlation id is sent should echo the generated one as the response header")
    void doFilter_whenNoCorrelationIdIsSent_shouldEchoGeneratedOneInTheResponseHeader() throws Exception {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        tested.doFilter(request(null), response, recordingChain());

        // then
        assertThat(UUID.fromString(response.getHeader(HEADER))).isNotNull();
        assertThat(response.getHeader(HEADER)).isEqualTo(mdcInChain.get());
    }

    @Test
    @DisplayName("UT doFilter() when the correlation id is blank should echo the replacement as the response header")
    void doFilter_whenCorrelationIdIsBlank_shouldEchoReplacementInTheResponseHeader() throws Exception {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        tested.doFilter(request("  "), response, recordingChain());

        // then
        assertThat(response.getHeader(HEADER)).isEqualTo(headerInChain.get());
        assertThat(UUID.fromString(response.getHeader(HEADER))).isNotNull();
    }

    @Test
    @DisplayName("UT doFilter() when the correlation id is too long should not echo it as the response header")
    void doFilter_whenCorrelationIdIsTooLong_shouldNotEchoItInTheResponseHeader() throws Exception {
        // given
        String tooLong = "x".repeat(CorrelationIdFilter.MAX_LENGTH + 1);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        tested.doFilter(request(tooLong), response, recordingChain());

        // then
        assertThat(response.getHeader(HEADER)).isNull();
    }

    @Test
    @DisplayName("UT doFilter() when the request ends should clear the MDC, keys put downstream included")
    void doFilter_whenRequestEnds_shouldClearMdcWithKeysPutDownstream() throws Exception {
        // when
        tested.doFilter(request("checkout-1"), new MockHttpServletResponse(),
                (request, response) -> MDC.put("orderId", "7"));

        // then
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("UT doFilter() when the chain fails should still clear the MDC")
    void doFilter_whenChainFails_shouldStillClearMdc() {
        // given
        FilterChain failing = (request, response) -> {
            MDC.put("orderId", "7");
            throw new ServletException("boom");
        };

        // when / then
        assertThatThrownBy(() -> tested.doFilter(request("checkout-1"), new MockHttpServletResponse(), failing))
                .isInstanceOf(ServletException.class);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    private FilterChain recordingChain() {
        return (request, response) -> {
            mdcInChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            headerInChain.set(((HttpServletRequest) request).getHeader(HEADER));
        };
    }

    private static MockHttpServletRequest request(String correlationId) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        if (correlationId != null) {
            request.addHeader(HEADER, correlationId);
        }
        return request;
    }
}
