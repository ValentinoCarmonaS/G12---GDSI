# Contrato 07: Configuración Administrativa de Branding ("Mi Local")

**Ruta de Frontend**: `/admin/mi-local` (o `/admin/branding`)  
**Endpoints Backend**:
- `GET /api/v1/admin/establishments/{establishmentId}/branding`
- `PUT /api/v1/admin/establishments/{establishmentId}/branding`
- `POST /api/v1/admin/establishments/{establishmentId}/branding/logo`  
**Consumidor**: Administrador del restaurante  
**Seguridad**: Autenticación JWT Bearer (`ROLE_ADMINISTRADOR`) con aislamiento de tenant (`current_user.tenant_id == establishmentId`)  
**Trazabilidad**: Cubre **AS-9**, **ADM-2.1.1**, **FR-009**, **INV-009**, **Q5/A5**, **Q7/A7**, **CB-009**, **SC-009**.

---

## 1. Descripción

Este contrato gobierna la consulta y actualización de los atributos de identidad visual del restaurante desde el módulo "Mi Local".
- **Tríada Cromática Explícita (Q7/A7)**: Exige los tres colores primarios de marca (`primaryColor`, `secondaryColor`, `accentColor`) en formato hexadecimal `#RRGGBB`.
- **Cálculo Automático de Contraste (Q5/A5)**: El backend valida el contraste accesible WCAG 2.1 y persiste los colores de texto derivados (`primaryContrastColor`, `secondaryContrastColor`, `accentContrastColor`) como `#000000` o `#FFFFFF`.
- **Aislamiento Multi-Tenant (INV-009)**: Un administrador solo puede leer y modificar el branding de su propio establecimiento.

---

## 2. Endpoint de Consulta de Branding (`GET`)

```http
GET /api/v1/admin/establishments/{establishmentId}/branding
Authorization: Bearer <jwt-token-admin>
Accept: application/json
```

### Respuesta Exitosa (`HTTP 200 OK`)
```json
{
  "establishmentId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
  "fantasyName": "Café Central Palermo",
  "description": "Especialistas en café de autor, brunch artesanal y pastelería",
  "logoUrl": "/uploads/logos/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11/logo.png",
  "primaryColor": "#059669",
  "secondaryColor": "#064E3B",
  "accentColor": "#D97706",
  "primaryContrastColor": "#FFFFFF",
  "secondaryContrastColor": "#FFFFFF",
  "accentContrastColor": "#000000",
  "updatedAt": "2026-11-09T14:10:00Z"
}
```

---

## 3. Endpoint de Guardado de Branding (`PUT`)

```http
PUT /api/v1/admin/establishments/{establishmentId}/branding
Authorization: Bearer <jwt-token-admin>
Content-Type: application/json

{
  "fantasyName": "Café Central Palermo",
  "description": "Especialistas en café de autor, brunch artesanal y pastelería de especialidad",
  "primaryColor": "#2563EB",
  "secondaryColor": "#1E3A8A",
  "accentColor": "#F59E0B"
}
```

### Reglas de Validación
- `fantasyName`: Obligatorio, longitud 2-150 caracteres.
- `description`: Opcional, longitud máxima 1000 caracteres.
- `primaryColor`, `secondaryColor`, `accentColor`: Obligatorios, regex `^#[0-9A-Fa-f]{6}$`.

### Respuesta Exitosa (`HTTP 200 OK`)
```json
{
  "establishmentId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
  "fantasyName": "Café Central Palermo",
  "description": "Especialistas en café de autor, brunch artesanal y pastelería de especialidad",
  "logoUrl": "/uploads/logos/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11/logo.png",
  "primaryColor": "#2563EB",
  "secondaryColor": "#1E3A8A",
  "accentColor": "#F59E0B",
  "primaryContrastColor": "#FFFFFF",
  "secondaryContrastColor": "#FFFFFF",
  "accentContrastColor": "#000000",
  "updatedAt": "2026-11-09T15:10:00Z"
}
```

---

## 4. Endpoint de Subida de Logotipo (`POST multipart/form-data`)

```http
POST /api/v1/admin/establishments/{establishmentId}/branding/logo
Authorization: Bearer <jwt-token-admin>
Content-Type: multipart/form-data

--boundary
Content-Disposition: form-data; name="file"; filename="logo-central.png"
Content-Type: image/png

<bytes del archivo>
--boundary--
```

### Reglas de Validación
- Formatos permitidos: `image/png`, `image/jpeg`, `image/webp`, `image/svg+xml`.
- Tamaño máximo de archivo: 2 MB.
- Si el archivo es válido, se almacena de forma segura y se actualiza `logo_url` en `restaurant_brandings`.

### Respuesta Exitosa (`HTTP 200 OK`)
```json
{
  "logoUrl": "/uploads/logos/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11/logo.png",
  "updatedAt": "2026-11-09T15:10:30Z"
}
```

---

## 5. Errores de Validación (RFC 7807)

### Formato Hexadecimal Inválido (`HTTP 422 Unprocessable Entity`)
```json
{
  "type": "https://easyeat.com/errors/invalid-branding-color",
  "title": "Color Hexadecimal Inválido",
  "status": 422,
  "detail": "El campo 'primaryColor' debe cumplir con el formato hexadecimal '#RRGGBB' (ej: '#10B981')."
}
```

