# Contrato Operativo 06: Ejecución de Migraciones y Semillas con Flyway

**Artefacto**: `application.yml` (`spring.flyway.*`) y directorio `db/`  
**Propósito**: Garantizar la ejecución completa, ordenada e idempotente de las migraciones `V1` a `V19` y los scripts repetibles de datos semilla `R__seed_dev_*` sobre la base de datos de Neon durante el arranque del backend (HU-04, FR-016, FR-017, AS-15, AS-16, Q1).  

---

## 1. Especificación del Manifiesto de Migración

### 1.1. Rutas de Escaneo
- Migraciones estructuradas versionadas: `classpath:db/migration`
- Migraciones repetibles y datos semilla: `classpath:db/dev`

### 1.2. Inventario Canónico de Scripts Ejecutables
1. **Migraciones Versionadas (`db/migration/`)**:
   - `V1__create_users.sql`
   - `V2__create_password_reset_tokens.sql`
   - `V3__extend_users_staff.sql`
   - `V4__create_ingredients_and_stock_audit.sql`
   - `V5__extend_users_profile_and_dietary.sql`
   - `V6__create_ingredient_batches_and_refactor_lifecycle.sql`
   - `V7__chef_dish_management.sql`
   - `V8__create_establishments.sql`
   - `V9__admin_dish_approval_pricing.sql`
   - `V10__support_admin_dish_cancellation.sql`
   - `V11__sync_ingredient_unit_purchase_cost.sql`
   - `V12__extend_user_demographics_and_avatar.sql`
   - `V13__dish_review_history_snapshot.sql`
   - `V14__add_units_per_package_to_ingredients.sql`
   - `V15__restaurant_operation_config.sql`
   - `V16__add_time_slot_and_order_fulfillment.sql`
   - `V17__critical_stock_and_chef_pause.sql`
   - `V18__mercadopago_checkout_and_reservations.sql`
   - `V19__add_dish_margin_percentage_and_backfill.sql`
2. **Scripts Repetibles de Semillas (`db/dev/`)**:
   - `R__seed_dev_users.sql`
   - `R__seed_dev_establishments.sql`
   - `R__seed_dev_dishes_and_dietary.sql`

---

## 2. Invariantes del Contrato

1. **Inv-Flyway-01 (Alcance Completo V1 a V19)**: En una base de datos vacía en Neon, Flyway debe aplicar la totalidad de scripts desde `V1` hasta `V19` en orden correlativo estricto, sin saltos ni omisiones (AS-16, Q1/A1).
2. **Inv-Flyway-02 (Idempotencia de Scripts Repetibles)**: Los scripts `R__seed_dev_*` deben poder re-ejecutarse en arranques sucesivos o ante cambios sin violar restricciones de clave primaria o índices únicos (mediante cláusulas `ON CONFLICT DO NOTHING`).
3. **Inv-Flyway-03 (Bloqueo Consultivo Obligatorio)**: Flyway debe obtener exitosamente el bloqueo consultivo `pg_advisory_lock` en PostgreSQL antes de aplicar cualquier migración, garantizando que despliegues o reinicios concurrentes no corrompan el esquema.
4. **Inv-Flyway-04 (Aborto ante Error de Esquema)**: Si cualquier script SQL falla durante su ejecución, Flyway debe detener inmediatamente la inicialización del contexto de Spring Boot, impidiendo que la aplicación atienda peticiones con esquema incompleto.

---

## 3. Fallos Esperados y Mitigación

| Escenario de Falla | Comportamiento | Mitigación |
|---|---|---|
| Falla de `pg_advisory_lock` por conexión a PgBouncer con pooler | Error fatal de inicio de Flyway | Conexión directa obligatoria a Neon (sin `-pooler`). |
| Reinicio de la aplicación ejecuta `R__seed_dev_users` nuevamente | Violación de clave única si el insert no es idempotente | Inserciones protegidas con `ON CONFLICT DO NOTHING`. |
| Script `V19` no ejecutado | Falta de columnas de margen en platos | Configuración explícita de escaneo `classpath:db/migration`. |

---

## 4. Verificación Observable

1. **Inspección de logs de Flyway al inicio**:
   ```text
   Flyway Community Edition ... by Redgate
   Database: jdbc:postgresql://... (PostgreSQL 16)
   Successfully validated 19 migrations (execution time ...)
   Current version of schema "public": << Empty Schema >>
   Migrating schema "public" to version "1 - create users"
   ...
   Migrating schema "public" to version "19 - add dish margin percentage and backfill"
   Successfully applied 19 migrations to schema "public"
   Executing repeatable migration "R__seed_dev_users"
   Executing repeatable migration "R__seed_dev_establishments"
   Executing repeatable migration "R__seed_dev_dishes_and_dietary"
   ```
2. **Consulta a tabla de metadatos de Flyway en Neon**:
   ```sql
   SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
   -- Debe mostrar 19 registros de V1 a V19 con success=true, más los registros repetibles R__.
   ```

