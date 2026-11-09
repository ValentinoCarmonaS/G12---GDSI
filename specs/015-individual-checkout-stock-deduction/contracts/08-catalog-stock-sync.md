# Contract 08: Sincronización Inmediata del Catálogo por Agotamiento de Insumos (FR-013)

**Tipo de Contrato**: Evaluación Dinámica en Lectura de Catálogo y Respuesta REST  
**Componentes Responsables**: `com.easyeat.catalog.service.DishAvailabilityEngine` y `DinerCatalogController`  
**Consumidor**: Comensales navegando el menú gastronómico (`GET /api/v1/catalog`)  
**Seguridad**: Acceso público o autenticado  

---

## 1. Descripción

Este contrato gobierna la sincronización visual inmediata del catálogo de platos cuando un débito transaccional agota las existencias de materias primas de una receta técnica (FR-013, INV-10).  
Reglas operativas obligatorias:
1. **Evaluación Continua**: La disponibilidad no depende de cron jobs ni de réplicas asíncronas con retraso.
2. **Efecto Inmediato**: Ni bien se debita el stock real y el remanente de un insumo resulta menor a la cantidad exigida por la receta de un plato, la siguiente consulta de catálogo expone dicho plato como no disponible (`available = false`, `portionsAvailable = 0`).
3. **Platos con Múltiples Recetas Compartidas (C3)**: El agotamiento de un insumo inhabilita instantáneamente a **todos** los platos cuyas recetas dependan de ese mismo insumo.

---

## 2. Endpoint de Catálogo para Comensales

```http
GET /api/v1/catalog?establishmentId=3fa85f64-5717-4562-b3fc-2c963f66afa6
```

### Respuesta Exitosa (`HTTP 200 OK`)

```json
{
  "establishmentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "establishmentName": "Restaurante Central",
  "dishes": [
    {
      "dishId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "name": "Hamburguesa Clásica",
      "category": "HAMBURGUESAS",
      "price": 4500.00,
      "available": false,
      "unavailabilityReason": "OUT_OF_STOCK",
      "portionsAvailable": 0,
      "pausedByChef": false
    },
    {
      "dishId": "f7e8d9c0-b1a2-3f4e-5d6c-7b8a9f0e1d2c",
      "name": "Ensalada César",
      "category": "ENSALADAS",
      "price": 3800.00,
      "available": true,
      "unavailabilityReason": null,
      "portionsAvailable": 14,
      "pausedByChef": false
    }
  ]
}
```

### Campos de Disponibilidad

| Campo | Tipo | Descripción |
|---|---|---|
| `available` | Boolean | `false` si el plato está agotado por inventario o pausado por el chef. |
| `unavailabilityReason` | String | `OUT_OF_STOCK` (insumo agotado por receta), `PAUSED_BY_CHEF` (Spec 013), o `null` si está disponible. |
| `portionsAvailable` | Integer | Cantidad máxima entera de porciones elaborables según el insumo más restrictivo de su receta (INV-3 de Spec 013). Si está agotado, vale `0`. |

---

## 3. Comportamiento en la Interfaz de Comensal

1. En el catálogo (`DinerCatalogPage`), los platos con `available = false` y `unavailabilityReason = 'OUT_OF_STOCK'` se renderizan con estilos atenuados y una etiqueta visible: **"Agotado"**.
2. El selector de cantidades y el botón "Agregar al carrito" quedan deshabilitados para ese plato.
3. Si un comensal ya tenía el plato en su carrito local antes de que se agotara, al intentar abonar en el checkout el sistema rechaza la transacción de forma segura conforme al **Contrato 04 (AS-2)**.

