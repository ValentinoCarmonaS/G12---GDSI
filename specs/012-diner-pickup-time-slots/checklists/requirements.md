# Control de Calidad de la Especificación: Selección de Modalidad de Retiro y Motor de Reservas

**Propósito**: Validar la completitud y calidad de la especificación funcional antes de avanzar a la planificación técnica (`speckit-plan`).  
**Fecha de Creación**: 31/10/2026  
**Especificación**: [spec.md](file:///c:/Users/seeba/OneDrive/Documentos/FIUBA/git/G12---GDSI/specs/012-diner-pickup-time-slots/spec.md)

## Calidad del Contenido

- [x] Ausencia de detalles de implementación técnica (lenguajes, frameworks, endpoints o SQL).
- [x] Foco exclusivo en valor para el usuario, reglas de negocio e invariantes de dominio.
- [x] Redacción clara para stakeholders no técnicos y comensales.
- [x] Todas las secciones obligatorias completadas con rigor y consistencia.

## Completitud de Requisitos

- [x] Sin marcadores `[NEEDS CLARIFICATION]` pendientes (todos los puntos fueron acordados en Grill-Me).
- [x] Requisitos funcionales testeables, unívocos y con numeración correlativa (`FR-001` a `FR-022`).
- [x] Criterios de éxito medibles y expresados de forma agnóstica a la tecnología (`SC-001` a `SC-006`).
- [x] Todos los escenarios de aceptación definidos en formato Given-When-Then.
- [x] Casos borde contemplados (desfase de reloj, envejecimiento en checkout, colisión concurrente, cierres comerciales).
- [x] Alcance claramente delimitado mediante la Matriz de Delimitación por Sprint (separando Sprint 3 de Sprint 4 y 5).
- [x] Supuestos y dependencias de upstream formalmente identificados.

## Preparación de la Feature (Readiness)

- [x] Todos los requisitos funcionales cuentan con criterios de aceptación verificables.
- [x] Los escenarios de usuario cubren los flujos primarios (`COM-6.2.1` a `COM-6.2.4`).
- [x] La feature cumple con los resultados cuantificables de los Criterios de Éxito.
- [x] Cumple con la Constitución del proyecto (Principios I, II, III, IV, V y VII).

## Notas

- Los 16 ítems de control fueron verificados exitosamente (16/16).
- Decisiones críticas acordadas durante la sesión de Grill-Me del 31/10/2026:
  1. **Persistencia y Mantenibilidad**: La orden se crea en estado `PENDING_PAYMENT` (o `RESERVED`) al completar el checkout, asegurando de forma indivisible el cupo de la franja. En el Sprint 4, la pasarela de pagos consumirá directamente esta orden y transicionará su estado a `PAID`, evitando tablas temporales intermedias descartables.
  2. **Lead Time y Visibilidad**: Margen de preparación por defecto fijado en 30 minutos. Las franjas pasadas o comprendidas en el buffer se muestran deshabilitadas (gris atenuado con leyenda indicativa) para mantener la cuadrícula temporal del local predecible y transparente.
  3. **Estrategia en Tiempo Real**: Polling periódico liviano (5-10 segundos) durante la estancia en checkout con validación atómica obligatoria en el backend al momento de la confirmación final, respetando la arquitectura previa a WebSockets (Sprint 5).
  4. **Fuera de Horario Operativo y Articulación con Spec 008**: Si el establecimiento está cerrado, se respeta la evolución natural del catálogo: el modo 'solo lectura' de Spec 008 restringía los pedidos inmediatos por ausencia de carrito en el Sprint 2. En el Sprint 3, el comensal puede conformar su carrito para turnos futuros; en el checkout, la opción "Lo antes posible" se deshabilita con aviso explicativo y se exige seleccionar "Para más tarde" dentro del calendario operativo futuro.

