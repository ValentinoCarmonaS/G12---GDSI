# Decisiones de Planificación del MVP: Escenario Final

**Proyecto:** EasyEat (Gestión Gastronómica SaaS Multi-Tenant)  
**Fecha de revisión:** 2026-10-14  
**Equipo:** Grupo 12 (6 integrantes, FIUBA GDSI 2026)  
**Documentos relacionados:**
- [`02 - Roadmap de Sprints del MVP.md`](./02%20-%20Roadmap%20de%20Sprints%20del%20MVP.md) (Distribución de historias por sprint)
- [`04 - Proceso de Ajuste y Respuesta al Product Owner.md`](./04%20-%20Proceso%20de%20Ajuste%20y%20Respuesta%20al%20Product%20Owner.md) (Bitácora de negociación y proceso dialéctico)
- [`03 - Justificación de Estimaciones Técnicas.md`](./03%20-%20Justificaci%C3%B3n%20de%20Estimaciones%20T%C3%A9cnicas.md) (Justificación técnica de las 82 historias del scope)

---

## 1. Contexto

La revisión del Product Owner (PO) sobre el plan acotado previo (357 SP) señaló tres puntos a corregir:

1. **Holgura insuficiente y sobrecarga:** Con 357 SP sobre 360 SP de capacidad máxima teórica (10 horas semanales por integrante), quedaban solo 3 SP libres (<1%). Además, los sprints 1 (63 SP) y 4 (65 SP) estaban sobrecargados. El PO solicitó apuntar a un promedio de 50 SP por sprint para tener un margen de contingencia real.
2. **Estimaciones duplicadas en autenticación:** Se asignaron 22 SP a historias atomizadas de login, redirecciones y recuperación de contraseñas por rol, cuando en la práctica se resuelven con un único endpoint de autenticación con JWT y guards de rutas en el cliente.
3. **Funcionalidades fuera del alcance central:** Se mantuvieron historias de alto costo que no figuraban en el Product Vision ni en las prioridades de Features 2.0 (asistente IA, compras colaborativas, Google SSO y reseñas). El PO pidió enfocar el MVP en el flujo central: Pagar → Cocinar → Entregar.

---

## 2. Decisiones de Planificación

### Decisión 1: Capacidad nominal y reserva de contingencia
- **Capacidad nominal (6 sprints):** 6 personas × 10 hs/semana × 6 semanas = 360 SP.
- **Backlog acordado:** 279 SP distribuidos en 82 historias de usuario.
- **Carga por sprint:** Entre 45 y 49 SP (promedio: 46.5 SP/sprint), eliminando picos mayores a 50 SP.
- **Reserva de contingencia:** 81 SP libres (22.5% de la capacidad nominal).

*Criterio:* Disponer de un margen de 13.5 SP libres por sprint para absorber demoras en integraciones técnicas complejas (pasarela de pagos, WebSockets en cocina) y tareas de testing sin comprometer las fechas de entrega.

### Decisión 2: Calibración técnica de autenticación y roles (RBAC)
Se conservaron las historias por rol para mantener la trazabilidad con la matriz USM, ajustando sus estimaciones al costo real de implementación:
- `COM-1.1.1` (Registro comensal): 3 SP.
- `COM-1.2.1` (Login base con JWT y manejo de sesión): 3 SP.
- `COM-1.3.1` (Servicio de reseteo de clave por correo): 3 SP.
- `ENC-1.1.1` (Login encargado): 1 SP (reutiliza el endpoint común).
- `ENC-1.2.1` (Redirección a mostrador): 1 SP (guard de ruta en frontend).
- `CHF-1.1.1` (Login chef): 1 SP (reutiliza el endpoint común).
- `CHF-1.2.1` (Redirección a cocina): 1 SP (guard de ruta en frontend).
- `ADM-1.1.1` (Login administrador): 1 SP (reutiliza el endpoint común).
- `ADM-1.2.1` (Recuperar clave admin): 1 SP (reutiliza el servicio de correo de comensal).

*Resultado:* El esfuerzo estimado para el módulo de acceso bajó de 22 SP a 12 SP (ahorro de 10 SP).

### Decisión 3: Exclusión de funcionalidades secundarias
Se dieron de baja 68 SP de módulos prescindibles para validar la operatoria del restaurante:

| Módulo excluido | Historias afectadas | SP reducidos | Motivo de exclusión |
|---|---|:---:|---|
| Asistente IA | `COM-3.1.3` (13 SP) | 13 SP | Incertidumbre técnica y falta de respaldo en el Product Vision. |
| Pedidos colaborativos y división de cuenta | `COM-2.1.1` (8 SP), `COM-2.2.1` (3 SP), `COM-2.3.1` (5 SP), `COM-7.2.2` (13 SP) | 29 SP | Complejidad de salas concurrentes y pagos divididos; no indispensable para compras individuales. |
| Login con Google (SSO) | `COM-1.1.2` (5 SP), `COM-1.2.2` (3 SP) | 8 SP | Dependencia externa prescindible; el registro nativo por correo cubre el acceso. |
| Sistema de reseñas y calificaciones | `COM-5.2.1` (2 SP), `COM-5.2.2` (3 SP), `COM-9.1.1` (5 SP), `COM-9.1.2` (5 SP), `ADM-6.2.2` (3 SP) | 18 SP | Contradice el documento *Es - No Es* ("No es una red social de reseñas"). |

Total reducido por exclusiones: 68 SP.

### Decisión 4: Reordenamiento de dependencias y flujo de entrega
Se reorganizó la secuencia de sprints para resolver inconsistencias funcionales:
1. **Restricciones alimentarias (`COM-1.4.3` - 5 SP):** Se movió del Sprint 6 al Sprint 1 para que los filtros de catálogo del Sprint 2 dispongan de los datos del perfil del usuario.
2. **Confirmación de pago (`COM-7.3.1` - 5 SP):** Se movió del Sprint 6 al Sprint 4 para vincularse de inmediato al procesamiento de la tarjeta y la emisión del código de retiro.
3. **Secuencia KDS y despacho:** El KDS de cocina en tiempo real se implementa en el Sprint 5 y la vista de entrega en mostrador en el Sprint 6.

---

## 3. Comparativa de Escenarios de Planificación

| Parámetro | Escenario Ideal | Escenario Acotado | Escenario Final |
|---|:---:|:---:|:---:|
| Dedicación por persona | 12 hs/semana | 10 hs/semana | 10 hs/semana |
| Capacidad nominal total (6 sprints) | 432 SP | 360 SP | 360 SP |
| Backlog planificado | 419 SP | 357 SP | 279 SP |
| Margen de contingencia | 13 SP (3.0%) | 3 SP (0.8%) | 81 SP (22.5%) |
| Carga media por sprint | 69.8 SP | 59.5 SP | 46.5 SP |
| Sprint con mayor carga | 72 SP | 65 SP (Sprint 4) | 49 SP (Sprint 2) |
| Alcance del flujo central | Completo + extras | Completo + extras | Pagar → Cocinar → Entregar |
