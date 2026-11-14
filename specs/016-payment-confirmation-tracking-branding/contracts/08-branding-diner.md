# Contrato 08: Aplicación de Branding en la Vista del Comensal y Fallback Institucional

**Ruta de Frontend**: `/catalogo/*`, `/checkout`, `/checkout/confirmacion/:orderId`  
**Endpoint Backend**: `GET /api/v1/establishments/{establishmentId}/branding`  
**Consumidor**: Comensal o invitado navegando el menú y realizando compras  
**Seguridad**: Acceso público (o autenticado con rol `ROLE_COMENSAL`)  
**Trazabilidad**: Cubre **AS-9**, **FR-009**, **Q3/A3**, **Q7/A7**, **INV-009**, **INV-010**, **CB-010**, **CB-011**, **SC-009**.

---

## 1. Descripción

Este contrato gobierna la entrega de la identidad visual del restaurante al comensal en el momento en que este ingresa a pedir en el establecimiento.
- **Delimitación de la Tríada Cromática (Q7/A7)**:
  1. `primaryColor`: Botones de acción clave (CTA, "Agregar al carrito", "Pagar", "Seguir pedido") con texto en `primaryContrastColor`.
  2. `secondaryColor`: Cabecera del restaurante, barra superior y fondos destacados de categoría con texto en `secondaryContrastColor`.
  3. `accentColor`: Insignias (badges de celíaco, vegano), píldoras de franja horaria y bordes de selección activa con texto en `accentContrastColor`.
- **Fallback Institucional Neutro (INV-010, Q3/A3, CB-011)**: Si el restaurante no ha configurado marca personalizada, se inyecta la identidad neutra de EasyEat sin degradar la experiencia visual ni arrojar errores de renderizado.

---

## 2. Endpoint de Consulta de Branding para el Comensal

```http
GET /api/v1/establishments/{establishmentId}/branding
Accept: application/json
```

---

## 3. Respuestas

### 3.1. Establecimiento con Branding Personalizado Guardado (`HTTP 200 OK`)

```json
{
  "establishmentId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
  "fantasyName": "Café Central Palermo",
  "description": "Especialistas en café de autor, brunch artesanal y pastelería",
  "logoUrl": "/uploads/logos/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11/logo.png",
  "primaryColor": "#2563EB",
  "secondaryColor": "#1E3A8A",
  "accentColor": "#F59E0B",
  "primaryContrastColor": "#FFFFFF",
  "secondaryContrastColor": "#FFFFFF",
  "accentContrastColor": "#000000",
  "isDefaultFallback": false
}
```

---

### 3.2. Establecimiento sin Configuración Previa de Branding (`HTTP 200 OK`)

Aplica el fallback institucional de EasyEat de manera transparente (**CB-011**, **INV-010**):

```json
{
  "establishmentId": "c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a33",
  "fantasyName": "Bistró San Telmo",
  "description": "Restaurante asociado a EasyEat",
  "logoUrl": "/assets/brand/easyeat-logo-neutral.svg",
  "primaryColor": "#10B981",
  "secondaryColor": "#065F46",
  "accentColor": "#F59E0B",
  "primaryContrastColor": "#FFFFFF",
  "secondaryContrastColor": "#FFFFFF",
  "accentContrastColor": "#000000",
  "isDefaultFallback": true
}
```

---

## 4. Mapeo de Variables CSS en el Cliente Frontend

El comensal adopta dinámicamente estos estilos inyectando tokens de diseño en el contenedor raíz o en un contexto temático `BrandThemeProvider`:

```css
:root {
  --brand-primary: #2563EB;
  --brand-primary-contrast: #FFFFFF;
  --brand-secondary: #1E3A8A;
  --brand-secondary-contrast: #FFFFFF;
  --brand-accent: #F59E0B;
  --brand-accent-contrast: #000000;
}
```

### Aplicación en Componentes React
- **Botón CTA de Carrito / Pagar**:
  ```tsx
  <button
    style={{
      backgroundColor: branding.primaryColor,
      color: branding.primaryContrastColor
    }}
    className="px-6 py-3 rounded-xl font-bold shadow-md hover:brightness-105 transition-all"
  >
    Confirmar y Pagar
  </button>
  ```
- **Cabecera del Restaurante (`CatalogPage.tsx`)**:
  ```tsx
  <header
    style={{
      backgroundColor: branding.secondaryColor,
      color: branding.secondaryContrastColor
    }}
    className="py-8 px-6 shadow-sm"
  >
    <div className="flex items-center gap-4">
      <img src={branding.logoUrl} alt={branding.fantasyName} className="h-16 w-16 rounded-full object-cover border-2 border-white/20" />
      <div>
        <h1 className="text-2xl font-black">{branding.fantasyName}</h1>
        <p className="text-sm opacity-90 mt-1">{branding.description}</p>
      </div>
    </div>
  </header>
  ```
- **Insignias y Badges**:
  ```tsx
  <span
    style={{
      backgroundColor: branding.accentColor,
      color: branding.accentContrastColor
    }}
    className="px-2.5 py-0.5 rounded-full text-xs font-semibold"
  >
    Sin TACC
  </span>
  ```

