# Guía Rápida de Validación y Ejecución (Phase 1)

**Característica**: Gestión de Personal, RBAC e Inventario Inicial de Ingredientes  
**Rama**: `004-staff-mgmt-rbac-inventory`  
**Fecha**: 2026-10-20  
**Estado**: Completado  

---

## 1. Prerrequisitos y Configuración del Entorno

### Requisitos de Software
- **Java**: OpenJDK 21 (LTS).
- **Maven**: 3.9.x o superior.
- **Docker**: Docker Engine 24.x o superior con soporte para Testcontainers.
- **PostgreSQL**: 16.x (para pruebas manuales locales; Testcontainers gestiona su propio contenedor en tests automáticos).

### Variables de Entorno Requeridas
```bash
export JWT_SECRET="c2VjdXJlX2p3dF9zZWNyZXRfa2V5X2Zvcl9lYXN5ZWF0X3BsYXRmb3JtXzIwMjYh"
export SPRING_PROFILES_ACTIVE="test"
```

### Ejecución de Pruebas Automatizadas
Para ejecutar la suite completa de pruebas unitarias, de integración y de seguridad:
```bash
cd easyeat/backend
./mvnw clean test
```

---

## 2. Escenarios de Validación de Extremo a Extremo

Los siguientes escenarios prueban el funcionamiento integral de la funcionalidad implementada. Los contratos de carga útil y respuestas detalladas se encuentran referenciados en [contracts/openapi.yaml](file:///c:/Users/seeba/OneDrive/Documentos/FIUBA/git/G12---GDSI/specs/004-staff-mgmt-rbac-inventory/contracts/openapi.yaml) y las estructuras de datos en [data-model.md](file:///c:/Users/seeba/OneDrive/Documentos/FIUBA/git/G12---GDSI/specs/004-staff-mgmt-rbac-inventory/data-model.md).

---

### Escenario 1: Alta de Empleado y Provisión de Credencial Temporal Única (ADM-3.1.1)

**Objetivo**: Verificar que un Administrador puede registrar un empleado en su establecimiento y que la credencial se entrega una única vez en texto plano para visualización en pantalla.

1. **Autenticación como Administrador**:
   Obtener token JWT de Administrador para el `tenant_id` `11111111-1111-1111-1111-111111111111`.
2. **Petición**:
   `POST /api/v1/admin/staff`
   ```json
   {
     "fullName": "Carlos Garmendia",
     "email": "carlos.garmendia@easyeat.com",
     "role": "CHEF"
   }
   ```
3. **Resultado Esperado**:
   - Código HTTP `201 Created`.
   - El cuerpo de la respuesta contiene `temporaryCredential` (ej. `"Tmp$9wK2#mL1"`) y `status = "PENDING_FIRST_LOGIN"`.
   - La credencial en texto plano **no** se almacena en base de datos; solo su hash BCrypt.
4. **Verificación de Nómina**:
   `GET /api/v1/admin/staff`
   - Código HTTP `200 OK`.
   - La lista incluye a Carlos Garmendia con estado `PENDING_FIRST_LOGIN`.

---

### Escenario 2: Primer Inicio de Sesión y Forzado de Cambio de Contraseña (FR-003)

**Objetivo**: Comprobar que el empleado no puede operar con la credencial temporal hasta completar el cambio de clave obligatorio.

1. **Intento de Acceso con Credencial Temporal**:
   `POST /api/v1/auth/login` con correo `carlos.garmendia@easyeat.com` y la credencial temporal.
   - Código HTTP `200 OK`. Retorna JWT con claim `must_change_password: true`.
2. **Intento de Consulta de Catálogo con Clave Temporal**:
   `GET /api/v1/chef/ingredients` con dicho JWT.
   - Código HTTP `403 Forbidden` con detalle: `"Debe completar el cambio obligatorio de contraseña antes de operar en la plataforma"`.
3. **Cambio Obligatorio de Contraseña**:
   `POST /api/v1/auth/first-login-password-change`
   ```json
   {
     "currentPassword": "Tmp$9wK2#mL1",
     "newPassword": "MiClaveDefinitivaChef2026!",
     "confirmNewPassword": "MiClaveDefinitivaChef2026!"
   }
   ```
   - Código HTTP `200 OK`.
   - El estado del usuario en base de datos cambia a `ACTIVE` y `must_change_password` se fija en `false`.
4. **Re-Login y Validación Operativa**:
   El usuario inicia sesión con la nueva contraseña y el nuevo token le permite consultar el catálogo.

---

### Escenario 3: Enforzamiento de RBAC y Aislamiento Multi-Tenant (ADM-3.1.2)

**Objetivo**: Demostrar que los roles restringen endpoints y que los datos entre locales permanecen 100% aislados.

1. **Acceso Denegado por Rol**:
   - Autenticado como `CHEF`: Enviar `POST /api/v1/admin/ingredients`.
   - Resultado: Código HTTP `403 Forbidden` y generación de evento estructurado en logs de seguridad.
   - Autenticado como `COUNTER_CLERK`: Enviar `GET /api/v1/chef/ingredients`.
   - Resultado: Código HTTP `403 Forbidden`.
2. **Aislamiento Multi-Tenant**:
   - Administrador del Local A (`tenant_id = 11111111-1111-1111-1111-111111111111`) intenta consultar o modificar un ingrediente con ID perteneciente al Local B (`tenant_id = 22222222-2222-2222-2222-222222222222`).
   - Resultado: Código HTTP `404 Not Found` (o `403 Forbidden`), garantizando que la existencia de datos ajenos nunca es expuesta.

---

### Escenario 4: Alta, Validación y Edición de Ingrediente en Catálogo Maestro (ADM-4.1.1)

**Objetivo**: Validar restricciones de campos, categorías permitidas y bloqueo de modificación de unidad de medida.

1. **Creación Válida**:
   `POST /api/v1/admin/ingredients`
   ```json
   {
     "name": "Aceite de Oliva Extra Virgen",
     "category": "Dry Goods",
     "unitOfMeasure": "l",
     "unitPurchaseCost": 4500.0000
   }
   ```
   - Código HTTP `201 Created`, estado inicial `ACTIVE`, `current_stock = 0.0000`.
2. **Rechazo por Nombre Duplicado**:
   Reintentar la creación con el mismo nombre en el mismo tenant.
   - Código HTTP `409 Conflict`.
3. **Rechazo por Categoría o Unidad Inválida**:
   Intentar alta con `category: "Bebidas"` o `unitOfMeasure: "toneladas"`.
   - Código HTTP `400 Bad Request` con errores de validación.

---

### Escenario 5: Carga Inicial de Stock y Ajustes con Invariante No Negativo (ADM-4.1.2)

**Objetivo**: Comprobar los modos de ajuste Absoluto y Relativo, justificación obligatoria y rechazo de stock negativo.

1. **Carga Inicial (Modo Absoluto)**:
   `POST /api/v1/admin/ingredients/{id}/stock-adjustments`
   ```json
   {
     "mode": "ABSOLUTE",
     "value": 20.0000,
     "justification": "Carga inicial de inventario según remito #1040"
   }
   ```
   - Código HTTP `200 OK`. `new_stock = 20.0000`, `net_delta = 20.0000`.
2. **Ajuste Relativo Negativo Válido (Merma)**:
   `POST /api/v1/admin/ingredients/{id}/stock-adjustments`
   ```json
   {
     "mode": "RELATIVE",
     "value": -3.5000,
     "justification": "Botella rota durante acomodamiento en depósito"
   }
   ```
   - Código HTTP `200 OK`. `previous_stock = 20.0000`, `new_stock = 16.5000`, `net_delta = -3.5000`.
3. **Rechazo de Ajuste con Resultado Negativo**:
   Enviar ajuste relativo con `value = -25.0000` (el stock caería a `-8.5000`).
   - Código HTTP `422 Unprocessable Entity` con mensaje: `"El stock resultante no puede ser inferior a cero"`.
   - El stock en base de datos permanece intacto en `16.5000`.
4. **Rechazo de Delta Cero**:
   Enviar ajuste relativo con `value = 0`.
   - Código HTTP `400 Bad Request` (o `422`).
5. **Verificación de Auditoría Inmutable**:
   `GET /api/v1/admin/ingredients/{id}/stock-audit`
   - Código HTTP `200 OK`. Retorna 2 registros inmutables con usuario, delta, stock previo y justificación.

---

### Escenario 6: Validación de Concurrencia con Bloqueo Pesimista (FR-020)

**Objetivo**: Probar la protección contra condiciones de carrera (`SELECT ... FOR UPDATE`).

1. **Configuración de Prueba de Integración Automatizada**:
   - Crear un ingrediente con stock inicial `10.0000`.
   - Lanzar dos hilos concurrentes que intentan aplicar simultáneamente un ajuste relativo de `-8.0000` cada uno.
2. **Resultado Esperado**:
   - Uno de los hilos obtiene el bloqueo de fila y descuenta `8.0000`, dejando el stock en `2.0000`.
   - El segundo hilo se desbloquea e intenta restar `8.0000` sobre `2.0000`; la validación del invariante falla y la transacción hace rollback de forma segura retornando `422 Unprocessable Entity`.
   - El stock final nunca es `-6.0000`; permanece consistentemente en `2.0000` con exactamente 1 registro de auditoría nuevo.

---

### Escenario 7: Ciclo de Vida y Reactivación de Ingrediente (FR-014)

**Objetivo**: Comprobar la transición a `DISCONTINUED`, su reactivación a `ACTIVE` y las condiciones de archivo.

1. **Descontinuar con Stock**:
   Ingrediente con stock `16.5000`.
   `PATCH /api/v1/admin/ingredients/{id}/status` con `targetStatus = "DISCONTINUED"`.
   - Código HTTP `200 OK`. El ingrediente pasa a `DISCONTINUED`.
2. **Verificación en Catálogo del Chef**:
   `GET /api/v1/chef/ingredients`.
   - El ingrediente descontinuado ya **no** figura en el listado de cocina.
3. **Ajuste de Stock en Descontinuado**:
   `POST /api/v1/admin/ingredients/{id}/stock-adjustments` con ajuste absoluto `value = 0` y justificación `"Venta de stock remanente"`.
   - Código HTTP `200 OK`. El stock queda en `0.0000`.
4. **Reactivación o Archivo**:
   - Si el Administrador envía `targetStatus = "ACTIVE"`, el insumo vuelve a estar disponible para el Chef.
   - Si envía `targetStatus = "ARCHIVED"` (estando el stock en `0.0000`), el insumo se archiva exitosamente.
   - Si se intentara archivar cuando el stock fuera mayor a 0, la API responde `409 Conflict`.

---

### Escenario 8: Exploración Paginada del Catálogo de Cocina (CHF-2.1.1)

**Objetivo**: Validar la vista segura y paginada del Chef sin filtración de datos de costos.

1. **Petición del Chef**:
   `GET /api/v1/chef/ingredients?page=0&size=20`
2. **Resultado Esperado**:
   - Código HTTP `200 OK`.
   - Lista ordenada alfabéticamente de la A a la Z.
   - Cada elemento incluye `name`, `category`, `unitOfMeasure` y `stockAvailability` (`IN_STOCK` o `OUT_OF_STOCK`).
   - Los campos `unitPurchaseCost` y `stockAuditHistory` **no** existen en el payload JSON.
3. **Filtro y Búsqueda**:
   - `GET /api/v1/chef/ingredients?category=Produce&search=tomate`
   - Retorna únicamente los ingredientes de huerta que coincidan con el término.
