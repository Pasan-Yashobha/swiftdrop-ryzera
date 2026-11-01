package lk.swiftdrop.merchant.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lk.swiftdrop.merchant.order.dto.Money;
import lk.swiftdrop.merchant.testsupport.CredentialFixtures.IssuedKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** Simultaneous requests: the unique constraints, not luck, decide who books the parcel. */
class OrderConcurrencyTest extends AbstractOrderTest {

    private static final int THREADS = 8;

    private final ExecutorService pool = Executors.newFixedThreadPool(THREADS);

    @BeforeEach
    void slowParcelService() throws Exception {
        // Slow enough that the other requests arrive while the first one is still waiting for the parcel service.
        doAnswer(invocation -> {
            Thread.sleep(400);
            return new ParcelBooking("parcel-1", "SD24000001", new Money(new BigDecimal("450.00"), "LKR"));
        }).when(parcelClient).createParcel(any());
    }

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    /** Runs all requests at the same moment and returns their responses. */
    private List<MockHttpServletResponse> fireTogether(List<Callable<MockHttpServletResponse>> requests)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
        for (Callable<MockHttpServletResponse> request : requests) {
            futures.add(pool.submit(() -> {
                start.await();
                return request.call();
            }));
        }
        start.countDown();
        List<MockHttpServletResponse> responses = new ArrayList<>();
        for (Future<MockHttpServletResponse> future : futures) {
            try {
                responses.add(future.get(30, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                throw new AssertionError("a request failed with an exception", e.getCause());
            }
        }
        return responses;
    }

    private Callable<MockHttpServletResponse> create(IssuedKey key, String externalRef, String idempotencyKey) {
        return () -> createViaApi(key, OrderFixtures.payload(externalRef), idempotencyKey)
                .andReturn().getResponse();
    }

    @Test
    void simultaneousRequestsWithTheSameKeyBookExactlyOneParcel() throws Exception {
        IssuedKey key = liveKey();
        List<Callable<MockHttpServletResponse>> requests = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            requests.add(create(key, "ORDER-1", "same-key"));
        }

        List<MockHttpServletResponse> responses = fireTogether(requests);

        List<Integer> statuses = responses.stream().map(MockHttpServletResponse::getStatus).toList();
        assertThat(statuses).isSubsetOf(200, 201, 409).filteredOn(status -> status == 201).hasSize(1);
        for (MockHttpServletResponse response : responses) {
            if (response.getStatus() == 409) {
                assertThat(response.getContentAsString()).contains("IDEMPOTENCY_KEY_IN_USE");
                assertThat(response.getHeader("Retry-After")).isEqualTo("1");
            }
        }
        verify(parcelClient, times(1)).createParcel(any());
        assertThat(orders.findAll()).singleElement()
                .satisfies(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED));

        createViaApi(key, OrderFixtures.payload("ORDER-1"), "same-key").andExpect(status().isOk());
        verify(parcelClient, times(1)).createParcel(any());
    }

    @Test
    void simultaneousRequestsWithTheSameExternalRefCreateOneOrder() throws Exception {
        IssuedKey key = liveKey();
        List<Callable<MockHttpServletResponse>> requests = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            requests.add(create(key, "ORDER-1", "key-" + i));
        }

        List<MockHttpServletResponse> responses = fireTogether(requests);

        List<Integer> statuses = responses.stream().map(MockHttpServletResponse::getStatus).toList();
        assertThat(statuses).isSubsetOf(201, 409).filteredOn(status -> status == 201).hasSize(1);
        for (MockHttpServletResponse response : responses) {
            if (response.getStatus() == 409) {
                assertThat(response.getContentAsString()).contains("DUPLICATE_EXTERNAL_REF");
            }
        }
        verify(parcelClient, times(1)).createParcel(any());
        assertThat(orders.count()).isEqualTo(1);
    }

    @Test
    void simultaneousRequestsForDifferentOrdersAreAllCreated() throws Exception {
        bookParcelsSuccessfully();
        IssuedKey key = liveKey();
        List<Callable<MockHttpServletResponse>> requests = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            requests.add(create(key, "ORDER-" + i, "key-" + i));
        }

        List<MockHttpServletResponse> responses = fireTogether(requests);

        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsOnly(201);
        assertThat(orders.count()).isEqualTo(THREADS);
        verify(parcelClient, times(THREADS)).createParcel(any());
    }
}
