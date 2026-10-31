# Plan de pruebas manuales: Spec 006 - Recipe Food Cost

**Fecha**: 28/10/2026  
**Rama**: `006-recipe-food-cost`  
**Historia de usuario**: `ADM-5.1.2` (Auditoría de receta y Food Cost base)  
**Objetivo**: Validar manualmente en el entorno local la auditoría de costos de materia prima por porción, la visualización de recetas técnicas, el cómputo de subtotales, las etiquetas dietarias requeridas, las advertencias de insumos sin costo y las compuertas de seguridad y estado.

---

## 1. Preparación del entorno local

1. Iniciar los contenedores de base de datos PostgreSQL:
   ```bash
   cd easyeat/backend
   docker compose up -d
   ```
2. Ejecutar la aplicación backend (Spring Boot con Java 21):
   ```bash
   mvn spring-boot:run
   ```
3. Ejecutar la aplicación frontend (en una terminal secundaria):
   ```bash
   cd easyeat/frontend
   npm run dev
   ```

---

## 2. Casos de prueba manuales

### Caso 1: Acceso a la auditoría desde el panel de administración
- **Rol requerido**: `ADMINISTRADOR` / `ADMIN` (ejemplo: `admin@laesquina.com`).
- **Precondición**: Existe al menos un plato en estado `PENDING_PRICE` enviado a revisión técnica por el Chef.
- **Flujo en frontend**:
  1. Iniciar sesión con credenciales de usuario Administrador.
  2. Acceder al panel de administración general (`/admin`).
  3. Ubicar la sección de notificaciones operativas "Platos Enviados a Revisión (Pendientes de Precio)".
  4. Verificar la presencia del botón **Auditar Costo** asociado al plato pendiente.
  5. Hacer clic en **Auditar Costo**.
- **Resultado esperado**:
  - El sistema navega a la ruta `/admin/dishes/{id}/cost-audit`.
  - Se visualiza el componente de auditoría financiera correspondiente al plato seleccionado.

---

### Caso 2: Desglose de insumos, subtotales, Food Cost total y etiquetas dietarias
- **Rol requerido**: `ADMINISTRADOR` / `ADMIN`.
- **Precondición**: El plato posee receta técnica asignada y todos sus insumos tienen costo de compra unitario registrado en el catálogo maestro de inventario.
- **Flujo en frontend**:
  1. Acceder a `/admin/dishes/{id}/cost-audit`.
- **Resultado esperado**:
  - El encabezado visualiza el nombre del plato y el identificador auditado.
  - La tabla detalla cada ingrediente de la receta: nombre, cantidad técnica por porción, unidad de medida, costo unitario de compra y subtotal monetario redondeado a dos decimales.
  - El pie de la tabla muestra de manera prominente el **Costo Total de Materia Prima (Food Cost)**, coincidiendo exactamente con la suma aritmética de los subtotales.
  - La sección inferior presenta las **Etiquetas Dietarias Declaradas** (por ejemplo: `CELIAC`, `VEGETARIAN`, `DAIRY`), informando las restricciones alimentarias vinculadas al plato.
  - El botón **Actualizar Costos** permite refrescar el cálculo en tiempo real ante eventuales actualizaciones de compras.

---

### Caso 3: Advertencia visual de insumos sin precio de compra
- **Rol requerido**: `ADMINISTRADOR` / `ADMIN`.
- **Precondición**: El plato posee en su receta al menos un insumo cuyo costo de adquisición en catálogo sea nulo o igual a cero (`unit_purchase_cost <= 0`).
- **Flujo en frontend**:
  1. Acceder a `/admin/dishes/{id}/cost-audit` para dicho plato.
- **Resultado esperado**:
  - Se despliega una alerta visual destacada en color amarillo con el título *“Advertencia de Insumos sin Precio”*.
  - El mensaje indica explícitamente qué insumos carecen de costo registrado en el inventario.
  - En la fila del insumo afectado, la columna de costo muestra el indicador *“Sin costo”*.
  - El cálculo del Food Cost total no se interrumpe: presenta el costo parcial acumulado de los insumos que sí cuentan con precio.

---

### Caso 4: Bloqueo de auditoría en platos en estado borrador (compuerta HTTP 409)
- **Rol requerido**: `ADMINISTRADOR` / `ADMIN`.
- **Precondición**: El plato se encuentra en estado `DRAFT` (aún en edición técnica por el Chef).
- **Flujo en frontend**:
  1. Intentar acceder directamente a la URL `/admin/dishes/{draft_id}/cost-audit`.
- **Resultado esperado**:
  - El backend rechaza la petición con código de estado HTTP `409 Conflict`.
  - La interfaz de usuario muestra un panel informativo de advertencia:  
    *“El plato debe estar en estado PENDING_PRICE (enviado a revisión) para auditar sus costos.”*
  - No se exponen datos financieros inconsistentes.

---

### Caso 5: Bloqueo de plato sin receta técnica (compuerta HTTP 400)
- **Rol requerido**: `ADMINISTRADOR` / `ADMIN`.
- **Precondición**: El plato carece de ingredientes asociados en su receta.
- **Flujo en frontend**:
  1. Acceder a la auditoría de un plato sin ingredientes.
- **Resultado esperado**:
  - El backend retorna HTTP `400 Bad Request`.
  - La interfaz despliega el mensaje de error:  
    *“El plato no tiene ingredientes asignados en su receta técnica.”*

---

### Caso 6: Control de acceso basado en roles y confidencialidad (RBAC)
- **Rol evaluado**: `CHEF`, `ENCARGADO` o usuario no autenticado.
- **Flujo en frontend**:
  1. Iniciar sesión con un rol operativo de cocina o salón.
  2. Intentar ingresar a la ruta `/admin/dishes/{id}/cost-audit`.
- **Resultado esperado**:
  - El guardia de rutas del frontend (`RoleRouteGuard`) bloquea el acceso y redirige al espacio de trabajo autorizado (`/cocina` o `/mostrador`).
  - Las invocaciones directas al endpoint `GET /api/v1/dishes/{id}/cost-audit` son rechazadas por Spring Security con HTTP `403 Forbidden`.
  - Se resguarda la confidencialidad de los costos de adquisición y márgenes financieros del negocio frente al personal operativo.
