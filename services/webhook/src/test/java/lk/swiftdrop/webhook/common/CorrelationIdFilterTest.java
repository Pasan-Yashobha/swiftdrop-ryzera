package lk.swiftdrop.webhook.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void suppliedIdIsUsedForTheRequestAndEchoed() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInChain = new AtomicReference<>();

        filter.doFilter(requestWith("gateway-7f3a.1:2"), response,
                (req, res) -> seenInChain.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(seenInChain.get()).isEqualTo("gateway-7f3a.1:2");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("gateway-7f3a.1:2");
    }

    @Test
    void missingIdIsGenerated() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> { });

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).matches(UUID_PATTERN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"has spaces", "line\r\nbreak: injected", "<script>", "", "this-id-is-far-too-long-this-id-is-far-too-long-this-id-is-far-too-long-xx"})
    void unsafeIdsAreReplaced(String supplied) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWith(supplied), response, (req, res) -> { });

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).matches(UUID_PATTERN);
    }

    @Test
    void idIsRemovedFromTheLogContextAfterTheRequest() throws Exception {
        filter.doFilter(requestWith("abc"), new MockHttpServletResponse(), (req, res) -> { });

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void idIsRemovedEvenWhenTheRequestFails() {
        try {
            filter.doFilter(requestWith("abc"), new MockHttpServletResponse(), (req, res) -> {
                throw new IllegalStateException("boom");
            });
        } catch (Exception expected) {
            assertThat(expected).isInstanceOf(IllegalStateException.class);
        }

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void currentOrNewReturnsTheActiveIdOrAFreshOne() {
        assertThat(CorrelationIdFilter.currentOrNew()).matches(UUID_PATTERN);

        MDC.put(CorrelationIdFilter.MDC_KEY, "active-id");
        assertThat(CorrelationIdFilter.currentOrNew()).isEqualTo("active-id");
    }

    private static MockHttpServletRequest requestWith(String correlationId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, correlationId);
        return request;
    }
}
