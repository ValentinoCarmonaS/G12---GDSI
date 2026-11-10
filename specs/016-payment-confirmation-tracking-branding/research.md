# Research: Confirmación de Pago, Tiempo Estimado en Vivo y Branding del Restaurante

**Feature Branch**: `016-payment-confirmation-tracking-branding`  
**Fecha**: 09/11/2026  
**Especificación**: [spec.md](./spec.md)  
**Estado**: Completado  

---

## 1. Resumen Ejecutivo de Decisiones

El presente documento de investigación resuelve las decisiones técnicas y patrones de arquitectura para la implementación de la **Spec 016**, que materializa la experiencia posterior al pago del comensal (`COM-7.3.1`), el seguimiento dinámico del pedido en vivo (`COM-7.3.2`) y la personalización de la identidad visual de marca del restaurante (`ADM-2.1.1`), respetando estrictamente las bases operativas consolidadas en las iteraciones previas (Specs 001, 002, 004, 009, 010, 012, 013, 014 y 015).

---

## 2. Decisiones Técnicas y Justificación

### D-01: Despacho Universal, Asíncrono e Idempotente del Comprobante por Correo Electrónico

| Aspecto | Definición |
|---|---|
| **Decisión** | Se adopta el despacho desacoplado y universal del comprobante formal mediante el evento de dominio `OrderPaymentApprovedEvent`, procesado de manera asíncrona (`@Async`) por `OrderReceiptEmailService`, garantizando su ejecución en segundo plano ante toda orden aprobada por la pasarela de pagos. Se introduce la tabla `order_receipts` con clave foránea unívoca `order_id UNIQUE` para asegurar idempotencia estricta en el despacho y trazabilidad de entrega. |
| **Justificación** | Cumple directamente con **AS-2**, **FR-002**, **INV-002**, **CB-002** y **Q4/A4**. Al estar totalmente desacoplado del ciclo HTTP del comensal, si el navegador sufre una interrupción (cierre de pestaña, caída de red móvil, descarga de batería o recarga forzada), el servidor procesa de forma autónoma la notificación autoritativa de la pasarela y envía el comprobante a la casilla registrada. La clave única en base de datos impide duplicar correos electrónicos ante reintentos de webhooks de Mercado Pago. |
| **Alternativas Rechazadas** | - *Envío síncrono en el controlador HTTP de retorno*: Rechazado porque añade entre 500ms y 2000ms de latencia al usuario, y si el comensal cierra el navegador antes de que el servidor responda, la transacción puede quedar en estado ambiguo.<br>- *Barrido periódico por cron*: Rechazado porque introduciría demoras de varios minutos en la recepción del comprobante, afectando negativamente la percepción de inmediatez del comensal.<br>- *Envío ciego sin registro de auditoría*: Rechazado porque no permite verificar idempotencia ante webhooks repetidos de pasarela ni auditar fallos transitorios de red SMTP. |

---

### D-02: Patrón de Actualización en Vivo para el Seguimiento del Pedido

| Aspecto | Definición |
|---|---|
| **Decisión** | Se adopta **Short HTTP Polling Inteligente** gestionado por el hook personalizado `useOrderLiveTrackingPolling`, con cadencia adaptativa: 5000 ms durante preparación activa en KDS; 3000 ms al ingresar en la ventana de proximidad (T-15 minutos) o en preparación final; y detención inmediata del polling al alcanzar el estado terminal `READY_FOR_PICKUP` o `COMPLETED`. El hook se integra obligatoriamente con la Page Visibility API (`document.visibilityState`) para suspender peticiones en segundo plano y utiliza `AbortController` para cancelar solicitudes redundantes. |
| **Justificación** | Máxima consistencia y reutilización de la infraestructura existente en el repositorio (patrón consolidado en `useTimeSlotPolling.ts`). Cero dependencias adicionales en backend y frontend. Funciona de manera 100% determinística y tolerante a fallos a través de proxies reversos, balanceadores y conexiones móviles inestables sin requerir soporte stateful en Tomcat. Cumple con el principio de simplicidad justificada (**Principio IV de la Constitución**) y permite reflejar ajustes del KDS en menos de 5 segundos de forma silenciosa (**AS-7**, **Q6/A6**). |
| **Alternativas Rechazadas** | - *WebSockets con STOMP/SockJS*: Rechazado por sobrediseño injustificado; requiere broker de mensajería, gestión de desconexiones, reconexión con backoff, y manejo de sesiones en clúster que el repositorio no posee actualmente.<br>- *Server-Sent Events (SSE)*: Rechazado porque puede presentar incompatibilidades con proxies corporativos HTTP/1.1 que mantienen buffers de respuesta, y no existe infraestructura de streaming previa en el proyecto.<br>- *Refetch únicamente manual*: Rechazado porque violaría explícitamente **AS-7** e **INV-007**, que exigen actualización reactiva y silenciosa sin recarga manual de la pantalla. |

---

### D-03: Cálculo Dinámico del Tiempo Estimado de Entrega (Retiro Inmediato)

| Aspecto | Definición |
|---|---|
| **Decisión** | El backend calcula la estimación de tiempo de entrega para órdenes de retiro inmediato (`IMMEDIATE`) mediante la fórmula determinística:<br>`estimatedMinutes = maxPreparationTime + (activeQueuedTickets * minutesPerTicket) + globalDelayMinutes`<br>donde:<br>- `maxPreparationTime`: tiempo base de elaboración del plato más complejo de la orden (obtenido del plato o `lead_time_minutes` del establecimiento, por defecto 20 min).<br>- `activeQueuedTickets`: recuento en tiempo real de comandas en estado `QUEUED` o `IN_PREPARATION` asignadas al mismo establecimiento en la tabla `kds_tickets`.<br>- `minutesPerTicket`: factor de saturación operativa de cocina (2 minutos adicionales por comanda previa).<br>- `globalDelayMinutes`: ajuste manual global registrado por el personal del KDS en `establishment_kds_settings` (por defecto 0 min). |
| **Justificación** | Da cumplimiento irrestricto a **AS-5**, **FR-005**, **INV-005** y **CB-004**. La métrica no es un número estático sino una función reactiva del volumen de trabajo real en cocina. Al mismo tiempo, permite al personal de cocina introducir demoras imprevistas que se sincronizan de forma limpia y transparente con el comensal (**AS-7**, **INV-007**). |
| **Alternativas Rechazadas** | - *Tiempo estático inmutable (ej. siempre 30 min)*: Viola terminantemente la invariante **INV-005** y desinforma al comensal ante sobrecargas del restaurante.<br>- *Algoritmos estocásticos o Machine Learning*: Sobrediseño desmedido e innecesario, fuera de alcance explícito de la especificación funcional.<br>- *Asignación manual comanda por comanda por el cocinero*: Impráctico operativamente durante horas pico de servicio en la brigada de cocina. |

---

### D-04: Regla Temporal para Reservas Programadas (Umbral T-15 Minutos)

| Aspecto | Definición |
|---|---|
| **Decisión** | Para órdenes con modalidad `SCHEDULED`, el backend y el frontend distinguen estrictamente dos estados temporales respecto al inicio de la franja horaria pactada (`time_slot_start`):<br>1. **Fase Estática (`now < time_slot_start - 15 minutos`)**: La interfaz exhibe de forma fija y destacada la fecha y franja acordada (ej. `"Turno programado: 14:00 - 14:30 hs"`), prohibiendo expresamente el despliegue de un contador regresivo.<br>2. **Fase de Proximidad Activa (`now >= time_slot_start - 15 minutos`)**: La interfaz conmuta de forma automática y reactiva a un reloj regresivo dinámico que cuenta los minutos y segundos restantes hasta el inicio del turno pactado. |
| **Justificación** | Cumple rigurosamente con **AS-6**, **FR-006**, **INV-006**, **Q1/A1**, **CB-005** y **CB-006**. Armoniza con el tamaño unitario estándar de las franjas horarias de EasyEat (15 minutos, definido en Spec 010 y Spec 012). Evita la ansiedad del usuario cuando reserva para el día siguiente o para turnos lejanos, y activa el llamado oportuno al aproximarse la hora de retiro. |
| **Alternativas Rechazadas** | - *Contador regresivo continuo desde la compra (ej. faltan 18 horas y 42 minutos)*: Genera ruido visual excesivo y puede inducir a error en el usuario al interpretar que la cocina ya está elaborando su plato en horarios donde el local está cerrado.<br>- *Cuenta regresiva basada en el fin de la franja*: Confuso, ya que el turno del comensal inicia al comienzo de la franja asignada. |

---

### D-05: Transición Irreversible a "Listo para Retirar" y Supresión del Tiempo

| Aspecto | Definición |
|---|---|
| **Decisión** | Cuando el personal del KDS o mostrador marca la orden como finalizada en la interfaz de cocina, el backend actualiza `kds_tickets.status = 'READY_FOR_PICKUP'` (y `orders.status = 'READY_FOR_PICKUP'`). En la siguiente lectura del comensal (vía polling o retorno):<br>1. El tiempo dinámico y la barra de progreso se suprimen de inmediato.<br>2. La pantalla realiza una transición visual destacada a un contenedor verde esmeralda vibrante con el mensaje `"¡Tu pedido está listo para retirar!"`.<br>3. Se expone el número de orden en tipografía de gran formato (`text-5xl font-black`) para su fácil visualización e identificación en el mostrador.<br>4. Se desactiva definitivamente el polling del cliente.<br>5. Esta transición es irreversible en la vista del comensal. |
| **Justificación** | Cumple con **AS-8**, **FR-008**, **INV-008** y **CB-008**. Garantiza una experiencia clara y libre de ambigüedades en el punto físico de entrega, optimizando el despacho en el mostrador y evitando aglomeraciones. |
| **Alternativas Rechazadas** | - *Dejar el minutero en "00:00"*: Induce a confusión sobre si la comida está verdaderamente lista o si la cocina está demorada.<br>- *Permitir reversión desde mostrador*: Viola **INV-008**; una vez anunciado el retiro al comensal, revertir a "en preparación" deteriora gravemente la confianza del cliente. |

---

### D-06: Convivencia Unificada y Persistencia de Confirmación y Seguimiento

| Aspecto | Definición |
|---|---|
| **Decisión** | Se diseña una vista de experiencia unificada `OrderConfirmationTrackingPage.tsx` bajo la ruta `/checkout/confirmacion/:orderId` (con alias `/pedidos/:orderId/seguimiento`). Esta vista presenta de manera simultánea:<br>- El encabezado de confirmación verde con icono de éxito, animación de verificación y número de orden visible (**AS-1**, **AS-3**).<br>- El desglose de la reserva (franja horaria pactada, modalidad, platos e importe total liquidado).<br>- El widget interactivo de seguimiento en vivo con su estado actual (preparación dinámica, horario fijo, cuenta regresiva a 15 min o aviso de retiro listo).<br>Ante cualquier recarga de pantalla (F5), reingreso desde el correo electrónico o acceso desde historial, el endpoint `GET /api/v1/orders/{orderId}/confirmation-tracking` reconstruye íntegramente este estado unificado. |
| **Justificación** | Resuelve con exactitud **Q2/A2**, **CB-012**, **AS-1**, **AS-3** y **FR-003**. Evita fragmentar la experiencia en pantallas disjuntas donde el usuario perdería su comprobante visual al navegar al seguimiento o viceversa. |
| **Alternativas Rechazadas** | - *Dos pantallas separadas con redirección forzada*: El comensal pierde la tranquilidad del comprobante de pago al navegar al seguimiento, y el uso del botón "Atrás" del navegador suele provocar reenvíos o errores de sesión.<br>- *Almacenamiento exclusivo en sessionStorage*: Si el comensal abre el enlace desde su teléfono a partir del correo electrónico, una sesión local previa no existiría y la pantalla quedaría en blanco. |

---

### D-07: Modelo de Branding Multi-Tenant y Aislamiento por Establecimiento

| Aspecto | Definición |
|---|---|
| **Decisión** | Se crea la entidad de persistencia `RestaurantBranding` mapeada a la tabla `restaurant_brandings` con relación 1:1 estricta con `establishments` (`establishment_id UUID PRIMARY KEY REFERENCES establishments(id) ON DELETE CASCADE`). Contiene:<br>- `fantasy_name VARCHAR(150)`: Nombre de fantasía comercial.<br>- `description TEXT`: Reseña o descripción comercial del restaurante.<br>- `logo_url VARCHAR(500)`: URL de imagen o identificador de almacenamiento del logotipo.<br>- `primary_color VARCHAR(7)`: Color hexadecimal para acciones principales (CTAs, botones de pago, agregar al carrito).<br>- `secondary_color VARCHAR(7)`: Color hexadecimal para cabecera, barras de navegación y fondos de sección.<br>- `accent_color VARCHAR(7)`: Color hexadecimal para distintivos, insignias (badges), etiquetas y bordes de selección.<br>- `primary_contrast_color`, `secondary_contrast_color`, `accent_contrast_color`: Colores de contraste precalculados (`#000000` o `#FFFFFF`).<br>El aislamiento está garantizado porque toda lectura y escritura se valida contra el `tenantId` del administrador o del establecimiento consultado. |
| **Justificación** | Cumple con **AS-9**, **ADM-2.1.1**, **FR-009**, **INV-009** y **Q7/A7**. Separa limpiamente los atributos visuales y de marketing de los datos operativos de horarios y franjas (evitando sobrecargar la tabla `establishments`), manteniendo un modelo altamente cohesivo. |
| **Alternativas Rechazadas** | - *Incrustar 10 columnas directamente en `establishments`*: Sobrecarga la tabla núcleo de descubrimiento y horarios, mezclando responsabilidades operativas con diseño visual.<br>- *Archivo de configuración estático por tenant*: Imposibilita la edición interactiva en caliente por el administrador desde "Mi Local" (**AS-9**). |

---

### D-08: Validación Hexadecimal y Cálculo Automático de Contraste Accesible (WCAG 2.1)

| Aspecto | Definición |
|---|---|
| **Decisión** | 1. **Validación de Formato**: Se exige en backend y frontend la expresión regular `^#[0-9A-Fa-f]{6}$` para los tres colores.<br>2. **Cálculo de Luminancia Relativa**: Se aplica la fórmula estándar de la recomendación W3C WCAG 2.1:<br>Para cada canal $C \in \{R, G, B\}$ normalizado en $[0, 1]$:<br>$$C_{lin} = \begin{cases} \frac{C}{12.92} & \text{si } C \le 0.04045 \\ \left(\frac{C + 0.055}{1.055}\right)^{2.4} & \text{si } C > 0.04045 \end{cases}$$<br>Luminancia relativa del fondo: $L = 0.2126 \cdot R_{lin} + 0.7152 \cdot G_{lin} + 0.0722 \cdot B_{lin}$.<br>3. **Asignación de Contraste**: Si $L > 0.179$ (umbral estándar de legibilidad), el texto/icono asignado es `#000000` (oscuro); si $L \le 0.179$, se asigna `#FFFFFF` (claro). Se persisten estos valores derivados y también se provee la función homóloga en TypeScript para renderizar previews instantáneos en la interfaz de "Mi Local". |
| **Justificación** | Cumple plenamente con **Q5/A5**, **AS-9**, **FR-009** y la directiva de accesibilidad. El administrador no necesita conocimientos de diseño ni de ratios de contraste; el sistema garantiza legibilidad absoluta y contraste superior a 4.5:1 sobre cualquier botón, cabecera o badge. |
| **Alternativas Rechazadas** | - *Permitir al usuario elegir manualmente el color de texto*: Riesgo altísimo de combinaciones ilegibles (ej. texto blanco sobre amarillo claro).<br>- *Fórmula simplificada de brillo promedio*: La percepción humana de luminosidad no es lineal; el canal verde contribuye más del 70% a la percepción lumínica, por lo que la fórmula WCAG es la única técnicamente correcta. |

---

### D-09: Fallback Institucional Neutro de EasyEat

| Aspecto | Definición |
|---|---|
| **Decisión** | Si un establecimiento no registra configuración de branding en `restaurant_brandings`, el servicio de consulta retorna inmediatamente la identidad predeterminada de EasyEat:<br>- `fantasyName`: `establishment.getName()` (nombre registrado en alta operativa).<br>- `description`: `"Restaurante asociado a EasyEat"` o texto base registrado.<br>- `logoUrl`: `"/assets/brand/easyeat-logo-neutral.svg"` (isotipo predeterminado).<br>- `primaryColor`: `"#10B981"` (Verde Esmeralda institucional, contraste `#FFFFFF`).<br>- `secondaryColor`: `"#065F46"` (Verde Oscuro de cabecera, contraste `#FFFFFF`).<br>- `accentColor`: `"#F59E0B"` (Ámbar cálido para badges, contraste `#000000`). |
| **Justificación** | Cumple con **Q3/A3**, **CB-011** e **INV-010**. Asegura que ningún comensal experimente interfaces rotas, fuentes con estilos faltantes o botones transparentes cuando un restaurante nuevo aún no configuró su estética comercial. |
| **Alternativas Rechazadas** | - *Bloquear el acceso al catálogo si no hay branding*: Destructivo para la operación comercial del restaurante.<br>- *Valores nulos en el JSON*: Obliga al frontend a plagar sus componentes de chequeos `null`, aumentando la propensión a errores de renderizado. |

---

### D-10: Almacenamiento y Carga del Logotipo del Restaurante

| Aspecto | Definición |
|---|---|
| **Decisión** | El formulario de "Mi Local" permite subir el archivo de logotipo (imágenes PNG, JPG, WEBP, SVG; tamaño máximo 2MB, validado con `spring.servlet.multipart.max-file-size=10MB`). El backend implementa `BrandingLogoStorageService`, que almacena el archivo en el sistema de almacenamiento configurado (`/uploads/logos/{establishmentId}/...`) o data URI / URL pública servida como recurso estático con cabeceras de caché inmutable (`Cache-Control: public, max-age=86400`). |
| **Justificación** | Proporciona una solución ligera, autocontenida y sin dependencias externas obligatorias de cloud storage (como S3 o GCS), totalmente compatible con el entorno de ejecución local y de pruebas del proyecto. |
| **Alternativas Rechazadas** | - *Almacenar binarios pesados en PostgreSQL (`bytea`)*: Degrada la memoria del buffer pool de base de datos y aumenta innecesariamente el tamaño de los backups relacionales.<br>- *Requerir obligatoriamente AWS S3*: Viola el principio de reproducibilidad local y costo cero en desarrollo. |

---

### D-11: Bloqueo de Interfaz y Prevención de Cobros Duplicados

| Aspecto | Definición |
|---|---|
| **Decisión** | En el momento en que el usuario acciona el botón de confirmación de pago en el checkout, el estado de React activa inmediatamente `isSubmitting = true`. Esto provoca:<br>1. Inhabilitación de los controles (`disabled={isSubmitting}`).<br>2. Sustitución del texto del botón por un spinner animado (`"Procesando pago seguro..."`).<br>3. Bloqueo de atajos de teclado y eventos de formulario.<br>4. Se respeta la clave de idempotencia enviada a Mercado Pago (`X-Idempotency-Key` por orden). Si el usuario lograse enviar dos peticiones concurrentes antes del bloqueo de UI, la restricción de base de datos y el adaptador de pago rechazan la redundancia con `HTTP 409 Conflict`. |
| **Justificación** | Cumple con **AS-4**, **FR-004**, **INV-003**, **CB-003** y **SC-004**. Combina salvaguarda preventiva en cliente con garantía matemática estricta en servidor. |
| **Alternativas Rechazadas** | - *Confiar únicamente en el frontend*: Vulnerable a herramientas de automatización o scripting.<br>- *Confiar únicamente en el backend*: Provoca una mala experiencia de usuario, permitiendo clics compulsivos que disparan múltiples llamadas de red innecesarias. |

---

### D-12: Estrategia de Testing de Extremo a Extremo y Validación Proporcional al Riesgo

| Aspecto | Definición |
|---|---|
| **Decisión** | Se diseña una matriz de verificación en capas (**Principio III de la Constitución**):<br>1. **Pruebas Unitarias**: Cálculo exacto de contraste WCAG, validación de formatos `#RRGGBB`, formateo de comprobantes de correo y lógica temporal del umbral T-15.<br>2. **Pruebas de Integración Backend (MockMvc + Testcontainers)**: Ciclo de vida de confirmación de orden, emisión asíncrona de email con mock de `JavaMailSender` verificando idempotencia (no duplicación), endpoints de KDS para ajustes globales de demora y finalización a "listo para retirar", y persistencia de branding multi-tenant.<br>3. **Pruebas de Componentes Frontend (Vitest + RTL)**: Comportamiento observable del botón de checkout ante múltiples clics (bloqueo), renderizado de pantalla verde de éxito, transición de horario fijo a cuenta regresiva a los 15 minutos, actualización reactiva del reloj dinámico sin modales, renderizado gigante del número de orden al estar listo, y aplicación de variables cromáticas de branding. |
| **Justificación** | Garantiza la cobertura individual y exhaustiva de **AS-1** a **AS-9**, **CB-001** a **CB-012**, e invariantes **INV-001** a **INV-010**, sin depender de servicios externos de pago o servidores SMTP reales durante los tests automáticos. |
| **Alternativas Rechazadas** | - *Pruebas manuales exclusivamente*: No proporciona salvaguardas de regresión continua en el pipeline de CI.<br>- *Mocking de la base de datos en integración*: Pierde la validación de restricciones relacionales de integridad (`UNIQUE`, claves foráneas y triggers). |

---

## 3. Conclusión de Fase 0

Todas las incógnitas y aspectos técnicos han quedado resueltos de forma coherente con la arquitectura y dependencias existentes del repositorio. No restan elementos clasificados como `NEEDS CLARIFICATION`. Se procede a la Fase 1: diseño de modelo de datos (`data-model.md`), contratos de interfaz (`contracts/`), guía de ejecución (`quickstart.md`) y plan de implementación (`plan.md`).

