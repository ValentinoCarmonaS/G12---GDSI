# Quickstart: Guía de Validación End-to-End para Umbral de Stock Crítico y Pausa Manual de Platos

**Feature**: `013-critical-stock-chef-pause`  
**Fecha**: 01/11/2026  
**Documentos de referencia**: [spec.md](./spec.md) · [data-model.md](./data-model.md) · [contracts/behavioral-contracts.md](./contracts/behavioral-contracts.md) · [contracts/openapi.yaml](./contracts/openapi.yaml)

---

## 1. Prerrequisitos del Entorno

1. **Servicios de Infraestructura**:
   - PostgreSQL 16 activo en localhost o vía Docker:
     ```bash
     docker run -d --name easyeat-postgres -p 5432:5432 -e POSTGRES_DB=easyeat -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres postgres:16-alpine
     ```
2. **Backend**:
   - JDK 21 LTS instalado y configurado (`java -version`).
   - Maven wrapper en `easyeat/backend`.
3. **Frontend**:
   - Node.js 20+ y npm (`node -v`).
   - Dependencias instaladas en `easyeat/frontend` (`npm install`).

---

## 2. Configuración y Ejecución de Servicios

### 2.1 Backend (Spring Boot)
Desde `easyeat/backend`:
```bash
# Ejecutar migraciones Flyway y levantar servidor en puerto 8080
./mvnw clean spring-boot:run
```

### 2.2 Frontend (React SPA)
Desde `easyeat/frontend`:
```bash
# Iniciar cliente de desarrollo en puerto 5173
npm run dev
```

---

## 3. Escenarios de Validación Ejecutables End-to-End

### Escenario 1: Definición de Umbral de Stock Crítico Individual y Validación (ADM-4.1.3, US1)

- **Objetivo**: Probar que el Administrador puede definir y editar un umbral individual entero $\ge 0$, y que valores negativos o no numéricos son rechazados.
- **Paso 1 (Autenticación Admin)**: Iniciar sesión con credenciales de Administrador en `http://localhost:5173/login`.
- **Paso 2 (Navegación a Inventario)**: Ir a `http://localhost:5173/admin/inventory`.
- **Paso 3 (Creación con umbral válido)**:
  - Clic en "+ Nuevo Ingrediente".
  - Ingresar: Nombre `"Carne Picada Especial"`, Categoría `Meat`, Unidad `kg`, Costo `1500.00`, Stock Mínimo/Crítico `10`.
  - Confirmar guardado.
  - **Resultado esperado**: El ingrediente se crea con éxito. El detalle muestra `criticalStockThreshold: 10`, `currentStock: 0.0000`, y dado que $0 < 10$, se marca en estado crítico.
- **Paso 4 (Validación de rechazo)**:
  - Abrir edición del insumo, ingresar `-5` o `3.5` en "Stock Mínimo/Crítico".
  - Intentar guardar.
  - **Resultado esperado**: Bloqueo con mensaje `"El umbral de stock crítico debe ser un número entero mayor o igual a cero"`.
- **Paso 5 (Verificación RBAC)**:
  - Cerrar sesión e iniciar como Chef (`ROLE_CHEF`). Intentar ejecutar `PUT /api/v1/admin/ingredients/{id}`.
  - **Resultado esperado**: HTTP 403 Forbidden.

---

### Escenario 2: Alertas Visuales en Inventario y Filtro Rápido (ADM-4.1.4, US2)

- **Objetivo**: Verificar que los ingredientes con `currentStock < criticalStockThreshold` se resaltan visualmente, que el filtro rápido los aísla, y que la reposición remueve la alerta.
- **Paso 1 (Visualización de Alerta)**:
  - Como Administrador en `/admin/inventory`, verificar que `"Carne Picada Especial"` ($0\text{ kg} < 10\text{ kg}$) exhibe la insignia visible `"⚠️ Stock crítico"`.
  - Cero notificaciones externas (sin emails a proveedores ni SMS).
- **Paso 2 (Filtro Rápido)**:
  - Activar el toggle de cabecera `"Solo insumos en stock crítico"`.
  - **Resultado esperado**: La tabla filtra y exhibe exclusivamente insumos por debajo de su umbral.
  - Desactivar el filtro rápido: vuelve a mostrarse el catálogo completo con los críticos destacados.
- **Paso 3 (Reabastecimiento y Remoción de Alerta)**:
  - Registrar ajuste de stock físico ingresando $+15\text{ kg}$ sobre `"Carne Picada Especial"`.
  - Stock actual pasa a $15\text{ kg}$ ($15 \ge 10$).
  - **Resultado esperado**: La insignia de stock crítico desaparece de inmediato.
- **Paso 4 (Suspensión en Descontinuado)**:
  - Cambiar estado del insumo a `DISCONTINUED`.
  - **Resultado esperado**: La evaluación de criticidad se suspende y no emite alerta (INV-17).

---

### Escenario 3: Pausa Manual de Platos por Chef y Ocultamiento en Catálogo (CHF-2.2.5, US3)

- **Objetivo**: Probar que el Chef puede pausar un plato publicado, ocultándolo inmediatamente del comensal sin alterar stocks ni recetas.
- **Paso 1 (Autenticación Chef)**: Iniciar sesión como Chef en `/login` y navegar a `/cocina/platos`.
- **Paso 2 (Accionar Toggle de Pausa)**:
  - Ubicar un plato publicado (ej. `"Hamburguesa Clásica"`).
  - Accionar el control de alternancia (toggle) a estado `"Pausado"`.
  - **Resultado esperado**: El toggle se actualiza. No se modifican stocks en inventario ni precios.
- **Paso 3 (Comprobación en Catálogo del Comensal)**:
  - En otra ventana o pestaña, navegar como Comensal a `/catalogo`.
  - En un lapso $\le 3\text{ s}$ (polling condicional ETag), el plato `"Hamburguesa Clásica"` desaparece de la vista del comensal (INV-8).
- **Paso 4 (Reversibilidad)**:
  - En la vista del Chef, accionar el toggle a `"Activo"`.
  - En $\le 3\text{ s}$, el plato reaparece visible en el catálogo del comensal.

---

### Escenario 4: Sincronización Colaborativa entre Múltiples Chefs (INV-18, FR-022, EC-025)

- **Objetivo**: Verificar que dos chefs del mismo restaurante ven sincronizados los toggles en $\le 3$ s.
- **Paso 1**: Abrir dos navegadores independientes (Navegador A y Navegador B), ambos autenticados como Chefs del mismo local en `/cocina/platos`.
- **Paso 2**: En Navegador A, el Chef 1 conmuta la pausa de `"Pizza Margarita"` a `Pausado`.
- **Paso 3**: Observar el Navegador B sin tocar el teclado ni refrescar la página.
- **Resultado esperado**: En $\le 3\text{ s}$, el toggle en el Navegador B conmuta automáticamente a `Pausado`.

---

### Escenario 5: Coexistencia de las Tres Causas de Indisponibilidad (INV-10, EC-011, EC-014)

- **Objetivo**: Demostrar que despausar un plato sin stock de insumos lo mantiene no comprable ("Agotado").
- **Paso 1**: Consumir o ajustar a $0$ el stock de un ingrediente clave de `"Hamburguesa Clásica"`. El plato queda en estado `"sin_stock"`.
- **Paso 2**: El Chef activa la pausa manual (`"pausa_chef = true"`). El plato queda oculto para el comensal.
- **Paso 3**: El Chef desactiva la pausa manual (`"pausa_chef = false"`).
- **Resultado esperado**:
  - En el panel del Chef, se muestra despausado pero con distintivo `"Sin Stock de Cocina"`.
  - En el catálogo del comensal, el plato reaparece pero atenuado con distintivo `"Agotado"` y botón de compra deshabilitado. No se permite agregar al carrito.

---

### Escenario 6: Carrito del Comensal ante Pausa en Servicio (FR-019, EC-023)

- **Objetivo**: Probar la preservación con bloqueo en el carrito.
- **Paso 1**: Como comensal, agregar 2 unidades de `"Hamburguesa Clásica"` al carrito.
- **Paso 2**: Como Chef, accionar la pausa manual del plato.
- **Paso 3**: Abrir el cajón del carrito (`CartDrawer`):
  - **Resultado esperado**: El plato sigue en el carrito; exhibe la advertencia `"Pausado por cocina"`; los controles `+` están deshabilitados; y el botón de checkout está bloqueado.
- **Paso 4**: El comensal elimina el ítem o el chef despausa el plato $\to$ el checkout se desbloquea.

---

### Escenario 7: Modal de Detalle Abierto ante Pausa en Vivo (EC-017, FR-016)

- **Objetivo**: Verificar que el modal no se cierra forzadamente y deshabilita acciones.
- **Paso 1**: Como comensal, hacer clic en `"Hamburguesa Clásica"` para abrir `DishDetailModal`.
- **Paso 2**: En otra ventana, el chef pausa el plato.
- **Paso 3**: En $\le 3\text{ s}$, el modal actualiza su estado: muestra `"Pausado por cocina"`, deshabilita el selector de cantidad y el botón `"Agregar al Carrito"`, **sin cerrar el modal**.
- **Paso 4**: Al presionar la 'X' o hacer clic fuera del modal, el plato ya no figura en la grilla del catálogo.

---

## 4. Comandos de Verificación Automatizada

### 4.1 Pruebas Backend (JUnit 5 + Mockito + Testcontainers)
```bash
cd easyeat/backend
# Ejecutar tests unitarios de criticidad, tres causas de indisponibilidad y RBAC
./mvnw test -Dtest=IngredientCriticalStockTest,DishChefPauseTest,DishAvailabilityEngineTest,InventorySecurityTest
```

### 4.2 Pruebas Frontend (Vitest + React Testing Library)
```bash
cd easyeat/frontend
# Ejecutar suite de pruebas de componentes y hooks de catálogo e inventario
npm test -- src/test/inventory/IngredientCatalogTable.test.tsx src/test/chef/ChefDishesPage.test.tsx src/test/catalog/DishDetailModal.test.tsx
```

