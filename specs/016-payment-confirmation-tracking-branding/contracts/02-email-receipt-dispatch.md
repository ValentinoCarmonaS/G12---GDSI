# Contrato 02: Despacho Garantizado del Comprobante por Correo Electrónico

**Tipo de Contrato**: Evento de Dominio y Servicio Transaccional Asíncrono  
**Componente Responsable**: `com.easyeat.order.service.OrderReceiptEmailService`  
**Disparador**: Publicación del evento `OrderPaymentApprovedEvent` (o aprobación de pago con Mercado Pago / orden bonificada al 100%)  
**Canal**: SMTP (`JavaMailSender` / `EmailSender`)  
**Trazabilidad**: Cubre **AS-2**, **FR-002**, **INV-002**, **CB-002**, **Q4/A4**, **SC-002**.

---

## 1. Descripción

Este contrato gobierna la emisión universal, automática y asíncrona del comprobante de orden ante todo pago aprobado.  
- **Gobernanza Servidor (CB-002)**: La emisión se produce de forma autónoma en el backend al procesarse el resultado autoritativo de la pasarela, garantizando el envío aun si el comensal cierra el navegador o pierde la conexión antes de visualizar la pantalla de éxito.
- **Idempotencia Estricta (INV-002)**: Cada orden posee exactamente un registro en `order_receipts` con clave `idempotency_key = "receipt-" + orderId`. Reintentos de webhooks no generan correos duplicados.

---

## 2. Definición del Evento de Dominio (Java Record)

```java
package com.easyeat.order.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record OrderPaymentApprovedEvent(
    UUID orderId,
    Integer ticketNumber,
    UUID establishmentId,
    String establishmentName,
    UUID userId,
    String recipientEmail,
    String recipientFullName,
    String fulfillmentMode,
    LocalDate pickupDate,
    LocalTime timeSlotStart,
    LocalTime timeSlotEnd,
    BigDecimal totalAmount,
    List<ReceiptItemSnapshot> items,
    String trackingUrl
) {
    public record ReceiptItemSnapshot(
        UUID dishId,
        String dishName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
    ) {}
}
```

---

## 3. Estructura del Mensaje de Correo Electrónico

### Cabeceras
- **De**: `noreply@easyeat.com` (o valor de `${app.mail.from}`)
- **Para**: `{recipientEmail}`
- **Asunto**: `EasyEat - Comprobante de Pedido #{ticketNumber} en {establishmentName}`
- **Content-Type**: `text/html; charset=UTF-8`

### Contenido HTML Mínimo Garantizado
1. **Encabezado**: Logo institucional de EasyEat o logotipo del restaurante y denominación comercial.
2. **Número de Pedido**: `# {ticketNumber}` destacado.
3. **Franja Horaria de Retiro**: `{pickupDate} de {timeSlotStart} a {timeSlotEnd} hs` (Modalidad: Inmediata / Programada).
4. **Tabla de Ítems**: Cantidad, descripción de plato, precio unitario y subtotal.
5. **Total Liquidado**: Monto monetario final con indicación `"Abonado mediante Mercado Pago"`.
6. **Botón Principal (CTA)**: Enlace directo y seguro a `{trackingUrl}` para dar seguimiento en vivo al pedido.
7. **Pie**: Datos de contacto del establecimiento y aviso de confidencialidad.

---

## 4. Semántica de Idempotencia y Reintentos

1. El servicio comprueba la existencia de un comprobante previo mediante `orderReceiptRepository.findByOrderId(orderId)`.
2. Si ya existe en estado `SENT`, omite el despacho de forma silenciosa y segura.
3. Si no existe, persiste la entidad `OrderReceipt` en estado `PENDING`.
4. Ejecuta el envío vía `mailSender.send(...)`.
5. Si el servidor SMTP confirma la recepción:
   - Actualiza `status = 'SENT'`, `sent_at = Instant.now()`.
6. Si ocurre una excepción transitoria de red:
   - Incrementa `retry_count += 1`.
   - Registra `error_message = e.getMessage()`.
   - Si `retry_count < 3`, programa reintento con backoff exponencial sin interrumpir el flujo principal de compra ni arrojar excepciones al cliente HTTP.

