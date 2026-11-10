# Fix C-11: Reclasificación de "Celíaco" de estilo de vida a alérgeno (chef / administrador / comensal)

**Fecha**: 10/11/2026  
**Rama**: `fix/C-11-reclasificacion-celiaco-alergeno` (origen: `develop/sprint-4`)  
**Módulos impactados**:
- `specs/003-diner-profile-dietary`
- `specs/005-chef-dish-management`
- `specs/007-admin-dish-approval-pricing`
- `specs/008-admin-dish-rejection-feedback`
- `specs/008-diner-catalog-multi-tenant`
- `specs/011-dish-detail-manual-filters`

---

## 1. Contexto y justificación del requerimiento

En la arquitectura previa del sistema, la etiqueta "Celíaco" (`CELIAC` / Sin TACC) se encontraba erróneamente catalogada dentro de la categoría `DIETARY_LIFESTYLE` ("Estilos de vida"), junto con elecciones dietarias personales o éticas (`VEGAN`, `VEGETARIAN`).

La celiaquía es una enfermedad autoinmune crónica multisistémica provocada por la ingesta de gluten (presente en trigo, avena, cebada y centeno - TACC). No constituye una preferencia o estilo de vida optativo, sino una condición de salud estricta regida por normativas sanitarias y de seguridad alimentaria.

Clasificar "Celíaco" como un estilo de vida genera incoherencias críticas:
1. En la experiencia de usuario (Chef, Administrador y Comensal), minimiza una condición médica severa al agruparla con elecciones voluntarias.
2. En la lógica de evaluación de conformidad (`CatalogComplianceService`), los estilos de vida operan por inclusión obligatoria (el plato debe poseer el sello para ser apto), mientras que los alérgenos operan por exclusión preventiva (si el plato contiene el alérgeno declarado, genera conflicto con el perfil del comensal que no puede consumirlo).
3. En la ficha informativa de detalle del plato (`DishDetailModal`), los alérgenos declarados deben visualizarse como sellos de advertencia neutros para la salud alimentaria de todos los comensales.

La corrección **C-11** desvincula definitivamente "Celíaco" de los estilos de vida y lo reclasifica formalmente en el grupo de alérgenos comunes y restricciones médicas de salud en base de datos, backend, contratos OpenAPI y frontend.

---

## 2. Requerimiento formal C-11

**Como**: Chef / Administrador / Comensal.  
**Quiero**: Que la condición de "Celíaco" (o Gluten / Sin Gluten / Sin TACC) se encuentre catalogada bajo la sección de Alérgenos / Restricciones de salud y no como un Estilo de vida.  
**Para**: Reflejar con precisión médica la naturaleza de la celiaquía, cumplir con las normas de seguridad alimentaria y evitar riesgos de salud en el comensal.

### Criterios de aceptación:
1. **Formularios de creación, edición y auditoría de platos (Chef y Administrador)**:
   - Dado el formulario de gestión de platos del chef (`AllergenStep` / paso 3) y la vista de auditoría y feedback del administrador (`RejectionDialog`), cuando se cargan o visualizan las etiquetas taxonómicas, la opción "Celíaco" (`CELIAC`) se despliega exclusivamente dentro de la sección "Alérgenos comunes" / advertencias médicas.
   - La opción queda 100% removida del apartado "Estilos de vida", el cual queda reservado exclusivamente para elecciones dietarias positivas (`VEGAN`, `VEGETARIAN`).
2. **Visualización en catálogo y modal de detalle del plato (Comensal)**:
   - Dado un comensal que consulta la ficha técnica o modal detallado de un plato (`DishDetailModal`), cuando visualiza las advertencias nutricionales del producto, observa el distintivo de "Celíaco / Sin TACC" (`CELIAC`) agrupado de forma neutral junto al resto de los alérgenos declarados del plato (Lácteos, Maní, Mariscos, etc.).
   - Si un plato contiene `CELIAC` como alérgeno declarado y el comensal tiene configurada dicha restricción en su perfil, el sistema genera el conflicto correspondiente y exhibe la insignia de advertencia preventiva ("No apto Celíaco").
3. **Panel de filtros de búsqueda y preferencias del comensal**:
   - Dado el selector de preferencias dietarias del perfil del comensal (`DietaryRestrictionsSelector`), la opción "Sin TACC / Celíaco" se presenta agrupada bajo el bloque de "Alérgenos comunes" y no en "Estilos de vida".
   - Cuando se habiliten o consulten filtros por restricciones médicas en el catálogo, la opción de filtrado para platos aptos para celíacos se clasifica formalmente como restricción de alérgeno/alerta médica.
4. **Migración e integridad de datos existentes en base de datos**:
   - Dado el esquema relacional de la base de datos, cuando se ejecute la migración de datos, todos los registros previos de platos y comensales asociados a la restricción `CELIAC` se preservan intactos, actualizando la categorización en la tabla maestra `dietary_restrictions` de `DIETARY_LIFESTYLE` a `COMMON_ALLERGEN` sin pérdida de registros ni claves foráneas huérfanas.

---

## 3. Taxonomía reestructurada de la plataforma

La taxonomía cerrada de la plataforma (`dietary_restrictions`) se redefine en dos categorías con límites semánticos estrictos:

### Categoría 1: Estilos de vida (`DIETARY_LIFESTYLE`)
Opciones de elección personal o ética. Operan bajo semántica de cumplimiento positivo ("este plato es apto para...").
- `VEGAN`: Vegano (excluye ingredientes de origen animal).
- `VEGETARIAN`: Vegetariano (excluye carnes y pescados; admite huevos y lácteos).

### Categoría 2: Alérgenos comunes y restricciones médicas (`COMMON_ALLERGEN`)
Sustancias controladas que representan un riesgo biológico o inmunológico para la salud. Operan bajo semántica de advertencia preventiva ("este plato contiene o presenta riesgo de trazas de...").
- `CELIAC`: Sin TACC / Celíaco (presencia de gluten / trigo, avena, cebada o centeno; no apto para personas con celiaquía).
- `DAIRY`: Lácteos / Lactosa.
- `PEANUTS`: Maní.
- `TREE_NUTS`: Frutos secos.
- `EGGS`: Huevos.
- `FISH`: Pescado.
- `SHELLFISH`: Mariscos / Crustáceos.
- `SOY`: Soja.
- `WHEAT`: Trigo / Gluten.

---

## 4. Estrategia de migración de base de datos (SQL / Flyway)

### Arquitectura de datos actual
El modelo de datos relacional (introducido en `V5__extend_users_profile_and_dietary.sql`) desacopla la categoría de las tablas de relación:
- `dietary_restrictions`: Almacena el catálogo con `(code, name, category, description)`.
  - Constraint: `CONSTRAINT chk_dietary_category CHECK (category IN ('DIETARY_LIFESTYLE', 'COMMON_ALLERGEN'))`.
- `dish_dietary_restrictions`: Asocia `(dish_id, restriction_code)`. Clave foránea `fk_ddr_restriction` referencia a `dietary_restrictions(code)`.
- `user_dietary_restrictions`: Asocia `(user_id, restriction_code)`. Clave foránea `fk_udr_restriction` referencia a `dietary_restrictions(code)`.

### Migración SQL determinística
Dado que las tablas puente (`dish_dietary_restrictions` y `user_dietary_restrictions`) no almacenan la columna `category` sino únicamente la clave foránea `restriction_code = 'CELIAC'`, la actualización del registro maestro en `dietary_restrictions` reasigna la taxonomía de forma inmediata y global:

```sql
-- Flyway Migration: Reclasificación taxonómica de CELIAC a COMMON_ALLERGEN
UPDATE dietary_restrictions
SET category = 'COMMON_ALLERGEN',
    description = 'Presencia de gluten (trigo, avena, cebada, centeno) o trazas; no apto para celíacos.'
WHERE code = 'CELIAC';
```

### Garantías de integridad:
1. **Cero pérdida de datos**: Ninguna fila de platos asociados (`dish_dietary_restrictions`) ni preferencias de usuarios (`user_dietary_restrictions`) se elimina o altera.
2. **Sin violación de constraints**: El valor `'COMMON_ALLERGEN'` satisface el constraint `chk_dietary_category`.
3. **Idempotencia**: Si la sentencia se ejecuta repetidas veces o sobre una base ya migrada, el resultado es determinístico e invariante.

---

## 5. Impacto en contratos, backend y frontend

### Backend (Spring Boot)
1. **`CatalogComplianceService`**:
   - `CELIAC` ahora evalúa bajo la rama `meta.getCategory() == DietaryCategory.COMMON_ALLERGEN`.
   - Si un comensal tiene configurado `CELIAC` en su perfil y el plato contiene el código `CELIAC` (marcado por el chef como advertencia de gluten), se genera un conflicto alimentario (`conflicts.add("CELIAC")`), marcando el plato como no apto para el comensal.
   - Si el plato no contiene `CELIAC`, el comensal celíaco puede consumirlo de forma segura.
2. **`DinerCatalogService`**:
   - `resolveAllergenTaxonomy()` filtra `DietaryCategory.COMMON_ALLERGEN`. Con la migración, incluye automáticamente a `CELIAC`.
   - `FALLBACK_ALLERGEN_NAMES` se actualiza para incluir `"CELIAC", "Sin TACC / Celíaco"`.
   - En el DTO `DishCatalogItemDto`, la lista `neutralAllergens` expone `"Sin TACC / Celíaco"` dentro de los alérgenos del plato.
3. **Endpoint `/api/v1/dietary-restrictions`**:
   - Retorna `{"code": "CELIAC", "category": "COMMON_ALLERGEN", ...}`.

### Frontend (React / TypeScript)
1. **`allergenTaxonomy.ts`**:
   - `CELIAC` se traslada de `DIETARY_LIFESTYLES` a `COMMON_ALLERGENS`.
   - `DIETARY_LIFESTYLES` contiene únicamente `VEGAN` y `VEGETARIAN`.
2. **`AllergenStep.tsx` (Chef)**:
   - Se renderiza automáticamente en la grilla de "Alérgenos comunes" y desaparece de "Estilos de vida".
3. **`RejectionDialog.tsx` (Administrador)**:
   - La lista de alérgenos auditables ubica `CELIAC` en la sección de alérgenos y no en estilos de vida.
4. **`DietaryRestrictionsSelector.tsx` (Perfil del comensal)**:
   - Al filtrar por `category === 'COMMON_ALLERGEN'`, "Sin TACC / Celíaco" se muestra bajo el bloque "Alérgenos comunes".
5. **`DishDetailModal.tsx` y `DishCard.tsx` (Comensal)**:
   - Los sellos neutros de alérgenos (`AllergenNeutralPill`) y las insignias de advertencia preventiva (`AllergenWarningBadge`) reconocen `CELIAC` como alérgeno de riesgo médico.

---

## 6. Matriz de trazabilidad de requisitos por especificación

| Especificación | Sección / Requisito | Cambio aplicado |
|---|---|---|
| **Spec 003** (`diner-profile-dietary`) | `FR-012`, Escenario 1, `data-model.md`, `openapi.yaml` | `CELIAC` se clasifica como `COMMON_ALLERGEN`. `DIETARY_LIFESTYLE` contiene solo `VEGAN` y `VEGETARIAN`. |
| **Spec 005** (`chef-dish-management`) | `CHF-2.2.3`, `FR-013`, `dishes.yaml`, `business-and-safety.md` | El formulario de carga y edición del Chef ubica `CELIAC` en el grupo de alérgenos con advertencia de contención. |
| **Spec 007** (`admin-dish-approval-pricing`) / **C-07** | Auditoría y modal de rechazo con feedback | La visualización de auditoría y propuesta de alérgenos clasifica `CELIAC` bajo alérgenos. |
| **Spec 008** (`diner-catalog-multi-tenant`) | Catálogo enriquecido y filtros de seguridad | El motor de cumplimiento (`CatalogComplianceService`) trata `CELIAC` como alérgeno excluyente preventivo. |
| **Spec 011** (`dish-detail-manual-filters`) | `FR-008`, `INV-6`, `DishDetailModal` | Los sellos neutros de alérgenos incluyen `CELIAC` en la sección de alérgenos declarados del plato. |
