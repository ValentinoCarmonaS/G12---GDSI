# Especificación formal: corrección C-13 — motor de filtrado por estilos de vida y dietas en el catálogo (comensal)

**Fecha**: 10/11/2026  
**Rama de trabajo**: `fix/C-13-filtro-estilos-vida-dietas`  
**Estado**: Especificado (Fase 1 SDD)  
**Specs impactadas**:
- `specs/008-diner-catalog-multi-tenant/spec.md` (FR-034)
- `specs/011-dish-detail-manual-filters/spec.md` (US 4, AC-4.7 a AC-4.9, FR-025 a FR-028)
- `specs/011-dish-detail-manual-filters/data-model.md` (§2.1, §2.6, §2.7)

---

## 1. Contexto y problema identificado

En el sistema EasyEat, la plataforma gestiona una taxonomía dual de restricciones alimentarias bajo la tabla `dietary_restrictions`, compuesta por:
1. **Estilos de vida / Dietas (`DIETARY_LIFESTYLE`)**: por ejemplo, Celíaco / Sin TACC (`CELIAC`), Vegano (`VEGAN`), Vegetariano (`VEGETARIAN`).
2. **Alérgenos comunes (`COMMON_ALLERGEN`)**: por ejemplo, Lácteos / Lactosa (`DAIRY`), Maní (`PEANUTS`), Frutos secos (`TREE_NUTS`), Huevos (`EGGS`), Pescado (`FISH`), Mariscos (`SHELLFISH`), Soja (`SOY`), Trigo / Gluten (`WHEAT`).

En la implementación actual del catálogo del comensal (`specs/008` y `specs/011`), el endpoint `GET /api/v1/establishments/{tenantId}/catalog` expone exclusivamente la lista de `allergens`, filtrando en el servicio backend las restricciones pertenecientes a `COMMON_ALLERGEN`. 

Como consecuencia:
- Las etiquetas de estilo de vida (`DIETARY_LIFESTYLE`) asignadas a los platos (`dish_dietary_restrictions` / `restriction_codes`) no son expuestas en el DTO del plato (`DishCatalogItemDto`).
- Al seleccionar filtros manuales de estilo de vida en la interfaz del comensal, la consulta y el filtrado omiten evaluar la presencia de estas etiquetas, manteniendo visibles platos incompatibles y erosionando la confianza del usuario celíaco, vegano o vegetariano.
- La barra lateral de filtros manuales (`ManualFiltersSidebar`) carece de controles para seleccionar de forma combinada e interactiva estilos de vida y alérgenos excluyentes con semántica estricta.

---

## 2. Requerimiento formal C-13

**Como**: Comensal.  
**Quiero**: Que los filtros de búsqueda por dietas y restricciones alimentarias evalúen tanto los Estilos de vida como los Alérgenos.  
**Para**: Encontrar con precisión únicamente los platos compatibles con mi alimentación y asegurar que el sistema excluya de forma confiable las opciones no aptas.

---

## 3. Criterios de aceptación

1. **Filtro por estilo de vida (semántica inclusiva requerida)**:
   - Dado un comensal en la pantalla de catálogo o búsqueda del establecimiento, cuando aplica un filtro por Estilo de vida (ej. Celíaco / Sin TACC, Vegano, Vegetariano), entonces el sistema consulta y devuelve únicamente los platos que tengan asignada explícitamente esa etiqueta en su configuración (`lifestyles`).
   - Dado un plato no compatible o que no tenga asignada la etiqueta de estilo de vida seleccionada por el usuario, cuando se activa dicho filtro en la interfaz, entonces el plato se oculta y excluye completamente del listado de resultados.

2. **Filtro por alérgenos (semántica excluyente)**:
   - Dado un comensal que selecciona un alérgeno para evitar (ej. Lácteos, Maní, Huevo), cuando se activa el filtro, entonces el sistema oculta y excluye completamente de los resultados cualquier plato que contenga dicho alérgeno.

3. **Intersección estricta (AND multidimensional)**:
   - Dado un comensal que selecciona una combinación simultánea de filtros (ej. Alérgeno "Sin Lactosa" + Estilo de vida "Vegano"), cuando el sistema filtra los platos, entonces aplica una intersección estricta (AND): el plato debe ser apto para el estilo de vida (contener la etiqueta) Y no contener el alérgeno excluido.
   - Si se combinan además con filtros de categoría gastronómica (OR), rango de precios [min, max] o término de búsqueda textual, el resultado debe satisfacer la conjunción de todas las dimensiones activas.

4. **Exposición en API de catálogo**:
   - Dado el catálogo de comensal, cuando se exponen los platos en la API (`/api/v1/establishments/{tenantId}/catalog`), el DTO del plato (`DishCatalogItemDto`) debe incluir tanto la lista de alérgenos (`allergens`) como la lista de estilos de vida / dietas (`lifestyles`), permitiendo al cliente web inspeccionar y filtrar por ambas taxonomías de forma confiable.

5. **Persistencia y restablecimiento en sesión**:
   - Los filtros dietarios y de estilos de vida seleccionados manualmente deben preservarse en `sessionStorage` asociados al establecimiento activo.
   - La acción "Limpiar filtros" debe desmarcar todas las selecciones de estilos de vida y alérgenos simultáneamente con las categorías, precios y búsqueda textual.

---

## 4. Diseño del modelo y contratos técnicos

### 4.1 Extensión de `DishCatalogItemDto` (Backend)

```java
public class DishCatalogItemDto {
    private UUID id;
    private String name;
    private String description;
    private String category;
    private BigDecimal price;
    private String photoUrl;
    private Boolean isAvailable;
    private Integer availableStock;
    private Boolean isCompliant;
    private List<String> conflictingRestrictions;
    private List<String> recipeIngredients;
    private List<String> allergens;   // COMMON_ALLERGEN: presencia
    private List<String> lifestyles;  // DIETARY_LIFESTYLE: compatibilidad declarada
}
```

### 4.2 Servicio `DinerCatalogService`

El servicio debe resolver la taxonomía de estilos de vida desde `dietary_restrictions`:
- `allergens`: códigos o nombres asociados con `category = COMMON_ALLERGEN`.
- `lifestyles`: códigos o nombres asociados con `category = DIETARY_LIFESTYLE` presentes en `dish.getRestrictionCodes()`.

### 4.3 Modelo en TypeScript (`frontend`)

```typescript
export interface DishCatalogItem {
  id: string;
  name: string;
  description: string | null;
  category: string;
  price: number;
  photoUrl: string | null;
  isAvailable: boolean;
  availableStock?: number;
  isCompliant: boolean;
  conflictingRestrictions: string[];
  recipeIngredients: string[];
  allergens?: string[];
  lifestyles?: string[];
}
```

### 4.4 Predicado formal de inclusión en cliente y servidor

Para cada plato $p$:
$$\text{cumple}(p) \iff \text{cumple\_categoria}(p) \land \text{cumple\_precio}(p) \land \text{cumple\_lifestyles}(p) \land \text{cumple\_alergenos}(p) \land \text{cumple\_busqueda}(p)$$

Donde:
- $\text{cumple\_lifestyles}(p) \iff \forall l \in L_{\text{sel}}: l \in p.\text{lifestyles}$
- $\text{cumple\_alergenos}(p) \iff \forall a \in A_{\text{sel}}: a \notin p.\text{allergens}$
