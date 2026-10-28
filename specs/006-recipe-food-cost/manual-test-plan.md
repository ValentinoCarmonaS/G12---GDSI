# Plan de Pruebas Manuales: Spec 006 - Recipe Food Cost

**Fecha**: 28/10/2026  
**Rama**: `006-recipe-food-cost`  
**Objetivo**: Validar manualmente en el entorno local el esquema transaccional N:M de recetas, las restricciones de estado del plato, el cálculo de Food Cost por porción (`ADM-5.1.2`) y la seguridad RBAC.

---

## 1. Preparación del Entorno Local

1. Levantar la base de datos PostgreSQL local:
   ```bash
   cd easyeat/backend
   docker compose up -d
   ```
2. Iniciar el backend con Java 21:
   ```bash
   mvn spring-boot:run
   ```
3. Iniciar el frontend (en otra terminal):
   ```bash
   cd easyeat/frontend
   npm run dev
   ```

---

## 2. Casos de Prueba Manuales

### Caso 1: Persistencia de Receta Técnica en la UI de Cocina (Chef)
- **Rol requerido**: `CHEF` (`carlos.cocina@laesquina.com` o usuario Chef del tenant).
- **Flujo Visual en Frontend**:
  1. Ingresá a la app y logueate con credenciales de Chef.
  2. Navegá a **Cocina** (`/cocina/platos`) y creá un plato nuevo o seleccioná un borrador (`DRAFT`).
  3. En la pestaña o paso **Receta**, abrí el modal **Seleccionar Insumo** (`IngredientSelectorModal`).
  4. Seleccioná materias primas del catálogo maestro (ej. Harina, Queso) y definí sus cantidades por porción.
  5. Hacé clic en **Guardar Receta**.
- **Resultado esperado en UI**:
  - La tabla de insumos de la receta se actualiza inmediatamente mostrando los insumos agregados y sus cantidades.
  - No permite agregar insumos duplicados (muestra alerta).
  - No permite cantidades $\le 0$.
  - La receta persiste en el backend de forma atómica (`PUT /api/v1/dishes/{id}/recipe`).

---

### Caso 2: Auditoría Financiera de Food Cost en la UI de Administración (Stakeholder / Admin)
- **Rol requerido**: `ADMINISTRADOR` / `ADMIN` (`admin@laesquina.com`).
- **Precondición**: El Chef completó la ficha técnica y la receta del plato, y lo envió a revisión (estado `PENDING_PRICE`).
- **Flujo Visual en Frontend**:
  1. Cerrá sesión e iniciá sesión con usuario Administrador.
  2. Navegá a la URL de auditoría del plato:  
     `http://localhost:5173/admin/dishes/{id_del_plato}/cost-audit`
- **Resultado esperado en UI**:
  - Se visualiza el componente **Auditoría Financiera de Costos (Food Cost)** (`CostAuditPanel`).
  - Muestra el nombre del plato, la tabla completa con cada insumo, cantidad por porción, unidad de medida, costo de compra unitario de inventario y subtotal.
  - En el pie de la tabla destaca en verde el **Costo Total de Materia Prima (Food Cost)** calculado de forma exacta (con 2 decimales).
  - En la sección inferior aparecen las **Etiquetas Dietarias Declaradas** (ej. `CELIAC`, `DAIRY`) asociadas al plato.
  - Dispone de un botón **Actualizar Costos** para refrescar los datos en tiempo real si hubo cambios en los precios de los insumos.

---

### Caso 3: Advertencia Visual de Insumos sin Costo de Compra
- **Rol requerido**: `ADMINISTRADOR`
- **Precondición**: Un plato en `PENDING_PRICE` tiene en su receta algún insumo que en el inventario tiene costo `0.00` o no tiene precio asignado.
- **Flujo Visual en Frontend**:
  1. Abrí la auditoría del plato en `/admin/dishes/{id}/cost-audit`.
- **Resultado esperado en UI**:
  - Aparece un banner de advertencia destacado en amarillo:  
    *“Advertencia de Insumos sin Precio: Uno o más insumos no tienen costo de adquisición cargado en inventario. El costo calculado es parcial.”*
  - Se listan los nombres específicos de los insumos sin precio.
  - En la fila del insumo se visualiza una insignia tipo badge: `Sin costo`.
  - La auditoría no se rompe: permite al stakeholder ver el costo parcial acumulado.

---

### Caso 4: Bloqueo de Auditoría si el Plato está en Borrador (State Gate)
- **Rol requerido**: `ADMINISTRADOR`
- **Precondición**: Un plato que aún se encuentra en estado `DRAFT` (el Chef aún no lo envió a revisión).
- **Flujo Visual en Frontend**:
  1. Intentá ingresar a `/admin/dishes/{draft_id}/cost-audit`.
- **Resultado esperado en UI**:
  - La pantalla no muestra datos financieros incorrectos.
  - Aparece un panel de aviso en rojo:  
    *“No se pudo realizar la auditoría: El plato debe estar en estado PENDING_PRICE (enviado a revisión) para auditar sus costos.”*

---

### Caso 5: Protección de Confidencialidad ante Usuarios no Administradores
- **Rol**: `CHEF` o `ENCARGADO`
- **Flujo Visual en Frontend**:
  1. Estando logueado como Chef o Encargado, intentá navegar a `/admin/dishes/{id}/cost-audit`.
- **Resultado esperado en UI**:
  - El sistema activa el `RoleRouteGuard` y bloquea inmediatamente el acceso, redirigiendo al espacio correspondiente (`/cocina` o `/mostrador`).
  - Si intentara acceder directo a la API, el backend responde `403 Forbidden`.
  - Se garantiza que el personal de cocina no tiene visibilidad de los costos de compra ni de los márgenes financieros.

