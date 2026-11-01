# Guía de Validación Rápida (Quickstart): Detalle de Plato, Información Nutricional, Precios y Filtros Manuales

**Feature Branch**: `011-dish-detail-manual-filters`  
**Fecha**: 01/11/2026  
**Referencia de Especificación**: [spec.md](./spec.md) · [plan.md](./plan.md) · [data-model.md](./data-model.md) · [contracts/behavioral-contracts.md](./contracts/behavioral-contracts.md)

---

## 1. Propósito y Alcance

Esta guía provee escenarios ejecutables de validación end-to-end para certificar de forma verificable que las cuatro historias de usuario de la feature 011 cumplen todos sus criterios de aceptación, invariantes de negocio y casos borde.

---

## 2. Prerrequisitos del Entorno

1. **Backend**:
   - Java 21 JDK instalado (`java -version`).
   - Maven 3.9+ instalado (`mvn -version`).
   - Contenedor PostgreSQL 16 activo o base de datos de pruebas configurada (vía Testcontainers o PostgreSQL local en puerto 5432).
2. **Frontend**:
   - Node.js 20+ y npm (`node -v`, `npm -v`).
3. **Credenciales de Prueba**:
   - Usuario comensal activo: `comensal@easyeat.com` / `Password123!`.
   - Establecimiento activo con platos publicados en diferentes categorías y con insumos en cocina.

---

## 3. Comandos de Inicialización y Ejecución de Pruebas

### 3.1 Pruebas Unitarias y de Integración Backend
Desde la raíz del backend (`easyeat/backend`):
```bash
# Ejecutar suite de pruebas de catálogo, disponibilidad y DTOs
mvn clean test -Dtest="DinerCatalogServiceTest,DishAvailabilityEngineTest,DinerCatalogControllerTest"
```

### 3.2 Pruebas de Componentes y Hooks Frontend
Desde la raíz del frontend (`easyeat/frontend`):
```bash
# Ejecutar suite de pruebas de componentes y filtros manuales
npm run test -- src/test/catalog/DishDetailModal.test.tsx src/test/catalog/DishCard.test.tsx src/test/catalog/CatalogSearchFilter.test.tsx src/test/catalog/ManualFiltersSidebar.test.tsx
```

---

## 4. Escenarios de Validación End-to-End

### Escenario 1: Apertura de Panel, Preservación de Scroll y Ausencia de Fotografía (US1, COM-5.1.1)

- **Objetivo**: Verificar que al abrir el detalle de un plato se muestre la información completa, que un plato sin foto no presente espacios en blanco rotos, y que al cerrar el panel se retorne a la coordenada exacta de scroll (AC-1.1, AC-1.2, AC-1.3, INV-1, INV-2, SC-001, SC-002, SC-003, EC-001, EC-021).
- **Pasos**:
  1. Iniciar sesión como comensal y navegar al catálogo (`/catalogo`).
  2. Desplazar la página verticalmente 1.200 píxeles hacia abajo (`window.scrollY = 1200`).
  3. Hacer clic sobre la tarjeta de un plato sin fotografía asignada.
  4. Confirmar que el panel superpuesto se despliega sobre la vista sin recargar la página, mostrando nombre comercial y descripción ocupando la cabecera directamente, sin recuadros grises vacíos ni íconos rotos (`[data-testid="modal-placeholder"]` no renderizado).
  5. Presionar la tecla Escape o hacer clic en el área exterior del fondo (`data-testid="dish-detail-modal"`).
- **Resultado Esperado**:
  - El modal se cierra inmediatamente.
  - La posición de desplazamiento vertical del navegador se mantiene exactamente en 1.200 píxeles, sin saltos visuales ni reseteo al inicio del documento.

---

### Escenario 2: Degradación Elegante en Red Lenta y Textos Largos (US1, COM-5.1.1)

- **Objetivo**: Validar la carga progresiva de imagen y el desplazamiento interno ante descripciones extensas (AC-1.4, AC-1.5, INV-3, INV-4, SC-004, SC-005, EC-002, EC-003).
- **Pasos**:
  1. Activar estrangulamiento de red en las herramientas de desarrollo del navegador (*Slow 3G*).
  2. Hacer clic sobre un plato con fotografía en alta resolución y descripción comercial extensa (> 300 caracteres).
- **Resultado Esperado**:
  - El nombre, precio, descripción e ingredientes se visualizan de inmediato.
  - El contenedor de la imagen presenta un indicador visual animado de carga (skeleton) hasta que la imagen completa su transferencia y sustituye suavemente el indicador.
  - El contenedor del panel no desborda la pantalla; se activan barras de desplazamiento vertical interno (`overflow-y-auto`) preservando íntegra la estructura visual y los botones de acción.

---

### Escenario 3: Invalidación Reactiva de Stock en Panel Abierto $\le 3$ s (US1, COM-5.1.1, C-5)

- **Objetivo**: Verificar que el panel abierto desactive los controles de compra en $\le 3$ s ante agotamiento de stock o baja administrativa sin cerrarse intempestivamente (AC-1.6, INV-5, FR-007, SC-010, EC-004, Clarification #5).
- **Pasos**:
  1. Abrir el modal de detalle de un plato con stock disponible.
  2. Desde una sesión paralela (o llamada API), registrar una orden concurrente que agote el stock del insumo principal o despublicar el plato desde administración.
  3. Observar el panel abierto sin interactuar ni recargar la página durante 3 segundos.
- **Resultado Esperado**:
  - En un lapso no superior a 3 segundos (intervalo de polling de 2.500 ms), el panel actualiza su vista a "Plato no disponible" / badge "Agotado".
  - Los controles de cantidad y el botón "Agregar al Carrito" se deshabilitan automáticamente.
  - El panel **no** se cierra intempestivamente.
  - Al cerrar voluntariamente el modal, la grilla del catálogo subyacente refleja el estado actualizado del plato.

---

### Escenario 4: Precio Final, Formato Regional y Subtotal Provisorio Libre (US2, COM-5.1.3, C-2)

- **Objetivo**: Comprobar el formato regional del precio ($P_{\text{final}}$), el cálculo dinámico del subtotal sin tope artificial y la validación de inventario al agregar al carrito (AC-3.1, AC-3.2, AC-3.5, INV-9, INV-10, INV-13, FR-011, FR-012, FR-015, SC-007, SC-011, EC-011, Clarification #2).
- **Pasos**:
  1. Abrir el detalle de un plato con precio comercial fijado en `$ 4.500,00` y stock en cocina igual a 5 unidades.
  2. Verificar que el precio exhibido junto al botón de acción aplique el formato regional argentino: `$ 4.500,00` (símbolo `$ `, punto de miles y dos decimales con coma).
  3. Utilizar el selector de cantidad y seleccionar $N = 3$.
  4. Constatar que el subtotal provisorio se actualiza instantáneamente en pantalla a `$ 13.500,00`.
  5. Incrementar el selector a $N = 10$ (permitido libremente para estimación, sin tope artificial en el selector). Constatar que el subtotal exhibe `$ 45.000,00`.
  6. Presionar el botón "Agregar al Carrito".
- **Resultado Esperado**:
  - El sistema intercepta la acción, valida que $10 > 5$ (stock de cocina), bloquea la incorporación y despliega una advertencia visible de disponibilidad insuficiente.
  - Al reducir la cantidad a $N = 3$ y pulsar "Agregar al Carrito", las 3 unidades se incorporan con éxito al carrito de compras, actualizando el subtotal general en el botón flotante.

---

### Escenario 5: Ingredientes, Alérgenos Neutros y Platos sin Componentes (US3, COM-5.1.2, C-4)

- **Objetivo**: Verificar la presentación objetiva de ingredientes y alérgenos neutros idénticos para todos los comensales, y la leyenda explícita ante platos sin receta (AC-2.1, AC-2.3, INV-6, INV-8, FR-008, FR-010, SC-006, EC-007, Clarification #4).
- **Pasos**:
  1. Abrir el detalle de un plato con alérgenos registrados (ej. Gluten y Lactosa) con un usuario comensal que tenga restricción de celiaquía en su perfil.
  2. Inspeccionar las etiquetas de alérgenos en el modal.
  3. Cerrar el modal y abrir un plato registrado sin ingredientes ni receta en el sistema.
- **Resultado Esperado**:
  - En el primer plato, las etiquetas de Gluten y Lactosa se muestran de forma estándar, uniforme y neutra, sin bordes rojos ni alertas preventivas personalizadas en esta vista informativa.
  - En el segundo plato, la sección presenta la leyenda explícita: `"Lista de componentes no disponible"`, sin listas vacías ni errores genéricos.

---

### Escenario 6: Filtros Manuales (Categorías OR + Rango AND + Búsqueda AND) y Limpieza Unificada (US4, COM-4.2.2, C-1)

- **Objetivo**: Validar el álgebra disyuntiva en categorías, el rango de precios cerrado inclusivo, la composición conjuntiva con búsqueda de texto y la limpieza unificada simultánea (AC-4.1 a AC-4.5, INV-14, INV-15, INV-16, FR-016 a FR-020, SC-012, SC-013, EC-012 a EC-018, Clarification #1).
- **Pasos**:
  1. En el catálogo, marcar en la barra lateral de filtros las categorías "Pastas" y "Pizzas" (semántica OR).
  2. Fijar en el rango de precios: Mínimo = `5000` y Máximo = `10000` (intervalo inclusivo $[5.000, 10.000]$).
  3. Ingresar en la barra de búsqueda por texto: `"casero"`.
  4. Verificar que la grilla exhibe exclusivamente platos que pertenezcan a Pastas o Pizzas, cuyo precio esté entre `$ 5.000` y `$ 10.000`, y cuyo nombre o receta contenga "casero".
  5. Modificar el filtro de precio fijando Mínimo = `15000` y Máximo = `5000` ($P_{\min} > P_{\max}$).
  6. Verificar advertencia de validación: `"El precio mínimo no puede superar al precio máximo"` y bloqueo de aplicación del rango.
  7. Ajustar a valores muy restrictivos sin coincidencias: verificar mensaje explicativo de falta de resultados y botón "Limpiar filtros".
  8. Presionar el botón "Limpiar filtros".
- **Resultado Esperado**:
  - Se desmarcan todas las categorías en la barra lateral.
  - Se vacían los campos de precio mínimo y máximo.
  - Se vacía simultáneamente la barra de búsqueda de texto del catálogo.
  - La grilla restaura el 100% de los platos del menú original.

---

### Escenario 7: Persistencia de Filtros Manuales en Sesión por Restaurante (US4, COM-4.2.2, C-3)

- **Objetivo**: Verificar que los filtros manuales sobrevivan a la navegación hacia el perfil o carrito dentro del mismo restaurante, y se restablezcan al cambiar de local (INV-16, FR-024, Clarification #3).
- **Pasos**:
  1. Aplicar un filtro manual de categoría "Postres" y rango `$ 2.000` a `$ 6.000` en el Restaurante A.
  2. Navegar a la pantalla de Perfil (`/perfil`) y luego a la vista del Carrito (`/carrito`).
  3. Regresar al catálogo del Restaurante A (`/catalogo`).
  4. Verificar que la categoría "Postres" y el rango de precios siguen marcados y la grilla permanece filtrada.
  5. Presionar "Cambiar local", seleccionar el Restaurante B y confirmar.
- **Resultado Esperado**:
  - Al cargar el Restaurante B, los filtros manuales se inicializan limpios por defecto, mostrando la totalidad de la carta del Restaurante B sin heredar las selecciones del local anterior.

