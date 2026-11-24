/**
 * Spec 027 — Chef Brutalism Redesign
 * Audit manifest for all in-scope Chef routes across 360px, 768px, 1024px and 1440px,
 * plus the explicit registry of scoped legacy test updates authorized by FR-015.
 */

export interface ChefScreenAuditEntry {
  readonly route: string;
  readonly pageComponent: string;
  readonly userStory: 'US1' | 'US2' | 'US3' | 'US4' | 'US5';
  readonly themeMode: 'chef-brutalism';
  readonly fullWidthLayout: boolean;
  readonly listsPattern: 'kds-kanban-cards' | 'brutalist-cards-only';
  readonly nativeBrowserDialogsAllowed: false;
  readonly financialDataAllowed: false;
  readonly requiredViewportsPx: readonly [360, 768, 1024, 1440];
}

export const CHEF_SCREENS_AUDIT_MANIFEST: readonly ChefScreenAuditEntry[] = [
  {
    route: '/cocina',
    pageComponent: 'KitchenWorkspacePage',
    userStory: 'US2',
    themeMode: 'chef-brutalism',
    fullWidthLayout: true,
    listsPattern: 'kds-kanban-cards',
    nativeBrowserDialogsAllowed: false,
    financialDataAllowed: false,
    requiredViewportsPx: [360, 768, 1024, 1440],
  },
  {
    route: '/cocina/platos',
    pageComponent: 'ChefDishesPage',
    userStory: 'US3',
    themeMode: 'chef-brutalism',
    fullWidthLayout: false,
    listsPattern: 'brutalist-cards-only',
    nativeBrowserDialogsAllowed: false,
    financialDataAllowed: false,
    requiredViewportsPx: [360, 768, 1024, 1440],
  },
  {
    route: '/cocina/platos/nuevo',
    pageComponent: 'ChefDishEditorPage',
    userStory: 'US4',
    themeMode: 'chef-brutalism',
    fullWidthLayout: false,
    listsPattern: 'brutalist-cards-only',
    nativeBrowserDialogsAllowed: false,
    financialDataAllowed: false,
    requiredViewportsPx: [360, 768, 1024, 1440],
  },
  {
    route: '/cocina/platos/:id',
    pageComponent: 'ChefDishEditorPage',
    userStory: 'US4',
    themeMode: 'chef-brutalism',
    fullWidthLayout: false,
    listsPattern: 'brutalist-cards-only',
    nativeBrowserDialogsAllowed: false,
    financialDataAllowed: false,
    requiredViewportsPx: [360, 768, 1024, 1440],
  },
  {
    route: '/cocina/ingredientes',
    pageComponent: 'ChefIngredientsPage',
    userStory: 'US5',
    themeMode: 'chef-brutalism',
    fullWidthLayout: false,
    listsPattern: 'brutalist-cards-only',
    nativeBrowserDialogsAllowed: false,
    financialDataAllowed: false,
    requiredViewportsPx: [360, 768, 1024, 1440],
  },
] as const;

export interface AuthorizedLegacyTestUpdate {
  readonly file: string;
  readonly authorizationRef: string;
  readonly scopedChangeOnly: string;
}

export const AUTHORIZED_LEGACY_TEST_UPDATES: readonly AuthorizedLegacyTestUpdate[] = [
  {
    file: 'easyeat/frontend/tests/components/KitchenKanbanBoard.test.tsx',
    authorizationRef: 'Spec 027 — FR-015(a)',
    scopedChangeOnly:
      'Reemplazar aserciones de clases del modo oscuro heredado (border-amber-500, ring-2, bg-amber-100, text-amber-800, text-slate-100) por tokens Brutalism claros de chef.DESIGN.md.',
  },
  {
    file: 'easyeat/frontend/tests/pages/KitchenWorkspacePage.test.tsx',
    authorizationRef: 'Spec 027 — FR-015(b)',
    scopedChangeOnly:
      'Retirar aserción del bloque redundante de ingredientes embebido en /cocina y verificar que el acceso directo vive en la navegación del Chef.',
  },
  {
    file: 'easyeat/frontend/tests/components/RoleLayoutShell.test.tsx',
    authorizationRef: 'Spec 027 — FR-015(d) / FR-005',
    scopedChangeOnly:
      'Actualizar regex de altura mínima en cabecera directa del Chef para aceptar min-h-[48px] (>= 44px).',
  },
  {
    file: 'easyeat/frontend/tests/components/dish/{BasicIdentityStep,RecipeBOMStep,AllergenStep,ReviewSubmitStep}.test.tsx',
    authorizationRef: 'Spec 027 — FR-015(c) / Grill-Me Q4',
    scopedChangeOnly:
      'Adaptar interacciones de botón de guardado por paso al contrato unificado Anterior/Siguiente (pasos 1-3) y Guardar borrador + Guardar y enviar a revisión (paso 4), preservando todas las validaciones de dominio.',
  },
  {
    file: 'easyeat/frontend/tests/integration/us{1_basic_identity,2_recipe_definition,3_allergen_labeling,4_review_submission}.test.tsx',
    authorizationRef: 'Spec 027 — FR-015(c) / Grill-Me Q4',
    scopedChangeOnly:
      'Recorrer el asistente con Anterior/Siguiente y confirmar la orquestación de dishApi al ejecutar Guardar borrador o Guardar y enviar a revisión en el Paso 4.',
  },
] as const;
