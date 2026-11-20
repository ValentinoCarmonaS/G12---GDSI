# Contrato de API: suspension y control de acceso de personal (`staff-access-status`)

**Especificacion**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Modelo de datos**: [data-model.md](../data-model.md)  
**Endpoint**: `PATCH /api/v1/admin/staff/{id}/status`  
**Seguridad**: `@PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('ADMIN')")`  

---

## 1. Descripcion operativa

Endpoint administrativo para mutar el estado operativo de la cuenta de un empleado del establecimiento (suspension laboral o reactivacion).

Aplica la regla de seguridad de orfandad administrativa (FR-005, SC-002): el sistema bloquea y rechaza atómicamente la solicitud si el usuario objetivo es el unico `ADMINISTRADOR` activo del establecimiento.

Asimismo, formaliza el comportamiento del endpoint publico de login (`POST /api/v1/auth/login`) ante credenciales correspondientes a una cuenta suspendida (FR-002, FR-003, SC-001).

---

## 2. Mutacion de estado de personal (`PATCH /api/v1/admin/staff/{id}/status`)

### 2.1. Peticion HTTP

- **Metodo**: `PATCH`
- **Ruta**: `/api/v1/admin/staff/{id}/status`
- **Parametros de ruta**:
  - `id` (UUID, obligatorio): Identificador del empleado en la nómina.
- **Cabeceras obligatorias**:
  - `Authorization: Bearer <JWT_ADMIN>`
  - `Content-Type: application/json`
- **Cuerpo de la peticion**:

```json
{
  "status": "SUSPENDED"
}
```

*Valores permitidos para `status`*: `"SUSPENDED"`, `"ACTIVE"`.

### 2.2. Respuestas HTTP

#### 2.2.1. Exito: `200 OK`

Retornado cuando el estado de la cuenta fue modificado exitosamente.

```json
{
  "id": "e4b3c2a1-5d6e-4f8a-9b1c-2d3e4f5a6b7c",
  "fullName": "Carlos Cocinero",
  "email": "carlos.cocinero@easyeat.com",
  "role": "CHEF",
  "status": "SUSPENDED",
  "createdAt": "2026-10-15T12:00:00Z",
  "updatedAt": "2026-11-20T16:30:00Z"
}
```

#### 2.2.2. Conflicto de orfandad administrativa: `409 Conflict`

Retornado cuando la cuenta objetivo es un `ADMINISTRADOR` activo y su suspension dejaria al establecimiento con cero administradores activos.

```json
{
  "type": "https://easyeat.com/errors/orphaned-admin",
  "title": "Orphaned Administrator Constraint Violated",
  "status": 409,
  "detail": "Operation rejected: cannot remove or reassign the sole active Administrator for establishment 7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "instance": "/api/v1/admin/staff/e4b3c2a1-5d6e-4f8a-9b1c-2d3e4f5a6b7c/status",
  "tenantId": "7c9e6679-7425-40de-944b-e07fc1f90ae7"
}
```

#### 2.2.3. Empleado no encontrado: `404 Not Found`

Retornado si el `id` no existe o pertenece a otro establecimiento.

```json
{
  "type": "https://easyeat.com/errors/not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "Staff member not found with ID: e4b3c2a1-5d6e-4f8a-9b1c-2d3e4f5a6b7c",
  "instance": "/api/v1/admin/staff/e4b3c2a1-5d6e-4f8a-9b1c-2d3e4f5a6b7c/status"
}
```

#### 2.2.4. Solicitud invalida: `422 Unprocessable Entity` o `400 Bad Request`

Retornado si el valor de `status` es nulo, vacio o no corresponde a `ACTIVE` o `SUSPENDED`.

---

## 3. Comportamiento en autenticacion de cuenta suspendida (`POST /api/v1/auth/login`)

### 3.1. Peticion HTTP

- **Metodo**: `POST`
- **Ruta**: `/api/v1/auth/login`
- **Cuerpo de la peticion**:

```json
{
  "email": "carlos.cocinero@easyeat.com",
  "password": "Password123!"
}
```

### 3.2. Respuesta obligatoria ante cuenta suspendida: `401 Unauthorized`

Conforme a FR-002, FR-003 y SC-001, la respuesta debe ser **100% indistinguible** de un intento con contrasena incorrecta o usuario inexistente, sin filtrar si la cuenta esta suspendida ni confirmar si la contrasena provista era valida.

```json
{
  "type": "https://easyeat.com/errors/invalid-credentials",
  "title": "Invalid Credentials",
  "status": 401,
  "detail": "The provided email or password is incorrect.",
  "instance": "/api/v1/auth/login"
}
```

*Invariante de seguridad*: El servidor registra el evento `ACCOUNT_SUSPENDED` unicamente en la auditoria interna (`SecurityAuditLogger`), pero la salida HTTP externa nunca emite error 403 ni menciona el estado de la cuenta.