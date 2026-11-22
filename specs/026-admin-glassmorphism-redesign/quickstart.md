# Guía de validación (Quickstart): Spec 026 — Rediseño ejecutivo Glassmorphism del Administrador

**Feature**: `specs/026-admin-glassmorphism-redesign/spec.md`  
**Contracts**: [contracts/glassmorphism-ui-blocks.contract.ts](contracts/glassmorphism-ui-blocks.contract.ts), [contracts/admin-navigation-bimodal.contract.ts](contracts/admin-navigation-bimodal.contract.ts), [contracts/admin-screens-audit.contract.ts](contracts/admin-screens-audit.contract.ts)

---

## 1. Prerrequisitos

- Rama activa: `feat/026-admin-glassmorphism-redesign` (creada desde `feat/frontend-redesign`).
- Dependencias instaladas en `easyeat/frontend/`.

```powershell
cd easyeat/frontend
npm install
```

## 2. Verificación automatizada (SC-002, SC-005)

Ejecutar pruebas trazables de Spec `026`, suite completa de regresión (`001`–`026`), chequeo de tipos y build de producción:

```powershell
# 1. Suite nueva de Spec 026
npx vitest run tests/admin/

# 2. Suite completa de frontend (cero regresiones 001–026)
npm test -- --run

# 3. Typecheck y build de producción
npx tsc --noEmit
npm run build
```

**Resultado esperado**: 100 % de las pruebas en verde, 0 errores de TypeScript, bundle compilado sin dependencias externas añadidas.

## 3. Escenarios de validación en navegador real (SC-001, SC-003, SC-004, SC-006)

Levantar el servidor de desarrollo (`npm run dev`) y verificar en `360px` (móvil), `768px` (tableta) y `1440px` (escritorio):

1. **Navegación bimodal y `pendingCount` (US1)**:
   - En `1440px`: barra lateral Glassmorphism fija y expandida con 6 ítems agrupados (`Operación`, `Catálogo y equipo`, `Negocio`), botón hamburguesa oculto, insignia de `pendingCount` junto a `Auditoría` cuando hay pendientes.
   - En `360px` y `768px`: barra lateral oculta, menú hamburguesa accesible (`>= 44×44 px`, cierre con `Escape` y retorno de foco).
2. **Panel Ejecutivo en `/admin` y redirección `/admin/analytics` (US2)**:
   - Navegar a `/admin/analytics` → redirige a `/admin`.
   - En `/admin`: KPIs en banda superior en `JetBrains Mono`, debajo 2 columnas en PC (gráfico `dataViz` ~60–65 % + Ranking en tarjetas ~35–40 %) y apilado en móvil; alternar `"Ver tabla"` / `"Ver gráfico"` sin modo oscuro heredado.
3. **Revisión de Platos y Costos (US3)**:
   - En `/admin/dishes`: split-pane en `1440px`; maestro → detalle con `Volver a la bandeja` en `360px`/`768px`. Al aprobar, rechazar o cancelar un plato, el contador `pendingCount` en navegación se actualiza de inmediato sin recargar.
   - En `/admin/dishes/:id/cost-audit`: desglose en tarjetas Glassmorphism y alertas semánticas WCAG 2.2 AA.
4. **Personal e Inventario en tarjetas y modales (US4)**:
   - En `/admin/staff` y `/admin/inventory`: tarjetas responsivas sin scroll horizontal en `360px`, filtro `Solo insumos en stock crítico` con señalización redundante, modales operables por teclado (`Escape` + retorno de foco).
5. **Operación, Mi Local y Primer Ingreso (US5)**:
   - En `/admin/operations`: horarios en tarjetas, cupos preservando `role="row"`, pagos con manejo de 409.
   - En `/admin/mi-local`: 2 columnas en PC; editar colores actualiza únicamente la previsualización sin contaminar el tema Glassmorphism.
   - En `/first-login-password-change`: tarjeta polimórfica vía `AuthCardShell` con checklist en tiempo real WCAG 2.2 AA.
6. **Auditoría visual Impeccable (`/critique`)**:
   - Verificar modo **Operate**, política Zero AI-Slop, `prefers-reduced-motion` y `prefers-reduced-transparency`.
