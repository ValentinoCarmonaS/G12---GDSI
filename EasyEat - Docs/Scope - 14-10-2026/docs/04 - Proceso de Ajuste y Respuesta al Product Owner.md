# Definición del MVP Final: Proceso de Ajuste y Respuesta al Product Owner

**Proyecto:** EasyEat (Gestión Gastronómica SaaS Multi-Tenant)  
**Fecha:** 2026-10-14  
**Equipo:** Grupo 12 (6 integrantes, FIUBA GDSI 2026)  
**Documentos relacionados:**
- [`01 - Decisiones de Planificación del MVP.md`](./01%20-%20Decisiones%20de%20Planificaci%C3%B3n%20del%20MVP.md) (Registro de decisiones de planificación)
- [`02 - Roadmap de Sprints del MVP.md`](./02%20-%20Roadmap%20de%20Sprints%20del%20MVP.md) (Distribución de historias por sprint)
- [`03 - Justificación de Estimaciones Técnicas.md`](./03%20-%20Justificaci%C3%B3n%20de%20Estimaciones%20T%C3%A9cnicas.md) (Justificación técnica de las 82 historias del scope)

---

## 1. Propósito

Este documento describe cómo se definió el alcance del MVP Final a partir del feedback recibido del Product Owner sobre el plan acotado previo (357 SP). Se detallan las observaciones analizadas, los criterios técnicos adoptados para recalibrar historias y los recortes aplicados para fijar un backlog sostenible.

---

## 2. Observaciones del Product Owner sobre el Plan Anterior

El plan previo contemplaba 357 Story Points (SP) sobre una capacidad teórica de 360 SP (10 horas semanales por integrante a lo largo de 6 sprints). La revisión del Product Owner planteó tres problemas concretos:

### Margen de contingencia insuficiente
El plan dejaba solo 3 SP libres en todo el proyecto (<1% de holgura). Con tareas que involucran pasarelas de pago y comunicación en tiempo real vía WebSockets, cualquier bloqueo técnico ponía en riesgo la entrega. Además, la carga por sprint era despareja, con picos de 63 SP en el Sprint 1 y 65 SP en el Sprint 4. El PO solicitó apuntar a un promedio de 50 SP por sprint para contar con un buffer real de contingencia.

### Estimaciones duplicadas en módulos de autenticación
El backlog asignaba 22 SP repartidos en historias de login por rol (Comensal, Encargado, Chef y Administrador), redirecciones de vistas y recuperaciones de clave. A nivel de implementación, esto se resuelve con un único endpoint de autenticación con JWT en backend y guards de rutas por rol en frontend. Las historias atómicas sobrestimaban el trabajo al tratar cada acceso como un desarrollo independiente.

### Alcance no alineado con el Product Vision
El plan conservaba módulos de alta carga que no figuraban en el Product Vision original ni en la matriz de Features 2.0:
- Asistente con IA (13 SP).
- Pedidos colaborativos y división de cuenta (29 SP).
- Login federado con Google (8 SP).
- Sistema de reseñas y comentarios (18 SP), funcionalidad que el documento *Es - No Es* descarta explícitamente ("No es una red social de reseñas").

El PO pidió revisar qué elementos eran prescindibles para validar el flujo central: Pagar → Cocinar → Entregar.

---

## 3. Decisiones de Ajuste Acordadas

Para responder a las observaciones del PO, se evaluaron alternativas y se acordaron las siguientes definiciones:

1. Feedback PO: nuestro plan anterior contemplaba 357 SP con 3 SP de margen
2. Fijar meta: 300 SP aprox. (50 SP por sprint)
3. Recalibrar Auth: base de 3 SP en COMENSAL y 1 SP en accesos secundarios (ENCARGADO, CHEF y ADMINISTRADOR)
4. Excluir periféricos: IA, grupos, Google SSO y reseñas
5. Reordenar dependencias: restricciones al Sprint 1, pagos al Sprint 4
6. BACKLOG FINAL CONSOLIDADO: 279 SP, buffer de 81 SP

### Meta de capacidad y buffer
Se fijó un techo de planificación de 300 SP para los 6 sprints, alineado con la pauta de 50 SP por sprint sugerida por el PO. Esto asegura una reserva de contingencia de al menos 60 SP (16.7%) sobre la capacidad nominal de 360 SP para absorber demoras en integraciones y pruebas.

### Calibración de historias de autenticación
Se mantuvo la separación de historias por rol en el backlog para no romper la trazabilidad con la matriz USM, pero se ajustaron las estimaciones al costo real de implementación:
- `COM-1.2.1` (Login central): pasó de 2 a 3 SP para absorber el desarrollo del endpoint común `POST /api/auth/login`, hashing con bcrypt y generación del JWT.
- `ENC-1.1.1` (Login encargado): se redujo de 3 a 1 SP al reutilizar el endpoint base.
- `ENC-1.2.1` (Redirección mostrador): se redujo de 3 a 1 SP (guard de ruta en frontend).
- `CHF-1.1.1` (Login chef): se redujo de 2 a 1 SP.
- `CHF-1.2.1` (Redirección cocina): se redujo de 3 a 1 SP (guard de ruta en frontend).
- `ADM-1.1.1` (Login administrador): se redujo de 3 a 1 SP.
- `ADM-1.2.1` (Recuperar clave admin): se redujo de 3 a 1 SP al apoyarse en el servicio de correo ya contemplado en `COM-1.3.1`.

Ahorro neto: 10 SP.

### Exclusión de módulos secundarios
Se eliminaron del alcance 68 SP correspondientes a funcionalidades accesorias:
- Asistente con IA (`COM-3.1.3`): 13 SP.
- Pedidos colaborativos y división de cuenta (`COM-2.1.1`, `COM-2.2.1`, `COM-2.3.1`, `COM-7.2.2`): 29 SP.
- Login con Google (`COM-1.1.2`, `COM-1.2.2`): 8 SP (el registro nativo por correo cubre la necesidad).
- Sistema de reseñas (`COM-5.2.1`, `COM-5.2.2`, `COM-9.1.1`, `COM-9.1.2`, `ADM-6.2.2`): 18 SP.

### Corrección de dependencias y balance de sprints
El plan previo presentaba inconsistencias de orden: la configuración de restricciones alimentarias (`COM-1.4.3`) figuraba en el Sprint 6, cuando los filtros de catálogo asociados (`COM-4.2.1`) se desarrollaban en el Sprint 1. Asimismo, la confirmación de pago (`COM-7.3.1`) estaba separada de la pasarela de cobro.

Se corrigió la secuencia:
1. Restricciones alimentarias se adelantaron al Sprint 1 junto con la autenticación básica y los roles.
2. La confirmación de pedido y generación del código numérico se integraron en el Sprint 4 con la pasarela de pagos.
3. El KDS de cocina en tiempo real se concentró en el Sprint 5.
4. El mostrador de entrega y los reportes de administración cerraron en el Sprint 6.

### Alcance final
Con los recortes y calibraciones, el backlog quedó en 279 SP (82 historias de usuario). Se optó por mantener este valor en lugar de recortar más, ya que cumple con creces el límite de 50 SP por sprint y deja un buffer de 81 SP (22.5%).

---

## 4. Balance Numérico del Rediseño

| Componente | SP Previos | Ajuste | SP Finales | Motivo |
|---|:---:|:---:|:---:|---|
| **Backlog inicial (MVP Acotado)** | **357** | N/A | N/A | Alcance presentado al PO |
| Login base COM (`COM-1.2.1`) | 2 | +1 | 3 | Concentra el endpoint JWT y sesiones |
| Login ENC (`ENC-1.1.1`) | 3 | -2 | 1 | Reutilización de endpoint base |
| Guard ENC (`ENC-1.2.1`) | 3 | -2 | 1 | Regla condicional en frontend |
| Login CHF (`CHF-1.1.1`) | 2 | -1 | 1 | Reutilización de endpoint base |
| Guard CHF (`CHF-1.2.1`) | 3 | -2 | 1 | Regla condicional en frontend |
| Login ADM (`ADM-1.1.1`) | 3 | -2 | 1 | Reutilización de endpoint base |
| Recuperación clave ADM (`ADM-1.2.1`) | 3 | -2 | 1 | Reutiliza servicio SMTP de comensal |
| Asistente IA (`COM-3.1.3`) | 13 | -13 | 0 | Fuera del Product Vision |
| Pedidos colaborativos (`COM-2.*`, `COM-7.2.2`) | 29 | -29 | 0 | Complejidad técnica no esencial |
| Google SSO (`COM-1.1.2`, `COM-1.2.2`) | 8 | -8 | 0 | Dependencia externa prescindible |
| Reseñas y calificaciones (5 historias) | 18 | -18 | 0 | Descartado en *Es - No Es* |
| **Total backlog MVP Final** | N/A | **-78** | **279** | **82 historias de usuario** |

---

## 5. Distribución por Sprint y Margen de Contingencia

La distribución resultante mantiene un ritmo parejo a lo largo de las 6 semanas:

| Sprint | Foco | Historias | SP Asignados | Capacidad Nominal | Buffer Libre |
|:---:|---|:---:|:---:|:---:|:---:|
| **1** | Infraestructura base, auth JWT, restricciones y staff | 17 | 46 SP | 60 SP | 14 SP |
| **2** | Platos, recetas, costeo y catálogo visible | 13 | 49 SP | 60 SP | 11 SP |
| **3** | Detalle de plato, carrito y reservas por franjas | 19 | 46 SP | 60 SP | 14 SP |
| **4** | Pasarela de pagos, confirmación y descuento de stock | 8 | 48 SP | 60 SP | 12 SP |
| **5** | KDS de cocina en tiempo real y avisos de estado | 14 | 45 SP | 60 SP | 15 SP |
| **6** | Despacho en mostrador, código manual y métricas BI | 11 | 45 SP | 60 SP | 15 SP |
| **Total** | **Flujo completo Pagar → Cocinar → Entregar** | **82** | **279 SP** | **360 SP** | **81 SP (22.5%)** |

El promedio de carga es de 46.5 SP por sprint y ningún sprint supera los 49 SP, lo que resuelve la sobrecarga observada por el Product Owner y asegura un margen de contingencia operativo.
