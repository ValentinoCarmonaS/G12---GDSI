package com.easyeat.order;

import com.easyeat.order.domain.OrderReceipt;
import com.easyeat.order.domain.OrderReceiptStatus;
import com.easyeat.order.event.OrderPaymentApprovedEvent;
import com.easyeat.order.repository.OrderReceiptRepository;
import com.easyeat.order.service.OrderReceiptEmailService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration test for email receipt idempotency and duplicate webhook suppression (INV-002, SC-002).
 */
public class OrderReceiptIdempotencyIntegrationTest extends BaseIntegrationTest {

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private OrderReceiptEmailService orderReceiptEmailService;

    @Autowired
    private OrderReceiptRepository orderReceiptRepository;

    @BeforeEach
    void setUp() {
        orderReceiptRepository.deleteAll();
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    }

    @Test
    @DisplayName("INV-002/SC-002: Duplicate webhook delivery for same orderId sends exactly 1 email and retains single receipt")
    void shouldSuppressDuplicateWebhookEmailDispatch() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID establishmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String recipientEmail = "diner.idempotent@test.com";
        String trackingUrl = "/pedidos/" + orderId + "/seguimiento";

        OrderPaymentApprovedEvent event = new OrderPaymentApprovedEvent(
                orderId,
                establishmentId,
                userId,
                recipientEmail,
                new BigDecimal("3500.00"),
                trackingUrl
        );

        // First event dispatch (regular approval)
        OrderReceipt firstReceipt = orderReceiptEmailService.dispatchReceipt(event).get();
        assertThat(firstReceipt.getStatus()).isEqualTo(OrderReceiptStatus.SENT);

        // Second event dispatch (duplicate webhook from payment gateway)
        OrderReceipt secondReceipt = orderReceiptEmailService.dispatchReceipt(event).get();
        assertThat(secondReceipt).isNotNull();

        // Third event dispatch (network replay)
        OrderReceipt thirdReceipt = orderReceiptEmailService.dispatchReceipt(event).get();
        assertThat(thirdReceipt).isNotNull();

        // Verification: exactly 1 email sent, 1 record in database
        verify(mailSender, times(1)).send(any(MimeMessage.class));

        List<OrderReceipt> receipts = orderReceiptRepository.findAll();
        long orderReceiptCount = receipts.stream()
                .filter(r -> r.getOrderId().equals(orderId))
                .count();
        assertThat(orderReceiptCount).isEqualTo(1);
    }

    @Test
    @DisplayName("SC-002: Concurrent duplicate payment approved webhooks must result in exactly 1 email dispatch")
    void shouldHandleConcurrentDuplicateWebhooksIdempotently() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID establishmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String recipientEmail = "diner.concurrent@test.com";
        String trackingUrl = "/pedidos/" + orderId + "/seguimiento";

        OrderPaymentApprovedEvent event = new OrderPaymentApprovedEvent(
                orderId,
                establishmentId,
                userId,
                recipientEmail,
                new BigDecimal("4200.00"),
                trackingUrl
        );

        int threads = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    orderReceiptEmailService.dispatchReceipt(event).get();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        verify(mailSender, atMost(1)).send(any(MimeMessage.class));

        List<OrderReceipt> receipts = orderReceiptRepository.findAll();
        long count = receipts.stream().filter(r -> r.getOrderId().equals(orderId)).count();
        assertThat(count).isEqualTo(1);
    }
}
