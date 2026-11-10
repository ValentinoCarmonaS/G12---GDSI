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
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration test verifying guaranteed, asynchronous and unconditional email receipt dispatch
 * upon payment approval (AS-2, FR-002, INV-002, CB-002).
 */
public class OrderReceiptEmailDispatchIntegrationTest extends BaseIntegrationTest {

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
    @DisplayName("AS-2/FR-002: Should dispatch email receipt asynchronously and persist OrderReceipt with status SENT")
    void shouldDispatchEmailReceiptAndPersistSentStatus() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID establishmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String recipientEmail = "diner.approved@test.com";
        String trackingUrl = "/pedidos/" + orderId + "/seguimiento";

        OrderPaymentApprovedEvent event = new OrderPaymentApprovedEvent(
                orderId,
                establishmentId,
                userId,
                recipientEmail,
                new BigDecimal("4850.00"),
                trackingUrl
        );

        OrderReceipt receipt = orderReceiptEmailService.dispatchReceipt(event).get();

        assertThat(receipt).isNotNull();
        assertThat(receipt.getOrderId()).isEqualTo(orderId);
        assertThat(receipt.getRecipientEmail()).isEqualTo(recipientEmail);
        assertThat(receipt.getStatus()).isEqualTo(OrderReceiptStatus.SENT);
        assertThat(receipt.getSentAt()).isNotNull();
        assertThat(receipt.getIdempotencyKey()).isEqualTo("receipt-" + orderId);

        Optional<OrderReceipt> persisted = orderReceiptRepository.findByOrderId(orderId);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getStatus()).isEqualTo(OrderReceiptStatus.SENT);
        assertThat(persisted.get().getTrackingUrl()).isEqualTo(trackingUrl);

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("CB-002/INV-002: Should record FAILED status gracefully without throwing exception when mail server fails")
    void shouldHandleMailExceptionGracefullyAndPersistFailedStatus() {
        doThrow(new MailSendException("SMTP connection refused"))
                .when(mailSender).send(any(MimeMessage.class));

        UUID orderId = UUID.randomUUID();
        UUID establishmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String recipientEmail = "diner.failure@test.com";
        String trackingUrl = "/pedidos/" + orderId + "/seguimiento";

        OrderPaymentApprovedEvent event = new OrderPaymentApprovedEvent(
                orderId,
                establishmentId,
                userId,
                recipientEmail,
                new BigDecimal("1200.00"),
                trackingUrl
        );

        assertThatCode(() -> {
            try {
                OrderReceipt receipt = orderReceiptEmailService.dispatchReceipt(event).get();
                assertThat(receipt.getStatus()).isEqualTo(OrderReceiptStatus.FAILED);
                assertThat(receipt.getErrorMessage()).contains("SMTP connection refused");
                assertThat(receipt.getRetryCount()).isGreaterThanOrEqualTo(1);
            } catch (ExecutionException e) {
                throw (Exception) e.getCause();
            }
        }).doesNotThrowAnyException();

        Optional<OrderReceipt> persisted = orderReceiptRepository.findByOrderId(orderId);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getStatus()).isEqualTo(OrderReceiptStatus.FAILED);
        assertThat(persisted.get().getErrorMessage()).contains("SMTP connection refused");
    }
}
