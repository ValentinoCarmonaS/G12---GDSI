# Checklist de requisitos para el spec 006 – Cálculo de Food Cost

---

## Ítems de verificación (sin marcar)

- [ ] **CHK001** Modelo N:M: La tabla `dish_recipe_ingredients` debe contener claves compuestas correctas y referencias FK a `dish` e `ingredient`.
- [ ] **CHK002** Campo `quantity` > 0 y con precisión adecuada.
- [ ] **CHK003** Evitar duplicados de combinación `dish_id` + `ingredient_id`.
- [ ] **CHK004** Ingredientes descontinuados (`discontinued = true`) no deben poder asociarse a una receta.
- [ ] **CHK005** Aislamiento por tenant: los registros deben respetar el `tenant_id` del contexto.
- [ ] **CHK006** Estado DRAFT: al crear o actualizar una receta, validar que **todos** los ingredientes tengan precio asignado (según respuesta A3).
- [ ] **CHK007** Cálculo de Food Cost: **sin redondeo** (exacto), usar `BigDecimal` con el método `setScale` apropiado (respuesta A1).
- [ ] **CHK008** Cuando algún ingrediente carece de precio, la receta debe marcarse con la bandera `has_unpriced_ingredients = true` y **permitirse** su guardado (respuesta A4).
- [ ] **CHK009** Endpoint de auditoría de costos: acceso restringido **solo al rol ADMIN** (respuesta A2).
- [ ] **CHK010** Seguridad del endpoint: validar autenticación y autorización antes de exponer datos de auditoría.
- [ ] **CHK011** Performance: aunque no hay límite estricto, se recomienda que la latencia sea razonable (< 250 ms) y que se realicen pruebas de carga.
- [ ] **CHK012** Documentación de API: incluir ejemplos de request/response y describir el comportamiento de la bandera `has_unpriced_ingredients`.
- [ ] **CHK013** Pruebas unitarias e integración que cubran todos los casos anteriores, incluyendo escenarios de precios faltantes y validaciones de rol.

---

*Este checklist está basado en las respuestas proporcionadas y en el contenido del spec 006. Revisá que los ítems cubran todos los requisitos críticos y, si falta algo, avísame para ajustarlo.*
