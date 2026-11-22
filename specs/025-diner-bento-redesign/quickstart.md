# Guía rápida de validación: Rediseño visual Bento del Comensal (Spec 025)

**Feature**: `specs/025-diner-bento-redesign`  
**Fecha**: 22/11/2026  
**Referencias**: [spec.md](spec.md), [data-model.md](data-model.md), [contracts/bento-ui-blocks.contract.ts](contracts/bento-ui-blocks.contract.ts), [contracts/brand-accent-slots.contract.ts](contracts/brand-accent-slots.contract.ts), [contracts/diner-screens-audit.contract.ts](contracts/diner-screens-audit.contract.ts)

---

## Prerrequisitos

1. Rama activa `feat/025-diner-bento-redesign` (creada desde `feat/frontend-redesign` con Spec `024` integrada).
2. Dependencias instaladas en `easyeat/frontend`:
   ```powershell
   Set-Location "easyeat\frontend"
   npm ci
   ```

---

## Escenarios de validación automatizada (`SC-001`–`SC-005`)

### 1. Verificación de tipos y compilación de producción

```powershell
Set-Location "easyeat\frontend"
npx tsc --noEmit
npm run build
```

**Resultado esperado**: cero errores de TypeScript y bundle generado sin advertencias de activos faltantes ni importaciones rotas.

### 2. Suite nueva de contratos Bento, brand slots y accesibilidad (Spec 025)

```powershell
Set-Location "easyeat\frontend"
npx vitest run tests/diner/
```

**Resultado esperado**:
- `BentoCard`, `AuthCardShell`, `AccessibleModal` y `AccessibleDrawer` validan foco, cierre por `Escape`, retorno de foco y ausencia de anidamiento redundante.
- Los cuatro `Focused Brand Accent Slots` (`header`, `category`, `cta`, `confirmation`) respetan aislamiento `--brand-*` vs `--ee-*` y cumplen WCAG 2.2 AA tanto con `DEFAULT_NEUTRAL_BRANDING` (Bento) como con branding personalizado extremo.
- `/login` con deep-link hacia `/mostrador`, `/cocina` y `/admin` hereda los tokens `--ee-*` del rol destino sin filtrar estilos fijos de Bento.

### 3. Regresión completa de suites heredadas (`001`–`024`)

```powershell
Set-Location "easyeat\frontend"
npx vitest run
```

**Resultado esperado**: 100 % de las pruebas conductuales existentes (`src/test/*` y `tests/*`) aprobadas, sin cambios en contratos Axios ni debilitamiento de aserciones (`SC-005`).

---

## Escenarios de verificación visual y auditoría Impeccable (`SC-002`, `SC-003`, `SC-004`, `SC-006`)

### 4. Inspección en navegador real en viewports `360px`, `768px` y `1440px`

Levantar el servidor de desarrollo frontend:

```powershell
Set-Location "easyeat\frontend"
npm run dev
```

Recorrer las 11 pantallas listadas en `contracts/diner-screens-audit.contract.ts` verificando:

1. **US1 (`/login`, `/register`, `/forgot-password`, `/reset-password`, `/verificar-email`, `/descubrimiento`)**:
   - Tarjeta centrada polimórfica sobre `#FFF5E6`, sin desbordamiento horizontal en `360px`.
   - En `/descubrimiento`, búsqueda de 3+ caracteres muestra resultados Bento, skeletons, estado vacío y error con reintento; tarjeta de restaurante activo y grilla de 3 bloques informativos.
2. **US2 (`/catalogo`)**:
   - Probar con establecimiento de marca personalizada y con marca neutra Bento: verificar que solo `header`, `category` activa y `cta` usan `--brand-*`.
   - Verificar barra lateral de filtros colapsable en `1440px` y panel deslizable accesible en `360px`.
   - Verificar insignias de alérgenos y modal de riesgo (texto + ícono, contraste `>= 4.5:1`), `DishDetailModal`, `CartDrawer` y `EstablishmentSwitchModal`.
3. **US3 (`/checkout`, `/checkout/payment-return`)**:
   - Grilla Bento de 2 columnas en `1440px` con `CheckoutSummary` fijo a la derecha y montos en `JetBrains Mono`; columna única limpia en `360px`.
   - Franjas horarias con objetivos táctiles `>= 44×44 px` y navegación por teclado.
   - Estados de retorno (`approved`, `pending`, `rejected`, error) en tarjeta Bento centrada.
4. **US4 (`/checkout/confirmacion/:orderId`, `/pedidos/:orderId/seguimiento`)**:
   - Cabecera con acento de marca, insignia de conexión en vivo, progreso por pasos, cuenta regresiva/demora y tarjeta héroe de PIN de alta legibilidad a distancia (`< 2 s`).
5. **US5 (`/perfil`, `/perfil#pedidos`)**:
   - Pestañas segmentadas Bento sincronizadas con el hash `#pedidos`.
   - Grilla Bento de 2 columnas en `Mi perfil` (`1440px`) y columna única en `360px`; grilla Bento de tarjetas de pedido en `Mis pedidos`.

### 5. Auditoría crítica de diseño (Constitución Principio XI)

Ejecutar `/impeccable critique` sobre las vistas rediseñadas verificando contra `.specify/memory/designs/comensal.DESIGN.md`:
- Cero uso de `Inter` en todo el DOM del Comensal (`DM Sans` + `JetBrains Mono` activos).
- Cero degradados genéricos púrpura-azul, cero tarjetas anidadas (`cards-in-cards`), cero texto gris de bajo contraste sobre superficies coloreadas.
- Todas las transiciones `<= 200ms` y suprimidas bajo `prefers-reduced-motion: reduce`.
- Contraste WCAG 2.2 AA (`>= 4.5:1` texto normal, `>= 3:1` texto grande/bordes/foco) y objetivos táctiles `>= 44×44 px`.
