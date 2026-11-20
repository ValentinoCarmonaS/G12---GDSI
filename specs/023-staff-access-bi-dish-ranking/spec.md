# Feature Specification: Control de acceso de personal, salud financiera y ranking de platos

**Feature Branch**: `023-staff-access-bi-dish-ranking`  
**Created**: 20/11/2026  
**Status**: Aprobado  
**Input**: User description: "La spec 023 agrupa tres historias independientes pero complementarias para el rol administrador: control de acceso del personal, panel de salud financiera y ranking de platos más vendidos. Historias de usuario fuente: 3.1.3, 6.1.1, 6.2.1."

---

## Contexto y objetivo

El rol Administrador de la plataforma gastronómica requiere herramientas de gestión y visualización analítica para resguardar la seguridad operativa del establecimiento y orientar decisiones de negocio informadas sin requerir sistemas ERP complejos o externos.

Esta especificación formaliza tres capacidades funcionales independientes pero complementarias:
1. **Control de acceso del personal (Baja de exempleados - Historia 3.1.3)**: Garantizar que cuando un administrador suspende o da de baja la cuenta de un empleado que ya no pertenece al local, el acceso quede revocado y cualquier intento subsiguiente de inicio de sesión sea rechazado de forma consistente e idéntica al tratamiento de credenciales inválidas, preservando íntegra la trazabilidad histórica de sus acciones previas y protegiendo al establecimiento contra la orfandad administrativa.
2. **Panel de salud financiera (Estadísticas de ingresos y volumen - Historia 6.1.1)**: Proveer una vista consolidada de métricas clave del negocio —específicamente ingresos totales acumulados y cantidad total de pedidos concretados (con pago acreditado)— consultables por granularidad temporal en curso (día, semana calendario o mes calendario), presentadas en formato tabular o gráfico a elección de la interfaz, sin incorporar liquidaciones salariales, cargas tributarias ni módulos contables.
3. **Ranking de platos más vendidos (Catálogo y demanda - Historia 6.2.1)**: Presentar un reporte ordenado de forma estrictamente descendente según la cantidad total de unidades vendidas a partir de los históricos de pedidos con pago acreditado vigentes al momento de la consulta, con criterio determinista secundario alfabético ante empates, permitiendo identificar rápidamente los productos de mayor rotación comercial para definir estrategias de catálogo o promociones.

La especificación describe exclusivamente comportamiento observable, reglas deterministas e invariantes funcionales. Es 100% agnóstica de detalles técnicos, rutas internas, esquemas relacionales, componentes de interfaz o librerías específicas.

---

## Clarifications

### Sesión 20/11/2026 (Grill-Me & Clarify)

- Q: ¿Debe impedirse suspender o dar de baja a una cuenta si es el único administrador activo del establecimiento? → A: Sí. Se aplica protección contra orfandad administrativa: el sistema impide la suspensión, baja o inactivación si la cuenta corresponde al único administrador en estado activo del establecimiento.
- Q: ¿Cómo se delimita exactamente el período de tiempo para las granularidades "día", "semana" y "mes" en el panel de salud financiera? → A: Estado relativo en curso: Día = fecha actual (00:00:00 a 23:59:59 según el huso horario del local), Semana = semana calendario en curso (Lunes a Domingo según ISO 8601), Mes = mes calendario en curso (desde el primer día del mes hasta el último instante del mes).
- Q: Para el panel financiero y el ranking de platos, ¿qué estados específicos de la orden computan como "pedido concretado"? → A: Todos los pedidos con pago acreditado en la plataforma: estados `PAID`, `IN_PROGRESS`, `READY_FOR_PICKUP` y `COMPLETED`. Los ingresos se devengan y computan una vez confirmado y acreditado el pago; los pedidos cancelados (`CANCELLED`), expirados (`EXPIRED`) o pendientes de cobro (`PENDING_PAYMENT`, `PENDING_ACCREDITATION`) quedan expresamente excluidos.
- Q: ¿El ranking de platos más vendidos debe reflejar el histórico global acumulado de ventas o acotarse a la granularidad temporal seleccionada? → A: Ranking histórico acumulado global de ventas del establecimiento por defecto, con opción de visualizarlo en el mismo período seleccionado en el panel o en vista global histórica.
- Q: En el ranking de platos más vendidos, ¿cuál es el criterio determinista de desempate cuando dos o más platos registran idéntica cantidad de unidades vendidas? → A: Criterio determinista secundario alfabético ascendente por nombre del plato (A-Z) ante igual cantidad de unidades vendidas.
- Q: ¿Cómo se gestiona el rechazo de inicio de sesión de una cuenta suspendida para garantizar que no filtre información sobre su estado? → A: El intento se rechaza con un mensaje genérico de credenciales inválidas indistinguible de una contraseña errónea o usuario inexistente, sin indicar si la cuenta existe o está suspendida, evaluándose de forma uniforme para mitigar ataques de temporización.
- Q: ¿Cómo debe comportarse el sistema si dos administradores intentan suspenderse mutuamente en el mismo instante dejando al local sin administradores activos? → A: Se garantiza atomicidad en la operación de suspensión: una de las solicitudes se procesa y la concurrente es rechazada informando la imposibilidad de dejar al establecimiento huérfano de administradores activos.
- Q: ¿Qué sucede con los platos del catálogo que tienen cero unidades vendidas en el ranking? → A: Se excluyen del ranking de más vendidos, listando únicamente aquellos platos con ventas efectivas en el período; si no hay ventas registradas, se presenta un estado neutral descriptivo.
- Q: ¿Cómo se organiza el acceso y visualización del panel financiero y el ranking de platos en el panel de administración? → A: Se integran en una vista consolidada de métricas y rendimiento para el administrador, permitiendo consultar la salud financiera en la sección principal y el ranking de platos de manera coordinada.
- Q: ¿Cómo se determina el momento exacto en que un pedido computa para las métricas del panel y ranking? → A: Se utiliza de forma determinista el instante en que el pago del pedido queda acreditado, delimitado según el huso horario configurado para el establecimiento.

---

## Personas

- **Administrador que gestiona cuentas del personal**: Usuario con rol administrativo responsable de registrar, suspender o dar de baja el acceso a las cuentas del personal operativo cuando finaliza su relación laboral con el local.
- **Administrador que consulta el panel de salud financiera**: Usuario con rol administrativo que necesita evaluar el desempeño económico global del local observando el volumen total de pedidos concretados y la recaudación total en períodos diarios, semanales o mensuales.
- **Administrador que consulta el ranking de platos**: Usuario con rol administrativo que revisa qué preparaciones tienen mayor demanda física en unidades para optimizar la oferta gastronómica y las decisiones de abastecimiento.
- **Usuario de personal dado de baja (afectado)**: Exempleado (cocinero, personal de mostrador u otro rol) cuya cuenta ha sido suspendida o eliminada y que, de intentar iniciar sesión, debe ver bloqueado su acceso a la plataforma.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Baja y suspensión de acceso de exempleados (Priority: P1)

Como administrador, quiero suspender o eliminar cuentas del personal que ya no trabaja más en el local, para mantener la seguridad del sistema y evitar accesos no autorizados desde afuera del establecimiento.

**Why this priority**: Es un requisito crítico de seguridad (P1) que resguarda la integridad de las operaciones del restaurante y la privacidad de la información comercial e inventario frente a desvinculaciones laborales.

**Independent Test**: Puede validarse de forma independiente inactivando una cuenta de personal activa y comprobando de inmediato que los intentos posteriores de inicio de sesión con dicha cuenta son rechazados sistemáticamente por la plataforma.

**Acceptance Scenarios**:

1. **Given** un administrador autenticado en la gestión de personal y una cuenta de empleado activa, **When** el administrador ejecuta la acción de inactivar o suspender la cuenta, **Then** la cuenta queda en estado inactivo/suspendido y el sistema confirma el cambio de estado.
2. **Given** un administrador que intenta suspender o inactivar al único administrador activo de su establecimiento, **When** confirma la acción, **Then** el sistema bloquea la operación y muestra un mensaje de advertencia indicando que no puede darse de baja al último administrador activo (protección contra orfandad administrativa).
3. **Given** un usuario de personal cuya cuenta fue previamente inactivada o suspendida, **When** intenta iniciar sesión en la plataforma ingresando sus credenciales de acceso (incluso si la contraseña es correcta), **Then** el sistema rechaza el intento de login con un mensaje genérico de credenciales inválidas y no inicia sesión.
4. **Given** un usuario de personal cuya cuenta fue dada de baja, **When** el administrador consulta los registros y auditoría de pedidos o platos manipulados previamente por ese empleado, **Then** el historial de actividad previa se mantiene intacto y visible para auditoría.

---

### User Story 2 — Panel de salud financiera del local (Priority: P1)

Como administrador, quiero visualizar un panel que muestre mis ingresos totales acumulados y la cantidad de pedidos concretados, para tener un pantallazo rápido del estado financiero del negocio por día, semana o mes.

**Why this priority**: Es el objetivo primario (P1) de visualización analítica del administrador para la toma de decisiones económicas y operativas directas sobre el flujo de pedidos concretados.

**Independent Test**: Puede validarse de forma independiente consultando el panel con un conjunto de órdenes con pago acreditado en diferentes fechas y verificando que los ingresos totales y la cantidad de pedidos coincidan exactamente con la suma del período seleccionado (día actual, semana calendario o mes calendario).

**Acceptance Scenarios**:

1. **Given** la existencia de pedidos con pago acreditado (`PAID`, `IN_PROGRESS`, `READY_FOR_PICKUP`, `COMPLETED`), **When** el administrador accede al panel de salud financiera, **Then** el sistema presenta el importe de ingresos totales acumulados y la cantidad total de pedidos concretados correspondientes al período por defecto (día en curso).
2. **Given** el administrador visualizando el panel financiero, **When** selecciona la granularidad "semana", **Then** el panel actualiza las métricas recalculando los ingresos y pedidos para la semana calendario en curso (Lunes a Domingo según ISO 8601).
3. **Given** el administrador visualizando el panel financiero, **When** selecciona la granularidad "mes", **Then** el panel actualiza las métricas recalculando los ingresos y pedidos para el mes calendario en curso.
4. **Given** un período temporal consultado (día, semana o mes) en el cual no se registraron pedidos con pago acreditado, **When** el administrador consulta el panel para dicho período, **Then** el sistema muestra ingresos totales en cero ($0,00) y cero (0) pedidos concretados, sin arrojar errores ni pantallas vacías anómalas.
5. **Given** pedidos en estado no acreditado o anulado (`CANCELLED`, `EXPIRED`, `PENDING_PAYMENT`, `PENDING_ACCREDITATION`), **When** se calculan las métricas del panel, **Then** dichos pedidos son estrictamente excluidos tanto del monto monetario como del conteo de pedidos.

---

### User Story 3 — Ranking de platos más vendidos (Priority: P2)

Como administrador, quiero consultar un ranking siempre actualizado de los platos con mayor volumen de ventas, para identificar cuáles impulsar con promociones y cuáles retirar o revisar en el catálogo.

**Why this priority**: Es una funcionalidad de análisis táctico (P2) que depende de los históricos acumulados de venta y complementa la visión financiera global del negocio.

**Independent Test**: Puede validarse de forma independiente registrando ventas con distintas cantidades de unidades en diferentes platos y verificando que el reporte liste los platos ordenados de mayor a menor cantidad de unidades vendidas, resolviendo empates por orden alfabético A-Z.

**Acceptance Scenarios**:

1. **Given** un historial de ventas con múltiples platos ordenados y con pago acreditado, **When** el administrador abre el reporte de platos más vendidos, **Then** el sistema exhibe por defecto el listado histórico global ordenado de forma estrictamente descendente según la cantidad total de unidades vendidas de cada plato.
2. **Given** platos que registran exactamente la misma cantidad de unidades vendidas acumuladas, **When** el administrador consulta el ranking, **Then** los platos empatados se ordenan alfabéticamente ascendente (A-Z) por su denominación comercial, manteniendo la estabilidad del reporte.
3. **Given** una nueva venta de platos acreditada en la plataforma, **When** el administrador abre o recarga el ranking de platos, **Then** el listado refleja de inmediato los datos consolidados vigentes incorporando las nuevas unidades vendidas.
4. **Given** un establecimiento sin historial de ventas registradas, **When** el administrador ingresa al reporte de ranking de platos, **Then** el sistema presenta un estado informativo neutral indicando la ausencia de datos de venta disponibles, sin fallos de cálculo.

---

### Edge Cases

- **Cuenta de personal suspendida con sesión activa previa**: La regla de rechazo opera de forma determinista ante el siguiente intento de inicio de sesión o renovación de credencial; el manejo de conexiones o sesiones concurrentes preexistentes queda fuera de la regla de login tratada en esta especificación.
- **Intento de baja del último administrador del establecimiento**: El sistema rechaza atómicamente la solicitud informando que el establecimiento debe conservar siempre al menos un administrador activo.
- **Intento de login con credenciales válidas en cuenta suspendida o eliminada**: El sistema debe rechazar el login con una respuesta genérica de error indistinguible de credenciales inválidas, sin revelar el estado de la cuenta.
- **Panel financiero con exactamente un pedido concretado en el período**: Debe mostrar el importe exacto de dicho pedido como ingreso total y "1" como cantidad de pedidos concretados, renderizando correctamente tanto en tabla como en gráfico.
- **Conmutación rápida de granularidades temporales (día / semana / mes)**: La vista debe actualizar de manera determinista los totales sin mezclar acumuladores ni conservar valores de la granularidad anterior.
- **Platos del catálogo con cero unidades vendidas**: La visualización del ranking prioriza los platos con ventas efectivas; si se incluyen platos sin ventas, deben figurar al final con valor cero unidades vendidas ordenados alfabéticamente A-Z sin distorsionar el orden descendente.
- **Transición de medianoche en granularidad "día"**: Los pedidos concretados a las 23:59:59 pertenecen al día que cierra; a partir de las 00:00:00 del día siguiente, el panel de "día" inicia un nuevo ciclo con sus acumuladores correspondientes a la nueva fecha.

---

## Requirements *(mandatory)*

### Functional Requirements

#### Control de acceso del personal (Historia 3.1.3)
- **FR-001**: El sistema DEBE permitir al administrador suspender o inactivar una cuenta de personal perteneciente a su establecimiento.
- **FR-002**: El sistema DEBE rechazar cualquier intento de inicio de sesión de un usuario cuya cuenta se encuentre en estado inactivo o suspendido.
- **FR-003**: El sistema DEBE emitir un mensaje genérico de error de autenticación ante un intento de login de un usuario inactivo, sin diferenciar si la falla es por credenciales incorrectas o por cuenta suspendida.
- **FR-004**: La inactivación o baja de una cuenta de personal NO DEBE eliminar ni modificar los registros de auditoría ni el historial de actividades previamente ejecutadas por dicho usuario en la plataforma.
- **FR-005**: El sistema DEBE impedir la suspensión, baja o inactivación de una cuenta de personal si dicha cuenta es el único administrador activo registrado para el establecimiento (protección contra orfandad administrativa).

#### Panel de salud financiera (Historia 6.1.1)
- **FR-006**: El sistema DEBE presentar al administrador un panel financiero con al menos dos métricas cuantitativas consolidadas: ingresos totales acumulados y cantidad total de pedidos concretados.
- **FR-007**: El sistema DEBE permitir filtrar o seleccionar la información del panel financiero por tres granularidades temporales deterministas en curso: día actual, semana calendario en curso (Lunes a Domingo según ISO 8601) y mes calendario en curso.
- **FR-008**: El sistema DEBE considerar como "pedido concretado" a toda orden que posea pago acreditado (`PAID`, `IN_PROGRESS`, `READY_FOR_PICKUP`, `COMPLETED`), sumando sus importes monetarios al total de ingresos.
- **FR-009**: El sistema DEBE excluir estrictamente del cálculo financiero todo pedido cancelado (`CANCELLED`), expirado (`EXPIRED`) o con cobro pendiente (`PENDING_PAYMENT`, `PENDING_ACCREDITATION`).
- **FR-010**: El sistema DEBE presentar las métricas financieras mediante formato de tabla de datos, gráfico visual o una combinación de ambos a disposición de la interfaz de usuario.
- **FR-011**: Cuando no existan pedidos concretados en la granularidad temporal consultada, el sistema DEBE informar explícitamente $0,00 en ingresos totales y 0 pedidos concretados.

#### Ranking de platos más vendidos (Historia 6.2.1)
- **FR-012**: El sistema DEBE presentar un reporte con el ranking de platos comercializados a partir de los históricos de pedidos con pago acreditado.
- **FR-013**: El ranking de platos DEBE ordenarse de forma estrictamente descendente según la suma acumulada de unidades vendidas de cada plato.
- **FR-014**: Ante igualdad en cantidad de unidades vendidas entre dos o más platos, el sistema DEBE aplicar un desempate determinista secundario ordenando alfabéticamente ascendente (A-Z) por el nombre del plato.
- **FR-015**: El ranking de platos DEBE reflejar por defecto el acumulado histórico global de ventas del establecimiento, con opción complementaria de acotarse al período en curso seleccionado en el panel.
- **FR-016**: El sistema DEBE asegurar que el ranking refleje los datos de venta vigentes y consolidados al momento en que el administrador abre o refresca el reporte.
- **FR-017**: Cuando no existan históricos de ventas en el establecimiento, el sistema DEBE mostrar un mensaje o estado vacío descriptivo sin fallos ni interrupciones visuales.

---

### Invariantes y reglas deterministas

1. **Invariante de bloqueo de acceso**: Una cuenta en estado inactivo, suspendido o dado de baja no puede autenticarse exitosamente bajo ninguna circunstancia.
2. **Invariante de protección administrativa**: Un establecimiento nunca puede quedar sin al menos un administrador en estado activo.
3. **Invariante de inmutabilidad histórica**: La modificación de estado de una cuenta de usuario es estrictamente hacia adelante y no altera la autoría ni los datos de pedidos, platos o transacciones pasadas.
4. **Invariante de cálculo de ingresos por pago acreditado**: Solo los pedidos con pago acreditado (`PAID`, `IN_PROGRESS`, `READY_FOR_PICKUP`, `COMPLETED`) devengan ingresos y computan para las métricas del panel y unidades del ranking.
5. **Invariante de límites de granularidad calendario**: La semana calendario comprende de Lunes a Domingo (ISO 8601) y el mes calendario comprende los días naturales del mes en curso según el huso horario local.
6. **Invariante de orden descendente con desempate alfabético**: Ningún plato con menor cantidad de unidades vendidas puede preceder a uno con mayor cantidad de unidades vendidas; a igual cantidad, la precedencia es alfabética ascendente por nombre de plato.
7. **Invariante de no-afectación contable**: Ni el panel de salud financiera ni el ranking de platos realizan asientos contables, amortizaciones, cálculo de nóminas salariales ni deducción de cargas impositivas.

---

### Key Entities

- **Cuenta de personal**: Representa la identidad digital de un empleado en la plataforma. Posee atributos de identificación (nombre, correo de acceso), rol operativo (ADMIN, CHEF, COUNTER_CLERK), estado operativo (activo, suspendido o inactivo) y relación de pertenencia con el establecimiento.
- **Métricas de salud financiera**: Representa la agregación cuantitativa de desempeño para un período temporal delimitado (día, semana o mes). Contiene el monto total de ingresos monetarios acumulados y el número entero de pedidos concretados con pago acreditado.
- **Ítem del ranking de platos**: Representa la agregación de ventas físicas para un plato del catálogo gastronómico. Incluye la denominación o identificación del plato y el total entero acumulado de unidades comercializadas.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los intentos de inicio de sesión efectuados por usuarios con cuentas en estado inactivo o suspendido son rechazados de forma inmediata por el sistema con mensaje genérico.
- **SC-002**: El 100% de los intentos de suspender al único administrador activo de un establecimiento son bloqueados atómicamente por la plataforma.
- **SC-003**: El administrador puede alternar entre las vistas de granularidad temporal (día, semana, mes) en el panel financiero y observar las cifras actualizadas en menos de 2 segundos.
- **SC-004**: Las métricas de ingresos totales y volumen de pedidos reflejan con 100% de exactitud aritmética la suma de las órdenes con pago acreditado registradas en el período seleccionado.
- **SC-005**: El reporte de platos muestra el 100% de los platos comercializados ordenados de mayor a menor según sus unidades vendidas, resolviendo empates alfabéticamente sin discrepancias.
- **SC-006**: El 100% de los registros históricos de auditoría y comandas previas vinculados a un empleado dado de baja permanecen inalterados tras el cambio de estado de la cuenta.

---

## Assumptions

- Se asume la existencia de un mecanismo previo de autenticación y de gestión de catálogo y pedidos donde las ventas con pago acreditado registran las cantidades de cada plato involucrado.
- Se asume que el huso horario de referencia para determinar los límites de inicio y fin de "día", "semana" y "mes" corresponde a la configuración horaria del establecimiento gastronómico.
- Se asume que la interfaz de administración provee selectores visuales sencillos e intuitivos para alternar entre día, semana y mes, así como para acceder al ranking de platos.

---

## Fuera de alcance

- Liquidación de sueldos, jornales, aguinaldos, indemnizaciones, aportes patronales o previsionales.
- Módulos de contabilidad general, libro diario, balances financieros, cálculo de márgenes netos, EBITDA o deducciones impositivas (IVA, ingresos brutos).
- Revocación o terminación forzada en tiempo real de sesiones ya abiertas o tokens activos previos a la inactivación.
- Mecanismos de reactivación de cuentas o reasignación masiva de permisos fuera del ámbito del bloqueo de login y orfandad administrativa.
- Granularidades temporales adicionales a día, semana y mes (como filtros por horas, turnos, trimestres o períodos interanuales).
- Funcionalidades de exportación de datos a formatos externos (PDF, Excel, CSV), reportes programados por email o alertas automáticas.
- Filtros avanzados o segmentaciones multidimensionales por sucursal, categoría de plato o medios de pago.
- Definiciones técnicas, arquitectónicas, esquemas SQL/NoSQL, endpoints HTTP, componentes frontend o bibliotecas de visualización.

---

## Verificación de cobertura de historias de usuario

| Historia de usuario | Descripción | Requisitos funcionales | Escenarios de aceptación |
| :--- | :--- | :--- | :--- |
| **3.1.3 (P1)** | Baja de exempleados y control de acceso | FR-001, FR-002, FR-003, FR-004, FR-005 | User Story 1 (Escenarios 1, 2, 3, 4) |
| **6.1.1 (P1)** | Panel de estadísticas de ingresos y volumen | FR-006, FR-007, FR-008, FR-009, FR-010, FR-011 | User Story 2 (Escenarios 1, 2, 3, 4, 5) |
| **6.2.1 (P2)** | Ranking de platos más vendidos | FR-012, FR-013, FR-014, FR-015, FR-016, FR-017 | User Story 3 (Escenarios 1, 2, 3, 4) |
