# Roadmap de Sprints: EasyEat MVP Final

**Proyecto:** EasyEat (Gestión Gastronómica SaaS Multi-Tenant)  
**Fecha de planificación:** 2026-10-14  
**Equipo:** Grupo 12 (6 integrantes, FIUBA GDSI 2026)  
**Documentos relacionados:**
- [`01 - Decisiones de Planificación del MVP.md`](./01%20-%20Decisiones%20de%20Planificaci%C3%B3n%20del%20MVP.md) (Registro de decisiones de planificación)  
- [`04 - Proceso de Ajuste y Respuesta al Product Owner.md`](./04%20-%20Proceso%20de%20Ajuste%20y%20Respuesta%20al%20Product%20Owner.md) (Bitácora de negociación y proceso dialéctico)
- [`03 - Justificación de Estimaciones Técnicas.md`](./03%20-%20Justificaci%C3%B3n%20de%20Estimaciones%20T%C3%A9cnicas.md) (Justificación técnica de las 82 historias del scope)

---

## 1. Parámetros de Capacidad

| Parámetro | Valor | Detalle |
|---|:---:|---|
| Dedicación semanal por integrante | 10 hs | Carga estándar consensuada para el equipo |
| Capacidad nominal por sprint | 60 SP | 6 personas × 10 hs/semana (1 SP ≈ 1 hora/hombre de diseño y desarrollo) |
| Capacidad nominal total (6 sprints) | 360 SP | Techo planificado del proyecto |
| Backlog asignado | 279 SP | 82 historias de usuario enfocadas en el flujo Pagar → Cocinar → Entregar |
| Reserva de contingencia | 81 SP (22.5%) | Margen para absorber demoras en pasarela de pagos, WebSockets y pruebas |
| Carga media por sprint | 46.5 SP | Cumple la pauta del PO de mantenerse por debajo de 50 SP |

---

## 2. Resumen de Carga por Sprint

```text
Sprint 1  [Infraestructura base, auth JWT, restricciones y staff]  ████████░░  46/60 SP  (Buffer: 14 SP)
Sprint 2  [Platos, recetas, costeo y catálogo visible]             ████████░░  49/60 SP  (Buffer: 11 SP)
Sprint 3  [Detalle de plato, carrito y reservas por franjas]       ████████░░  46/60 SP  (Buffer: 14 SP)
Sprint 4  [Pasarela de pagos, confirmación y descuento de stock]   ████████░░  48/60 SP  (Buffer: 12 SP)
Sprint 5  [KDS de cocina en tiempo real y avisos de estado]        ███████░░░  45/60 SP  (Buffer: 15 SP)
Sprint 6  [Despacho en mostrador, código manual y métricas BI]     ███████░░░  45/60 SP  (Buffer: 15 SP)
─────────────────────────────────────────────────────────────────────────────────────────────
Total asignado:                                                    279 SP / 360 SP (Buffer: 81 SP)
```

| Sprint | Alcance principal | Historias | SP Asignados | Capacidad | Margen libre |
|:---:|---|:---:|:---:|:---:|:---:|
| **1** | Infraestructura base, auth JWT, restricciones y staff | 17 | 46 SP | 60 SP | 14 SP (23.3%) |
| **2** | Platos, recetas, costeo y catálogo visible | 13 | 49 SP | 60 SP | 11 SP (18.3%) |
| **3** | Detalle de plato, carrito y reservas por franjas | 19 | 46 SP | 60 SP | 14 SP (23.3%) |
| **4** | Pasarela de pagos, confirmación y descuento de stock | 8 | 48 SP | 60 SP | 12 SP (20.0%) |
| **5** | KDS de cocina en tiempo real y avisos de estado | 14 | 45 SP | 60 SP | 15 SP (25.0%) |
| **6** | Despacho en mostrador, código manual y métricas BI | 11 | 45 SP | 60 SP | 15 SP (25.0%) |
| **Total** | **Flujo completo Pagar → Cocinar → Entregar** | **82** | **279 SP** | **360 SP** | **81 SP (22.5%)** |

---

## 3. Plan de Sprints

### Sprint 1: Infraestructura base, Auth JWT, Restricciones y Staff (46 SP)
*Objetivo:* Configurar la base multi-tenant, persistencia, autenticación unificada con JWT, asignación de roles (RBAC), perfil con restricciones alimentarias y gestión de ingredientes.

| # | Historia de Usuario | Rol | Estimación | Notas de Implementación |
|---|--------------------|:---:|:---:|---|
| `COM-1.1.1` | Crear cuenta con mail y contraseña | COM | 3 SP | Formulario base de registro, validaciones de formato y unicidad de correo. |
| `COM-1.2.1` | Login con mail y contraseña (Arquitectura Base JWT) | COM | 3 SP | Endpoint `POST /api/auth/login`, hash bcrypt y emisión de JWT con claims de rol y tenant. |
| `COM-1.3.1` | Recuperar contraseña por mail (Servicio centralizado) | COM | 3 SP | Generación de token temporal con expiración y envío de correo transaccional. |
| `COM-1.4.1` | Editar datos personales | COM | 2 SP | Actualización básica de nombre, apellido y teléfono. |
| `COM-1.4.3` | Configurar restricciones alimentarias y preferencias | COM | 5 SP | Selección de restricciones (celíaco, vegano, alérgenos) guardadas en el perfil. |
| `COM-4.2.1` | Filtros de perfil automáticos aplicados al catálogo | COM | 3 SP | Inyección de las restricciones del perfil comensal en las consultas de menú. |
| `ENC-1.1.1` | Ingreso con credenciales de empleado | ENC | 1 SP | Reutiliza el endpoint central de auth validando el rol ENCARGADO. |
| `ENC-1.2.1` | Redirección automática a vista de mostrador | ENC | 1 SP | Guard de ruta en cliente hacia `/mostrador`. |
| `CHF-1.1.1` | Ingreso con credenciales de empleado | CHF | 1 SP | Reutiliza el endpoint central de auth validando el rol CHEF. |
| `CHF-1.2.1` | Redirección automática a vista de cocina (KDS) | CHF | 1 SP | Guard de ruta en cliente hacia `/cocina`. |
| `ADM-1.1.1` | Ingresar al Back-Office web | ADM | 1 SP | Reutiliza el endpoint central de auth validando el rol ADMIN. |
| `ADM-1.2.1` | Recuperar contraseña de administrador | ADM | 1 SP | Reutiliza el servicio de tokens y correo de recuperación. |
| `ADM-3.1.1` | Alta de staff del restaurante | ADM | 5 SP | Formulario para registrar empleados vinculados al establecimiento. |
| `ADM-3.1.2` | Asignación de Roles y Permisos (RBAC) | ADM | 5 SP | Middlewares de backend para restringir endpoints según rol asignado. |
| `ADM-4.1.1` | Crear, editar y eliminar ingredientes del inventario | ADM | 3 SP | CRUD básico de materias primas e insumos. |
| `ADM-4.1.2` | Ingresar y actualizar manualmente stock inicial | ADM | 3 SP | Ajuste de stock con registro de auditoría del usuario que modifica. |
| `CHF-2.1.1` | Visualizar catálogo de ingredientes disponibles | CHF | 5 SP | Lectura paginada para que la cocina consulte los insumos en depósito. |

---

### Sprint 2: Platos, Recetas N:M, Aprobación y Catálogo Visible (49 SP)
*Objetivo:* Permitir la creación de platos por el Chef con recetas Many-to-Many, revisión y costeo por el Administrador, y publicación en el catálogo del Comensal.

| # | Historia de Usuario | Rol | Estimación | Notas de Implementación |
|---|--------------------|:---:|:---:|---|
| `CHF-2.2.1` | Crear ficha base del plato | CHF | 5 SP | Formulario con nombre, categoría, descripción y subida de imagen a storage. |
| `CHF-2.2.2` | Asignar receta (matriz N:M de ingredientes) | CHF | 8 SP | Relación Many-to-Many con cantidades de insumos necesarias por porción. |
| `CHF-2.2.3` | Configurar alérgenos y etiquetas dietarias | CHF | 3 SP | Checkboxes de sellos de advertencia (sin TACC, frutos secos, lácteos). |
| `CHF-2.2.4` | Enviar ficha técnica a revisión del Administrador | CHF | 3 SP | Transición de estado: Borrador → Pendiente de revisión. |
| `ADM-5.1.1` | Visualizar platos pendientes de revisión | ADM | 3 SP | Bandeja de entrada administrativa con solicitudes de platos para aprobar. |
| `ADM-5.1.2` | Auditoría de receta y food cost base | ADM | 2 SP | Cálculo automático del costo de ingredientes según cantidades de la receta. |
| `ADM-5.2.1` | Fijar precio de venta al público (PVP) | ADM | 3 SP | Asignación de margen comercial y precio final en moneda local. |
| `ADM-5.2.2` | Aprobar y publicar plato en catálogo | ADM | 2 SP | Transición a estado Activo, visible para los comensales. |
| `ADM-5.2.3` | Rechazar plato al Chef con notas de corrección | ADM | 3 SP | Devolución de la ficha con mensaje de feedback para ajustes. |
| `COM-1.5.1` | Seleccionar establecimiento (Buscador Multi-tenant) | COM | 5 SP | Selector de restaurantes por nombre para cargar el catálogo correspondiente. |
| `COM-4.1.1` | Ver catálogo del establecimiento en tiempo real | COM | 5 SP | Listado de platos activos organizados por categorías. |
| `COM-4.1.2` | Ocultar platos sin stock automáticamente | COM | 5 SP | Exclusión del menú visible cuando los ingredientes asociados están en cero. |
| `COM-4.2.3` | Barra de búsqueda rápida de platos por texto | COM | 2 SP | Filtro de platos por coincidencia de texto en el cliente. |

---

### Sprint 3: Detalle de Plato, Carrito y Motor de Reservas por Franjas (46 SP)
*Objetivo:* Completar la vista de detalle de platos, el carrito de compras individual y el selector de reservas por turnos con control de cupos.

| # | Historia de Usuario | Rol | Estimación | Notas de Implementación |
|---|--------------------|:---:|:---:|---|
| `COM-5.1.1` | Visualizar foto y presentación multimedia del plato | COM | 2 SP | Vista de detalle del plato con imagen y descripción ampliada. |
| `COM-5.1.2` | Ver lista de ingredientes y etiquetas de alérgenos | COM | 1 SP | Listado informativo de insumos y alérgenos en la ficha del plato. |
| `COM-5.1.3` | Ver precio unitario del plato | COM | 1 SP | Muestra del precio final fijado por la administración. |
| `COM-4.2.2` | Búsqueda por filtros manuales | COM | 3 SP | Filtros combinados por rango de precio, categoría y sellos dietarios. |
| `COM-6.1.1` | Agregar plato al pedido | COM | 2 SP | Manejo de estado del carrito de compras en el cliente. |
| `COM-6.1.2` | Ver acumulado del carrito y subtotal | COM | 1 SP | Indicador numérico de ítems y subtotal acumulado. |
| `COM-6.1.3` | Modificar cantidades o quitar ítems | COM | 2 SP | Controles para sumar, restar o eliminar productos del carrito. |
| `COM-6.2.1` | Seleccionar modalidad de retiro (Inmediato o Reserva) | COM | 1 SP | Selector entre preparación inmediata o turno diferido. |
| `COM-6.2.2` | Elegir día y franja horaria de retiro | COM | 3 SP | Selector de bloques de horario parametrizados (ej: turnos de 30 min). |
| `COM-6.2.3` | Indicador visual de saturación de franja horaria | COM | 3 SP | Indicador de disponibilidad según cupos ocupados en la franja. |
| `COM-6.2.4` | Ocultar franjas horarias que superaron su capacidad | COM | 2 SP | Deshabilitación de turnos completos para evitar sobreventa. |
| `ADM-2.2.1` | Configurar días y horarios comerciales | ADM | 5 SP | Definición de horarios de atención y días de apertura por local. |
| `ADM-2.2.2` | Definir capacidad máxima de pedidos por franja | ADM | 3 SP | Límite numérico de pedidos permitidos por turno de 30 minutos. |
| `ADM-2.2.3` | Habilitar o deshabilitar métodos de pago | ADM | 2 SP | Configuración de pasarelas y canales autorizados para cobro. |
| `CHF-2.2.5` | Pausa manual de platos (apagado rápido) | CHF | 2 SP | Toggle para suspender temporalmente la venta de un plato desde cocina. |
| `CHF-5.2.1` | Alerta visual de stock crítico en cocina | CHF | 3 SP | Aviso en terminal de cocina cuando un insumo alcanza el umbral mínimo. |
| `ADM-4.1.3` | Definir umbral de stock mínimo o crítico | ADM | 2 SP | Campo numérico de alerta de reposición por cada ingrediente. |
| `ADM-4.1.4` | Visualizar alertas de reabastecimiento en back-office | ADM | 3 SP | Panel administrativo con insumos que requieren compra o reposición. |
| `COM-1.4.2` | Editar datos demográficos y foto de perfil | COM | 5 SP | Actualización de foto de usuario, edad y datos demográficos. |

---

### Sprint 4: Pasarela de Pagos Transaccional y Descuento ACID de Stock (48 SP)
*Objetivo:* Procesar el pago individual mediante pasarela externa, emitir la confirmación con código numérico de retiro y descontar stock automáticamente por porción.

| # | Historia de Usuario | Rol | Estimación | Notas de Implementación |
|---|--------------------|:---:|:---:|---|
| `COM-7.1.1` | Seleccionar método de pago guardado | COM | 3 SP | Uso de tarjetas previamente tokenizadas con la pasarela. |
| `COM-7.1.2` | Cargar nueva tarjeta (SDK Pasarela de pagos) | COM | 8 SP | Integración del SDK de pagos (MercadoPago/Stripe) en frontend y backend. |
| `COM-7.1.3` | Opción de guardar medio de pago para futuras compras | COM | 3 SP | Almacenamiento seguro del token de tarjeta devuelto por el procesador. |
| `COM-7.2.1` | Pago y confirmación de carrito individual | COM | 8 SP | Flujo de cobro: validación de stock, cargo en pasarela y creación de la orden. |
| `COM-7.3.1` | Confirmación inmediata de pago y webhook fallback | COM | 5 SP | Pantalla de confirmación con código de retiro y listener para webhooks. |
| `CHF-5.1.1` | Descuento automático de stock transaccional ACID | CHF | 8 SP | Transacción SQL que descuenta ingredientes de la receta al confirmar el pago. |
| `COM-7.3.2` | Cálculo de tiempo estimado de espera en cola | COM | 5 SP | Estimación basada en la cantidad de pedidos pendientes en cocina. |
| `ADM-2.1.1` | Configurar branding del restaurante | ADM | 8 SP | Carga de logo institucional y definición de paleta de colores del local. |

---

### Sprint 5: Cocina en Tiempo Real (KDS) y Sincronización (45 SP)
*Objetivo:* Implementar la pantalla de cocina (KDS) con WebSockets, avance incremental de elaboración y actualización de estado hacia el comensal.

| # | Historia de Usuario | Rol | Estimación | Notas de Implementación |
|---|--------------------|:---:|:---:|---|
| `CHF-3.1.1` | Visualización en tiempo real de comandas (KDS WebSockets) | CHF | 8 SP | Conexión WebSocket bidireccional para recibir pedidos pagados al instante. |
| `CHF-3.1.2` | Priorización automática de cola de pedidos | CHF | 3 SP | Ordenamiento por proximidad de franja horaria y hora de llegada. |
| `CHF-3.2.1` | Lectura detallada de comanda y notas | CHF | 2 SP | Vista de comanda con platos, cantidades y observaciones del comensal. |
| `CHF-4.1.1` | Marcar pedido como "En preparación" | CHF | 3 SP | Cambio de estado de la orden al iniciar su cocción. |
| `CHF-4.1.2` | Check incremental por ítem de la comanda | CHF | 5 SP | Marcado individual de cada plato listo dentro de una comanda. |
| `CHF-4.1.3` | Finalizar pedido completo y pasar a estado "Listo" | CHF | 3 SP | Transición a "Listo para retirar" cuando todos los platos están terminados. |
| `CHF-4.1.4` | Cronómetro de preparación progresivo (Count-Up) | CHF | 3 SP | Contador en pantalla desde que la orden pasó a preparación. |
| `CHF-4.1.5` | Alerta visual por demora excesiva en KDS | CHF | 3 SP | Indicador visual de alerta cuando el pedido supera el tiempo estimado. |
| `CHF-4.2.1` | Gatillar cambio de estado en vivo hacia el comensal | CHF | 3 SP | Emisión de evento WebSocket para actualizar la pantalla del cliente. |
| `COM-8.1.1` | Indicadores visuales en interfaz comensal | COM | 2 SP | Badge de estado en la app del comensal (Recibido, En Preparación, Listo). |
| `COM-8.1.2` | Aviso destacado al comensal: "Listo para retirar" | COM | 2 SP | Notificación en pantalla indicando que el pedido puede retirarse. |
| `CHF-4.2.2` | Sincronización con cola de "Próximos" de Mostrador | CHF | 3 SP | Notificación a mostrador para anticipar el armado de pedidos en cocción. |
| `CHF-4.2.3` | Sincronización con cola de "Listos" de Mostrador | CHF | 2 SP | Envío automático a la columna de entrega al completarse la preparación. |
| `ENC-2.2.1` | Visualizar lista de pedidos "En preparación" en mostrador | ENC | 3 SP | Vista en mostrador de los pedidos que cocina tiene en marcha. |

---

### Sprint 6: Mostrador (Despacho / Entrega), Operación y Métricas BI (45 SP)
*Objetivo:* Gestionar la entrega de pedidos en mostrador por código numérico manual, cierre operativo de órdenes y reportes analíticos para la administración.

| # | Historia de Usuario | Rol | Estimación | Notas de Implementación |
|---|--------------------|:---:|:---:|---|
| `ENC-2.1.1` | Visualizar pedidos "Listos para retirar" en mostrador | ENC | 8 SP | Lista de pedidos listos para despacho actualizada en tiempo real. |
| `ENC-2.1.2` | Ver detalles rápidos del pedido en mostrador | ENC | 2 SP | Consulta rápida del contenido del pedido para verificar antes de entregar. |
| `ENC-2.2.2` | Alerta visual de espera prolongada en mostrador | ENC | 3 SP | Alerta para pedidos que llevan mucho tiempo en mostrador sin retirarse. |
| `ENC-3.2.1` | Ingreso de código numérico manual de retiro (Fallback) | ENC | 2 SP | Input para buscar y validar pedidos por su código numérico. |
| `ENC-3.3.1` | Visualización de confirmación y match exitoso | ENC | 2 SP | Confirmación en pantalla de que el código ingresado coincide con el pedido. |
| `ENC-3.3.2` | Marcar pedido como "Entregado" y archivar | ENC | 3 SP | Cierre del ciclo de la orden y archivo en el historial de ventas. |
| `COM-8.1.3` | Alerta visual de congestión en mostrador | COM | 3 SP | Aviso en la app del comensal si hay concentración de retiros pendientes. |
| `ADM-3.1.3` | Baja y suspensión de cuentas de exempleados | ADM | 3 SP | Inhabilitación de accesos y revocación de sesiones para exempleados. |
| `ADM-6.1.1` | Reportes analíticos de ingresos y volumen de ventas | ADM | 8 SP | Gráficos de facturación y cantidad de órdenes por día, semana o mes. |
| `ADM-6.1.2` | Mapa de calor de franjas horarias y picos de demanda | ADM | 8 SP | Vista de distribución de pedidos por turno para planificar la cocina. |
| `ADM-6.2.1` | Ranking analítico de platos más vendidos | ADM | 3 SP | Listado de platos con mayor rotación ordenados por ventas. |

---

## 4. Cobertura del Flujo Central

El roadmap cubre de punta a punta las tres etapas del ciclo de negocio:
- **Pagar:** Procesamiento con pasarela, tokenización y confirmación inmediata con código unívoco (Sprint 4).
- **Cocinar:** Recetas estructuradas, descuento de inventario por porción y KDS con avance táctil en tiempo real (Sprints 1, 2, 4 y 5).
- **Entregar:** Panel de despacho en mostrador, validación por código y cierre formal de la orden (Sprint 6).
