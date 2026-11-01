# Quickstart & End-to-End Validation Guide: Shopping Cart & Diner Demographics

**Feature Branch**: `009-shopping-cart`  
**Fecha**: 31/10/2026  
**Estado**: Completado  
**Referencia de Especificación**: [spec.md](./spec.md) | [data-model.md](./data-model.md) | [contracts/openapi.yaml](./contracts/openapi.yaml)

---

## 1. Prerrequisitos y Configuración del Entorno

### 1.1. Prerrequisitos Técnicos
- Java 21 JDK instalado y configurado en el PATH.
- Node.js 18+ y npm instalados.
- Docker y Docker Compose activos (para la instancia de base de datos PostgreSQL 16 con Flyway).
- Repositorio ubicado en rama `009-shopping-cart`.

### 1.2. Inicialización de Servicios
```bash
# 1. Iniciar la base de datos PostgreSQL local
cd easyeat/backend
docker compose up -d postgres

# 2. Compilar el backend y ejecutar migraciones Flyway (incluyendo V10)
./mvnw clean compile flyway:migrate

# 3. Iniciar el servidor backend Spring Boot (puerto 8080)
./mvnw spring-boot:run

# 4. En otra terminal, instalar dependencias y levantar el frontend SPA (puerto 5173)
cd ../frontend
npm install
npm run dev
```

---

## 2. Escenarios de Validación End-to-End

### Escenario 1: Selector Previo de Cantidad y Agregar al Carrito (COM-6.1.1)
*Objetivo*: Validar selector en detalle de plato, adición a sesión local y notificación visible (AC-1.1, AC-1.2, FR-001, FR-002, SC-001).

1. Iniciar sesión como comensal (`comensal@easyeat.com` / `Password123!`).
2. Seleccionar un restaurante activo en `/descubrimiento` y navegar a `/catalogo`.
3. Hacer click sobre la tarjeta de un plato con stock disponible (ej. "Milanesa Napolitana").
4. **Verificación visual**: En el modal de detalle (`DishDetailModal`), verificar la presencia del selector numérico con valor predeterminado en `1` y botones `+` y `-`.
5. Ajustar el selector a `3` unidades y presionar "Agregar al Carrito".
6. **Resultado esperado**:
   - Se despliega una notificación visible de éxito confirmando la adición de 3 unidades.
   - En la esquina inferior derecha aparece de inmediato el botón flotante del carrito exhibiendo `3` ítems y el subtotal exacto (INV-1, INV-2, INV-7).
   - Reabrir el detalle del mismo plato, seleccionar `1` unidad adicional y presionar "Agregar al Carrito": la cantidad total se incrementa a `4` sin duplicar filas (AC-1.2).

---

### Escenario 2: Visibilidad Biunívoca del Botón Flotante y Apertura de Modal (COM-6.1.2)
*Objetivo*: Validar visibilidad condicional, actualización reactiva y precisión decimal monetaria (AC-2.1 a AC-2.4, INV-1, INV-2, INV-6, INV-7).

1. Con productos en el carrito, navegar entre categorías del catálogo ("Entradas", "Platos principales", "Postres").
2. **Verificación visual**: El botón flotante permanece fijo en la esquina inferior derecha mostrando el acumulado de unidades y el subtotal en tiempo real.
3. Navegar a la pantalla `/perfil`.
4. **Resultado esperado**: El botón flotante se oculta automáticamente para no interferir con los formularios (INV-7, FR-005).
5. Volver a `/catalogo` y hacer click sobre el botón flotante.
6. **Resultado esperado**: Se abre el modal/drawer (`CartDrawer`) desplegando el desglose íntegro: nombre de cada plato, precio unitario, cantidad asignada y subtotal por línea exacto.

---

### Escenario 3: Modificación Interactiva, Bloqueo de Stock y Reversión ante Falla (COM-6.1.3)
*Objetivo*: Probar suma/resta, límite de inventario y rollback determinista por desconexión (AC-3.1 a AC-3.5, INV-3, INV-9, SC-003, SC-004).

1. Abrir el modal del carrito.
2. Presionar el control `+` en una línea: la cantidad sube inmediatamente en 1 unidad y los totales se recalculan al instante (AC-3.1, SC-002).
3. Continuar incrementando hasta alcanzar el stock real disponible en cocina para dicho plato.
4. **Resultado esperado**: El control `+` se deshabilita visualmente y aparece la advertencia de stock máximo alcanzado (AC-3.3, INV-3).
5. Simular fallo de conectividad (desconectar red o simular error de red en DevTools "Offline").
6. Presionar rápidamente modificaciones de cantidad.
7. **Resultado esperado**: Al transcurrir la ventana de debounce de 300 ms, el sistema detecta la falla de comunicación, retrotrae deterministamente el contador al último valor sincronizado confirmado y presenta una notificación emergente de error de conexión (AC-3.5, INV-9).

---

### Escenario 4: Eliminación Total al Restar Última Unidad (COM-6.1.3)
*Objetivo*: Validar remoción total de ítem y ocultamiento inmediato del botón flotante (AC-3.4, INV-6).

1. Dejar el carrito con un único plato cuya cantidad sea `1`.
2. Abrir el modal del carrito y presionar el botón `-` sobre dicho plato.
3. **Resultado esperado**:
   - El plato se elimina completamente de la orden (no queda en 0 ni negativo).
   - El modal refleja el estado de "Tu carrito está vacío".
   - Al cerrar el modal, el botón flotante exterior ha desaparecido de inmediato de la pantalla (INV-6, SC-009).

---

### Escenario 5: Carga de Foto de Perfil y Actualización de Avatar en Navbar (COM-1.4.2)
*Objetivo*: Validar subida de imagen $\le 5$ MB y reflejo inmediato en barra superior (AC-4.1, INV-4, SC-008).

1. Navegar a `/perfil` y situarse en la sección "Datos Demográficos".
2. Seleccionar un archivo JPEG, PNG o WEBP con tamaño menor o igual a 5 MB (ej. 1,2 MB).
3. Presionar "Guardar cambios".
4. **Resultado esperado**:
   - La imagen se persiste en el backend (`POST /api/v1/profile/avatar`).
   - El avatar en la barra de navegación superior (`WorkspaceLayout.tsx`) se actualiza inmediatamente mostrando la nueva fotografía sin requerir recargar la página.

---

### Escenario 6: Rechazo de Imagen > 5 MB y Formatos No Soportados (COM-1.4.2)
*Objetivo*: Probar validaciones de tamaño y formato con mensajes explicativos (AC-4.2, AC-4.3, INV-4, SC-005).

1. En el selector de foto de perfil, intentar cargar un archivo de 5,5 MB (5.767.168 bytes).
2. **Resultado esperado**: La carga se bloquea inmediatamente en cliente, mostrando el mensaje de error: `"El archivo supera el tamaño máximo permitido de 5 MB."`. La foto previa permanece intacta.
3. Intentar seleccionar un archivo con formato `.pdf` o `.gif`.
4. **Resultado esperado**: El sistema rechaza el archivo y despliega el mensaje: `"Formato no soportado. Debe ser JPEG, PNG o WebP."`.

---

### Escenario 7: Validación de Rango de Edad y Domicilio Aislado (COM-1.4.2)
*Objetivo*: Probar validación de edad (14 a 120 años) y almacenamiento de domicilio (AC-4.4, AC-4.6, INV-5, INV-8, SC-006).

1. En el campo de edad ingresar valores inválidos: `-5`, `13`, `125`, `25.5` o `treinta`.
2. **Resultado esperado**: El formulario resalta el campo en rojo, bloquea el guardado y muestra `"La edad debe ser un número entero entre 14 y 120 años."` (AC-4.4, INV-5).
3. Ingresar `29`, seleccionar género `"No binario"`, ingresar domicilio `"Av. Callao 450, Piso 3, CABA"` y presionar "Guardar cambios".
4. **Resultado esperado**:
   - Los datos se guardan exitosamente (`PUT /api/v1/profile/demographics`).
   - En la base de datos se verifica que el domicilio se guarda exclusivamente como dato de texto informativo, sin disparar llamadas a motores de ruteo externos (INV-8).

---

### Escenario 8: Eliminación Diferida de Foto de Perfil (COM-1.4.2)
*Objetivo*: Validar diálogo de confirmación diferido y avatar por defecto (AC-4.5, FR-024, FR-025, SC-008).

1. Teniendo una foto de perfil personalizada configurada, presionar "Eliminar foto".
2. En el diálogo de confirmación emergente, pulsar "Confirmar eliminación".
3. **Verificación visual provisional**: La vista previa del formulario muestra provisionalmente el avatar genérico, pero la barra de navegación superior AÚN conserva la foto original.
4. Presionar "Cancelar" o recargar la página: la foto original se conserva intacta.
5. Volver a presionar "Eliminar foto", confirmar en el diálogo y esta vez presionar "Guardar cambios".
6. **Resultado esperado**: Se consolida la remoción en el servidor (`DELETE /api/v1/profile/avatar`), y tanto el perfil como la barra de navegación superior restablecen de inmediato el avatar predeterminado de la plataforma (SC-008).

---

## 3. Comandos de Verificación Automatizada

### 3.1. Tests Unitarios y de Integración Backend (JUnit 5 + MockMvc)
```bash
cd easyeat/backend
./mvnw test -Dtest=ProfileControllerTest,UserPhotoServiceTest,DemographicsValidationTest
```

### 3.2. Tests de Componentes y Contexto Frontend (Vitest + React Testing Library)
```bash
cd easyeat/frontend
npm test src/test/cart/CartContext.test.tsx
npm test src/test/cart/FloatingCartButton.test.tsx
npm test src/test/cart/DishDetailModal.test.tsx
npm test src/test/profile/PersonalDataForm.test.tsx
```
