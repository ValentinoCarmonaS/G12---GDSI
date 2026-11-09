# Control de Calidad de la Especificación: Pago de Carrito Individual y Descuento de Stock Transaccional

**Propósito**: Validar la completitud y calidad de la especificación funcional antes de avanzar a la fase de planificación técnica (`speckit-plan`).  
**Fecha de Creación**: 09/11/2026  
**Especificación**: [spec.md](file:///f:/ALL/PROGRAMACION/Proyectos/SDD/sdd%20-%20gdsi%20-%20grupo%2012/specs/015-individual-checkout-stock-deduction/spec.md)

## Calidad del Contenido

- [x] Ausencia total de detalles de implementación técnica (sin mención a frameworks, rutas, endpoints, SQL, tablas, bases de datos, librerías, algoritmos específicos ni nombres de campos técnicos).
- [x] Foco exclusivo en valor para el comensal y el chef, reglas operativas de cocina e invariantes de dominio de inventario.
- [x] Redacción profesional en español neutral para stakeholders no técnicos, responsables de cocina y comensales.
- [x] Todas las secciones obligatorias completadas con rigor, consistencia y exhaustividad.

## Completitud de Requisitos

- [x] Sin marcadores `[NEEDS CLARIFICATION]` pendientes; supuestos razonables documentados formalmente.
- [x] Requisitos funcionales testeables, unívocos y con numeración correlativa (`FR-001` a `FR-013`).
- [x] Criterios de éxito medibles y expresados de forma agnóstica a la tecnología (`SC-001` a `SC-010`).
- [x] Todos los escenarios de aceptación definidos en formato Dado / Cuando / Entonces (`AS-1` a `AS-5`, `AS-1b`, `AS-6` y `AS-6b`), con trazabilidad directa y unívoca a las historias fuente `COM-7.2.1` y `CHF-5.1.1`.
- [x] Casos borde y comportamiento ante errores contemplados exhaustivamente (pago aprobado, agotamiento de insumos por concurrencia, saturación de franja horaria por demora, carrito con bonificación 100%, estado pendiente de la pasarela, insumos compartidos, cantidades fraccionarias de recetas, insuficiencia parcial y notificaciones repetidas).
- [x] Alcance claramente delimitado, explicitando exclusiones y fronteras con historias previas y posteriores.
- [x] Restricciones transversales obligatorias (`RT-1`, `RT-2` y `RT-3`) incorporadas en sección propia sin alterar la pureza de las historias de usuario.
- [x] Supuestos y dependencias de upstream formalmente identificados (Specs 004, 005, 006, 008, 009, 010, 012, 013 y 014).

## Preparación de la Feature (Readiness)

- [x] Todos los requisitos funcionales cuentan con criterios de aceptación verificables.
- [x] Los escenarios de usuario cubren de punta a punta el flujo primario de confirmación, cobro, descuento de inventario y despacho al KDS.
- [x] La feature cumple con los resultados cuantificables de los Criterios de Éxito (0 ventas no preparables, 0 débitos parciales de stock, 0 cobros en rechazos, 100% verificable a costo cero).
- [x] Cumple con la Constitución del proyecto (Principios I, II, III, IV, V, VII, VIII, IX y X).

## Notas

- Los 16 ítems de control fueron verificados y validados exitosamente (16/16).
- Decisiones clave consolidadas en la especificación (09/11/2026):
  1. **Garantía estricta durante los 15 minutos (Criterios 2 y 3)**: Mientras corre la ventana de 15 minutos de reserva temporal conjunta, el cupo de la franja y el stock de insumos están 100% garantizados para el comensal. El rechazo por insumos agotados por concurrencia ocurre únicamente al intentar iniciar el pago (antes de establecer el bloqueo temporal). La interrupción por franja saturada ocurre únicamente si el comensal excede los 15 minutos de la ventana y otros comensales agotan los cupos del turno liberado.
  2. **Descuento atómico en bonificación al 100%**: Las órdenes bonificadas en su totalidad omiten el pasaje por la pasarela de pagos pero ejecutan el débito atómico y estricto de materias primas por receta y el envío directo a cocina, garantizando que el KDS reciba órdenes viables de preparar.
  3. **Retención temporal vs. débito real en estado pendiente**: Al devolver Mercado Pago un estado pendiente tras haber efectuado el pago el comensal, la retención provisoria de stock y franja se congela y mantiene activa a la espera de la resolución final asíncrona de la pasarela, sin ingresar a la cola del KDS ni debitar inventario real. Las aprobaciones tardías sobre órdenes ya vencidas por abandono previo quedan formalmente delegadas a una historia posterior de contingencias financieras.
  4. **Precisión de recetas**: Se garantiza el soporte exacto de cantidades fraccionarias (ej. gramos, mililitros) sin pérdidas de redondeo y bajo regla de todo-o-nada (cero débitos parciales).
  5. **Reproducibilidad a costo cero (RT-1 / RT-3)**: Verificación integral mediante tarjetas y credenciales de prueba de la pasarela y simulación controlada de condiciones de concurrencia e inventario.

