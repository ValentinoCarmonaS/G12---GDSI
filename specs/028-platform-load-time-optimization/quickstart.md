# Guía de validación ejecutable extremo a extremo (Quickstart — Spec 028)

**Rama**: `028-platform-load-time-optimization` | **Fecha**: 24/11/2026 | **Especificación**: [spec.md](./spec.md)

Esta guía define los escenarios ejecutables para verificar de punta a punta las cuatro historias de usuario (US1–US4) y los seis criterios de éxito medibles (SC-001–SC-006). Los detalles de entidades, índices y contratos HTTP residen en [data-model.md](./data-model.md) y en [contracts/](./contracts/).

---

## 1. Prerrequisitos

- **Backend**: Java 21 (JDK 21), Maven 3.9+, Docker en ejecución (requerido por Testcontainers PostgreSQL 16 para pruebas de integración).
- **Frontend**: Node.js 20+ y npm 10+.
- **Base de datos local (opcional para pruebas manuales)**: PostgreSQL 16 (p. ej. vía `docker compose up -d` en el directorio del proyecto) con credenciales por defecto de `application-local.yml`.
- **Entorno productivo / staging (para validar SC-003 sobre RTT intercontinental)**: Despliegue en Render Free (US-West Oregon) + Neon Free (São Paulo) con instancias en caliente (activas dentro de la ventana de 15 minutos).

---

## 2. Comandos de preparación (Setup)

Desde la raíz del repositorio:

```bash
# Verificar rama activa
git branch --show-current
# Esperado: 028-platform-load-time-optimization

# Instalar dependencias existentes del frontend (sin agregar paquetes nuevos)
cd easyeat/frontend
npm ci

# Compilar y validar dependencias del backend (sin agregar paquetes nuevos)
cd ../backend
mvn test-compile
```

---

## 3. Comandos de ejecución y pruebas automatizadas

### 3.1 Suite completa de backend (unitarias, contratos, integración Testcontainers y cobertura JaCoCo ≥ 80 %)

```bash
cd easyeat/backend
mvn verify
```

Para ejecuciones focalizadas por módulo durante la validación:

```bash
# US1 — Catálogo y motor de disponibilidad (FR-001, FR-002)
mvn test -Dtest=DinerCatalogServiceTest,DishAvailabilityEngineTest,DinerAvailabilityControllerTest,DinerCatalogQuickstartIntegrationTest,DinerCatalogAvailabilityPollingTest,CatalogStockExhaustionSyncIntegrationTest

# US2 — Franjas horarias de Checkout (FR-003)
mvn test -Dtest=TimeSlotOccupancyTest,PickupSlotServiceTest

# US3 — Mostrador, KDS y Gestión de Platos del Chef (FR-004)
mvn test -Dtest=CounterOrderControllerIntegrationTest,CounterOrderControllerSecurityCodeTest,KitchenKdsControllerIntegrationTest,KitchenKdsExclusionTest,DishBasicServiceTest,DishChefPauseOpenApiContractTest,ChefDishPauseIntegrationTest

# US4 — Cachés, invalidación de sesión, binarios ETag/304, pool e índices (FR-005, FR-006, FR-009)
mvn test -Dtest=JwtAuthenticationFilterTest,LoginIntegrationTest,StaffStatusControllerIntegrationTest,DishPhotoControllerTest,DinerLogoEndpointTest,ProfileAvatarStaffControllerTest,NeonDataSourceConfigurationTest
```

### 3.2 Suite completa de frontend (Vitest + React Testing Library + verificación de tipos TypeScript)

```bash
cd easyeat/frontend
npm run lint
npm test
npm run build
```

### 3.3 Levantamiento local para validación manual con DevTools y cURL

```bash
# Terminal 1 — Backend local (con show-sql activo para auditar cardinalidad de consultas SQL)
cd easyeat/backend
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Terminal 2 — Frontend local
cd easyeat/frontend
npm run dev
```

---

## 4. Escenarios de validación y resultados esperados por US y SC

### US1 + SC-001 + SC-003 + SC-004 — Catálogo del comensal y disponibilidad

1. **Cardinalidad SQL constante (`SC-001`)**:
   - Invocar `GET /api/v1/establishments/{tenantId}/catalog` sobre un establecimiento con ≥ 20 platos publicados y recetas de múltiples ingredientes (tanto como invitado como autenticado con restricciones dietarias).
   - **Resultado esperado**: ≤ 6 consultas SQL ejecutadas en el servidor (verificable vía contador de Hibernate Statistics en pruebas de integración y log SQL local), salida JSON y ordenamiento de categorías/platos/ingredientes/alérgenos/estilos de vida idénticos a [contracts/01-catalogo-y-disponibilidad.md](./contracts/01-catalogo-y-disponibilidad.md).
2. **Cero peticiones duplicadas en arranque (`SC-004`)**:
   - Abrir DevTools → Network en `/catalogo` autenticado como `COMENSAL` y recargar la página.
   - **Resultado esperado**: exactamente `1` petición `GET /api/v1/profile/me`, `1` petición `GET /api/v1/establishments/{tenantId}/branding`, `1` petición `GET /api/v1/establishments/{tenantId}/catalog`, `0` peticiones a `/dishes` heredado, `0` peticiones a `/dietary-restrictions` y `0` peticiones a `/catalog/availability` en `t = 0 ms` (el primer sondeo de disponibilidad se dispara recién a los `2500 ms` enviando `If-None-Match` en ciclos sucesivos y recibiendo `304 Not Modified` si no hubo cambios).
3. **Navegación repetida instantánea vía SWR y estabilidad visual (`US1`, `FR-008`, `SC-003`)**:
   - Navegar de `/catalogo` → `/descubrimiento` o `/perfil` y volver a `/catalogo`.
   - **Resultado esperado**: renderizado de tarjetas y branding en `< 100 ms` sin esqueleto de carga a pantalla completa; la revalidación en segundo plano actualiza datos silenciosamente sin reiniciar scroll, categoría activa, filtros ni modales abiertos; si se simula fallo de red en segundo plano (DevTools Offline temporal durante la revalidación), los datos visibles se conservan sin mostrar pantalla de error bloqueante. En la primera visita sin caché y con fallo de red, sí se exhibe el esqueleto inicial y la pantalla de error con botón `Reintentar`.
4. **Latencia en entorno desplegado en caliente (`SC-003`)**:
   - Medir tiempo total de carga de datos de `/catalogo` en Render Free + Neon Free (servicios activos).
   - **Resultado esperado**: `< 1,5 s` (reducción desde ~10 s).

---

### US2 + SC-002 + SC-003 — Franjas horarias de retiro en Checkout

1. **Consulta agrupada única (`SC-002`)**:
   - Con platos en el carrito, abrir `/checkout`, elegir retiro programado y alternar entre fechas dentro de los próximos 7 días con órdenes activas distribuidas en múltiples franjas de 15 minutos.
   - **Resultado esperado**: el servidor ejecuta exactamente `1` consulta SQL agrupada (`GROUP BY time_slot_start`) para calcular todas las franjas del día (ver [data-model.md §2](./data-model.md) y [contracts/02-turnos-retiro-checkout.md](./contracts/02-turnos-retiro-checkout.md)), preservando lead time, capacidad máxima, estado `HIGH_DEMAND` (`>= 80 %`) y ocultamiento de franjas `SOLD_OUT` o vencidas.
2. **Latencia en caliente (`SC-003`)**:
   - **Resultado esperado**: respuesta de `GET /api/v1/establishments/{id}/pickup-slots` en `< 1 s` en entorno desplegado en caliente (reducción desde 5–8 s).

---

### US3 + SC-002 — Agilidad operativa en Mostrador, KDS y Gestión de Platos

1. **Mostrador (`/mostrador`)**:
   - Con múltiples comandas `READY_FOR_PICKUP`, verificar `GET /api/v1/counter/orders`.
   - **Resultado esperado**: ≤ 4 consultas SQL constantes ($O(1)$) independientemente del número de comandas (ver [contracts/03-operacion-mostrador-kds-chef.md §1](./contracts/03-operacion-mostrador-kds-chef.md)).
2. **Tablero KDS (`/cocina`)**:
   - Verificar `GET /api/v1/kitchen/dashboard/summary`.
   - **Resultado esperado**: exactamente `1` consulta SQL agregada devolviendo los 4 contadores idénticos (ver [contracts/03-operacion-mostrador-kds-chef.md §2](./contracts/03-operacion-mostrador-kds-chef.md)).
3. **Gestión de Platos (`/cocina/platos`)**:
   - Verificar `GET /api/v1/dishes` y el sondeo cada `2500 ms` de `GET /api/v1/dishes/operational-status`.
   - **Resultado esperado**: ≤ 3–4 consultas SQL constantes ($O(1)$); respuesta `200 OK` con cabecera `ETag` visible vía CORS (`Access-Control-Expose-Headers: ETag`), y respuesta `304 Not Modified` sin cuerpo cuando `If-None-Match` coincide y sin excepciones en consola del navegador (ver [contracts/03-operacion-mostrador-kds-chef.md §3–§4](./contracts/03-operacion-mostrador-kds-chef.md)).

---

### US4 + SC-005 + SC-006 — Caché coherente, transporte, binarios `304`, índices y recursos

1. **Validación de sesión cacheada e invalidación inmediata (`FR-005`, `SC-005`)**:
   - Ejecutar varias peticiones autenticadas consecutivas y verificar que `users` no se reconsulta en cada request.
   - Ejecutar cierre de sesión (`POST /api/v1/auth/logout`), inicio de sesión concurrente, cambio de contraseña o suspensión de cuenta por Administrador; reutilizar de inmediato el JWT previo.
   - **Resultado esperado**: rechazo instantáneo con `401 Unauthorized` o `403 Forbidden` sin ventana de inconsistencia. En el cliente, el logout o `401` purga el 100 % del `ClientSWRStore` impidiendo ver datos personalizados previos.
2. **Coherencia de metadatos y stock en vivo (`FR-005`, `SC-005`)**:
   - Modificar branding, subir logotipo o cambiar horarios operativos como Administrador y consultar de inmediato el catálogo o checkout: el cambio se refleja en la siguiente lectura.
   - Pausar un plato como Chef o agotar stock / crear reserva en Checkout y consultar el catálogo: el cambio se refleja de inmediato (cero caché temporal sobre stock o reservas).
3. **Binarios condicionales `ETag` / `304` sin lectura `BYTEA` (`FR-006`, `SC-006`)**:
   - Ejecutar `curl -i` contra `/api/v1/dishes/{id}/photo`, `/api/v1/establishments/{id}/logo` y `/api/v1/profile/users/{id}/avatar`:
     ```bash
     # 1) Primera petición: capturar ETag y verificar Cache-Control exacto
     curl -i "http://localhost:8080/api/v1/dishes/<DISH_ID>/photo"
     # Esperado: 200 OK, ETag: "<hex>", Cache-Control: private, max-age=86400

     # 2) Revalidación condicional: enviar If-None-Match
     curl -i -H 'If-None-Match: "<ETAG_OBTENIDO>"' "http://localhost:8080/api/v1/dishes/<DISH_ID>/photo"
     # Esperado: 304 Not Modified, cuerpo vacío (Content-Length: 0), sin lectura SQL de columna BYTEA
     ```
   - Verificar `Cache-Control` exactos definidos en [contracts/04-medios-binarios-y-transporte.md](./contracts/04-medios-binarios-y-transporte.md).
4. **Compresión GZIP, carga diferida de imágenes, pool cálido e índices (`FR-009`, `SC-006`)**:
   - Verificar cabecera `Content-Encoding: gzip` en respuestas JSON ≥ `1024 bytes` solicitadas con `Accept-Encoding: gzip`.
   - Inspeccionar elementos `<img>` de `DishCard` en el DOM: confirmar atributos `loading="lazy"` y `decoding="async"`.
   - Verificar en PostgreSQL (`EXPLAIN`) la presencia y uso de los índices de la migración versionada descrita en [data-model.md §6](./data-model.md): `idx_dishes_public_catalog`, `idx_order_items_dish_order` e `idx_temp_reservations_active_expires_order`.
   - Confirmar consumo estable de memoria del backend por debajo de `512 MB` con `minimum-idle = 5` y `maximum-pool-size = 5`, y ejecución 100 % en verde de `mvn verify` y `npm test` (`SC-006`).

---

## 5. Referencias a artefactos de diseño

- Plan técnico y matriz de trazabilidad: [plan.md](./plan.md)
- Decisiones de investigación (Fase 0): [research.md](./research.md)
- Modelo de datos, cachés e índices: [data-model.md](./data-model.md)
- Contratos de interfaz:
  - [01-catalogo-y-disponibilidad.md](./contracts/01-catalogo-y-disponibilidad.md)
  - [02-turnos-retiro-checkout.md](./contracts/02-turnos-retiro-checkout.md)
  - [03-operacion-mostrador-kds-chef.md](./contracts/03-operacion-mostrador-kds-chef.md)
  - [04-medios-binarios-y-transporte.md](./contracts/04-medios-binarios-y-transporte.md)
  - [05-estado-cliente-swr-y-deduplicacion.md](./contracts/05-estado-cliente-swr-y-deduplicacion.md)
