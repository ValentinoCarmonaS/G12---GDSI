package com.easyeat.order.service;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderReceipt;
import com.easyeat.order.domain.OrderReceiptStatus;
import com.easyeat.order.event.OrderPaymentApprovedEvent;
import com.easyeat.order.repository.OrderReceiptRepository;
import com.easyeat.order.repository.OrderRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service for universal, asynchronous and idempotent order receipt email dispatch.
 * Conforms to AS-2, FR-002, INV-002, CB-002.
 */
@Service
public class OrderReceiptEmailService {

    private static final Logger log = LoggerFactory.getLogger(OrderReceiptEmailService.class);

    private final JavaMailSender mailSender;
    private final OrderReceiptRepository orderReceiptRepository;
    private final OrderRepository orderRepository;
    private final EstablishmentRepository establishmentRepository;
    private final String fromEmail;
    private final String frontendBaseUrl;

    public OrderReceiptEmailService(
            @Autowired(required = false) JavaMailSender mailSender,
            OrderReceiptRepository orderReceiptRepository,
            OrderRepository orderRepository,
            EstablishmentRepository establishmentRepository,
            @Value("${app.mail.from:${spring.mail.username:noreply@easyeat.com}}") String fromEmail,
            @Value("${mercadopago.frontend-base-url:http://localhost:3000}") String frontendBaseUrl
    ) {
        this.mailSender = mailSender;
        this.orderReceiptRepository = orderReceiptRepository;
        this.orderRepository = orderRepository;
        this.establishmentRepository = establishmentRepository;
        this.fromEmail = fromEmail != null && !fromEmail.isBlank() ? fromEmail.trim() : "noreply@easyeat.com";
        this.frontendBaseUrl = frontendBaseUrl != null && !frontendBaseUrl.isBlank() ? frontendBaseUrl.trim().replaceAll("/+$", "") : "http://localhost:3000";
    }

    public String getFromEmail() {
        return fromEmail;
    }

    /**
     * Dispatches order confirmation receipt asynchronously.
     * Guarantees idempotency via unique constraints on order_id and idempotency_key.
     */
    @Async
    @Transactional
    public CompletableFuture<OrderReceipt> dispatchReceipt(OrderPaymentApprovedEvent event) {
        if (event == null || event.orderId() == null) {
            log.warn("Null event or orderId provided to dispatchReceipt. Skipping.");
            return CompletableFuture.completedFuture(null);
        }

        String idempotencyKey = "receipt-" + event.orderId();

        // 1. Check idempotency: if already SENT, suppress duplicate dispatch
        Optional<OrderReceipt> existing = orderReceiptRepository.findByOrderId(event.orderId());
        if (existing.isPresent() && existing.get().getStatus() == OrderReceiptStatus.SENT) {
            log.info("Order receipt already sent for orderId={}. Suppressing duplicate dispatch.", event.orderId());
            return CompletableFuture.completedFuture(existing.get());
        }

        // 2. Obtain or create OrderReceipt entity
        OrderReceipt receipt;
        if (existing.isPresent()) {
            receipt = existing.get();
        } else {
            receipt = new OrderReceipt(event.orderId(), event.recipientEmail(), event.trackingUrl(), idempotencyKey);
            try {
                receipt = orderReceiptRepository.saveAndFlush(receipt);
            } catch (Exception e) {
                // In case of concurrent insert race condition, fetch the record
                Optional<OrderReceipt> raced = orderReceiptRepository.findByOrderId(event.orderId());
                if (raced.isPresent() && raced.get().getStatus() == OrderReceiptStatus.SENT) {
                    return CompletableFuture.completedFuture(raced.get());
                }
                receipt = raced.orElse(receipt);
            }
        }

        // 3. Resolve establishment name and order context
        String establishmentName = "EasyEat";
        if (event.establishmentId() != null) {
            establishmentName = establishmentRepository.findById(event.establishmentId())
                    .map(Establishment::getName)
                    .orElse("EasyEat");
        }

        // 4. Send email via JavaMailSender
        if (mailSender == null) {
            log.warn("JavaMailSender not configured. Marking receipt as SENT without SMTP dispatch.");
            receipt.markSent();
            orderReceiptRepository.save(receipt);
            return CompletableFuture.completedFuture(receipt);
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(event.recipientEmail());
            helper.setSubject("EasyEat - Comprobante de Pedido en " + establishmentName);

            String htmlBody = buildHtmlReceipt(event, establishmentName);
            helper.setText(htmlBody, true);

            mailSender.send(mimeMessage);

            receipt.markSent();
            orderReceiptRepository.save(receipt);
            log.info("Email receipt sent successfully for orderId={} to {}", event.orderId(), event.recipientEmail());
        } catch (Exception e) {
            log.error("Failed to dispatch email receipt for orderId={}: {}", event.orderId(), e.getMessage());
            receipt.markFailed(e.getMessage());
            orderReceiptRepository.save(receipt);
            // Universal guarantee: do not crash client flow
        }

        return CompletableFuture.completedFuture(receipt);
    }

    private String buildHtmlReceipt(OrderPaymentApprovedEvent event, String establishmentName) {
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head><meta charset="UTF-8"><title>Comprobante de Compra - EasyEat</title></head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f9fafb; margin: 0; padding: 24px; color: #111827;">
              <div style="max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 16px; border: 1px solid #e5e7eb; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);">
                <div style="background-color: #059669; padding: 24px; text-align: center; color: #ffffff;">
                  <h1 style="margin: 0; font-size: 24px; font-weight: 800;">¡Pago Confirmado!</h1>
                  <p style="margin: 4px 0 0 0; font-size: 14px; opacity: 0.9;">Tu pedido ya está registrado en %s</p>
                </div>
                <div style="padding: 24px;">
                  <div style="background-color: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 12px; padding: 16px; text-align: center; margin-bottom: 24px;">
                    <p style="margin: 0; font-size: 13px; color: #166534; font-weight: 600;">ID de Pedido</p>
                    <p style="margin: 4px 0 0 0; font-size: 18px; color: #14532d; font-family: monospace; font-weight: 700;">%s</p>
                  </div>
                  <div style="margin-bottom: 24px;">
                    <h3 style="font-size: 16px; margin: 0 0 12px 0; color: #374151;">Resumen de Transacción</h3>
                    <p style="margin: 4px 0; font-size: 14px;"><strong>Establecimiento:</strong> %s</p>
                    <p style="margin: 4px 0; font-size: 14px;"><strong>Total Abonado:</strong> $%s</p>
                    <p style="margin: 4px 0; font-size: 14px;"><strong>Medio de Pago:</strong> Aprobado vía Pasarela Segura</p>
                  </div>
                  <div style="text-align: center; margin: 32px 0 16px 0;">
                    <a href="%s" style="background-color: #059669; color: #ffffff; text-decoration: none; padding: 14px 28px; border-radius: 10px; font-weight: 700; font-size: 15px; display: inline-block;">
                      Ver Seguimiento en Vivo
                    </a>
                  </div>
                </div>
                <div style="background-color: #f3f4f6; padding: 16px; text-align: center; font-size: 12px; color: #6b7280; border-top: 1px solid #e5e7eb;">
                  EasyEat &copy; 2026 - Este es un comprobante de respaldo emitido de forma automática.
                </div>
              </div>
            </body>
            </html>
            """.formatted(
                establishmentName,
                event.orderId().toString(),
                establishmentName,
                event.totalAmount() != null ? event.totalAmount().toPlainString() : "0.00",
                resolveAbsoluteTrackingUrl(event.trackingUrl())
        );
    }

    private String resolveAbsoluteTrackingUrl(String trackingUrl) {
        if (trackingUrl == null || trackingUrl.isBlank()) {
            return "#";
        }
        if (trackingUrl.startsWith("http://") || trackingUrl.startsWith("https://")) {
            return trackingUrl;
        }
        String cleanPath = trackingUrl.startsWith("/") ? trackingUrl : "/" + trackingUrl;
        return frontendBaseUrl + cleanPath;
    }
}
