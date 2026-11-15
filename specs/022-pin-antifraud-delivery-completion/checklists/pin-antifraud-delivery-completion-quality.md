# Requirements Quality Checklist: Protocolo Antifraude de Entrega y Cierre de Orden

**Purpose**: Requirements-quality review gate ("unit tests for English") for feature 022 — validates that the functional requirements, business invariants, 4-digit security code generation, monospace typography and oral verification, direct delivery action without modal, green confirmation and anti-double-click protection, atomic and indivisible terminal closure, idempotency, card removal, code purge, and final diner tracking view are complete, unambiguous, consistent, measurable, and traceable. It does **not** validate runtime code behavior or test implementation.  
**Created**: 15/11/2026  
**Feature**: [spec.md](../spec.md)  
**Scope of sources**: `specs/022-pin-antifraud-delivery-completion/spec.md`, `.specify/memory/constitution.md`

**Note**: This custom checklist is generated following the Spec Kit requirements quality standard.  
**Review Ownership**: This checklist is a reviewer-owned requirements-quality review artifact. Mark an item `[x]` only when the reviewer determines the requirements-quality criterion is satisfied.  
**Marker Semantics**: `[x]` means the criterion has been reviewed and satisfied for requirements quality. It does not mean implementation work is complete.  
**Authority rule**: Constitution Principle I makes `spec.md` the authority on required behavior. Where this checklist reports an ambiguity or gap, `spec.md` must be refined.

---

## A. Código Numérico de Seguridad y Protocolo Antifraude Oral (ENC-3.2.1)

- [x] CHK001 - ¿Está especificada la generación de un código numérico de seguridad aleatorio de exactamente 4 dígitos con rango determinista entre 1000 y 9999 inclusive al confirmarse y pagarse la orden? [Completitud, Trazabilidad, Spec §RF-001, §Invariantes 1, §AC-1]
- [x] CHK002 - ¿Se encuentra formalizada la inmutabilidad y estabilidad absoluta del código numérico de seguridad durante todo el ciclo de vida del pedido, prohibiendo su regeneración o rotación? [Inmutabilidad, Integridad de Dominio, Spec §RF-002, §Invariantes 2, §Clarifications]
- [x] CHK003 - ¿Está definida la disponibilidad y formato del código numérico en tipografía monospace con espaciado amplio ("4 8 2 1") en la pantalla de seguimiento del comensal? [Formato Visual, Accesibilidad, Spec §RF-003, §Invariantes 3, §AC-1]
- [x] CHK004 - ¿Se especifica la exhibición destacada y legible del código de seguridad en tipografía monospace con espaciado amplio en la tarjeta desplegada de mostrador para el cotejo visual? [Ergonomía, Mostrador, Spec §RF-004, §Invariantes 3, §AC-2, §SC-003]
- [x] CHK005 - ¿Está explícitamente formalizado que el protocolo opera sin lectores ópticos, escáneres de código de barras ni lectores QR externos? [Límites Operativos, Ausencia de Hardware, Spec §RF-005, §Invariantes 4, §Fuera de Alcance]
- [x] CHK006 - ¿Se contempla el tratamiento sin truncamiento de los valores límite del rango (1000 y 9999)? [Casos Borde, Robustez, Spec §Caso Borde 1, §SC-001]
- [x] CHK007 - ¿Está definido el comportamiento operativo ante discrepancia verbal del código recitado por el comensal frente al mostrado, reteniendo la bandeja física sin validación algorítmica por software? [Protocolo Humano, Seguridad Operativa, Spec §Caso Borde 3, §Clarifications, §Invariantes 4]
- [x] CHK008 - ¿Se especifica el comportamiento ante órdenes en estado listo sin código de seguridad como inconsistencia previa sin bloquear la interfaz? [Casos Borde, Resiliencia, Spec §Caso Borde 10]

---

## B. Confirmación de Coincidencia y Acción Directa de Entrega (ENC-3.3.1)

- [x] CHK009 - ¿Está especificada la presencia del control de acción directa identificado con el rótulo exacto "ENTREGAR PEDIDO" en la tarjeta desplegada de mostrador? [Identidad Visual, Ergonomía, Spec §RF-006, §AC-1]
- [x] CHK010 - ¿Se formaliza la ejecución directa mediante un único clic sin diálogos modales ni pantallas emergentes de confirmación intermedia? [Fluidez Operativa, UX, Spec §RF-007, §Invariantes 5, §Clarifications, §SC-004]
- [x] CHK011 - ¿Se define el deshabilitado instantáneo del botón "ENTREGAR PEDIDO" en el momento exacto del clic para proteger contra dobles clics y peticiones duplicadas? [Protección Concurrente, UX, Spec §RF-008, §Invariantes 10, §AC-2, §Caso Borde 4, §SC-004]
- [x] CHK012 - ¿Está especificada la exhibición inmediata de la confirmación visual en color verde con la leyenda textual exacta "Entrega Confirmada" tras accionar el botón? [Feedback Inmediato, Claridad, Spec §RF-009, §AC-1, §SC-004]
- [x] CHK013 - ¿Se contempla el escenario de clics múltiples o repetitivos en rápida sucesión, garantizando que solo se despacha una única solicitud de cierre? [Robustez, Control de Eventos, Spec §Caso Borde 4, §RF-008]
- [x] CHK014 - ¿Está contemplada la contingencia de fallo de comunicación de red al accionar la entrega, reestableciendo el botón y admitiendo reintento manual? [Resiliencia de Red, Manejo de Excepciones, Spec §RF-018, §Clarifications]

---

## C. Cierre Terminal Atómico, Idempotencia y Actualización de Pantallas (ENC-3.3.2)

- [x] CHK015 - ¿Está especificada la transición atómica e indivisible que actualiza de manera simultánea el estado de la orden y del ticket de cocina a "completada"? [Atomicidad Transaccional, Integridad, Spec §RF-010, §Invariantes 6, §AC-1, §SC-005]
- [x] CHK016 - ¿Se define el registro exacto del instante temporal en que la entrega queda confirmada (`completed_at`)? [Auditoría Temporal, Consistencia, Spec §RF-011, §Invariantes 8, §AC-1]
- [x] CHK017 - ¿Se encuentra formalizada la garantía de reversión total ante fallos de persistencia (o se aplica por completo o no cambia nada)? [Indivisibilidad, Spec §RF-012, §Invariantes 6, §AC-1]
- [x] CHK018 - ¿Está definida la remoción inmediata y automática de la tarjeta de mostrador de la vista activa de despacho al procesar la confirmación afirmativa? [Flujo de Despacho, Reactividad, Spec §RF-013, §AC-2, §SC-006]
- [x] CHK019 - ¿Se garantiza que la desaparición de la tarjeta entregada no altera, colapsa ni resetea el estado de despliegue de las demás tarjetas abiertas? [Invarianza de Interfaz, Aislamiento, Spec §RF-014, §Invariantes 11, §Caso Borde 7]
- [x] CHK020 - ¿Está especificada la regla de idempotencia determinista donde solicitudes repetidas o duplicadas para órdenes ya completadas responden con éxito sin alterar datos ni marcas temporales? [Idempotencia, Resiliencia, Spec §RF-015, §Invariantes 8, 9, §AC-2, §Casos Borde 5, 6, §SC-007]
- [x] CHK021 - ¿Se define la conmutación definitiva en la pantalla de seguimiento del comensal exhibiendo el mensaje exacto "Pedido Entregado — ¡Que disfrutes tu comida!" junto al resumen de compra y purga del PIN? [Cierre de Experiencia, Seguridad, Spec §RF-016, §Invariantes 12, §AC-3, §Caso Borde 8, §SC-008]
- [x] CHK022 - ¿Se especifica la inhibición o cese de sondeos periódicos en la pantalla del comensal una vez alcanzado el estado completado terminal? [Optimización de Recursos, Estado Terminal, Spec §RF-017, §Invariantes 12, §Clarifications]
- [x] CHK023 - ¿Se contempla la estabilidad de consultas posteriores del comensal en días u horas subsiguientes mostrando el estado cerrado sin exponer el código? [Persistencia Histórica, Spec §Caso Borde 9]
- [x] CHK024 - ¿Está formalizada la irreversibilidad estricta del estado "completada" impidiendo reaperturas o cancelaciones posteriores? [Invariantes de Ciclo de Vida, Spec §Invariantes 7, §Fuera de Alcance]
- [x] CHK025 - ¿Se encuentran delimitados con precisión los alcances excluidos (sin validación algorítmica, sin impresión fiscal, sin modales, sin gestión de no-shows)? [Fronteras de Dominio, Claridad, Spec §Fuera de Alcance, §Clarifications]
