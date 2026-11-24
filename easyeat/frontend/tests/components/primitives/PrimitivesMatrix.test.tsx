import fs from 'node:fs';
import path from 'node:path';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ThemeScopeProvider } from '@/theme/ThemeScopeProvider';
import type { RoleThemeId } from '@/theme/types';
import {
  Button,
  StatusBadge,
  FeedbackState,
  LoadingSkeleton,
  EmptyState,
} from '@/components/primitives';

const THEME_ROUTES: readonly { readonly themeId: RoleThemeId; readonly route: string }[] = [
  { themeId: 'comensal', route: '/catalogo' },
  { themeId: 'mostrador', route: '/mostrador' },
  { themeId: 'chef', route: '/cocina' },
  { themeId: 'admin', route: '/admin' },
];

type PrimitiveCase = {
  readonly name: string;
  readonly renderNode: () => JSX.Element;
  readonly assertRendered: () => void;
};

const PRIMITIVE_CASES: readonly PrimitiveCase[] = [
  {
    name: 'Button',
    renderNode: () => <Button variant="primary">Acción principal</Button>,
    assertRendered: () => {
      const btn = screen.getByRole('button', { name: 'Acción principal' });
      expect(btn.className).toContain('bg-ee-primary');
      expect(btn.className).toContain('min-h-[44px]');
    },
  },
  {
    name: 'StatusBadge',
    renderNode: () => <StatusBadge variant="success">Activo</StatusBadge>,
    assertRendered: () => {
      const badge = screen.getByTestId('status-badge');
      expect(badge).toHaveTextContent('Activo');
      expect(badge.className).toContain('bg-ee-success-bg');
      expect(badge.className).toContain('text-ee-success-fg');
    },
  },
  {
    name: 'FeedbackState',
    renderNode: () => (
      <FeedbackState
        variant="danger"
        title="Error operativo"
        message="No se pudo completar la operación."
        actionLabel="Reintentar"
        onAction={vi.fn()}
      />
    ),
    assertRendered: () => {
      const alert = screen.getByRole('alert');
      expect(alert).toHaveAttribute('aria-live', 'assertive');
      expect(alert.className).toContain('bg-ee-danger-bg');
      expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    },
  },
  {
    name: 'LoadingSkeleton',
    renderNode: () => <LoadingSkeleton variant="text" lines={2} />,
    assertRendered: () => {
      const status = screen.getByRole('status');
      expect(status).toHaveAttribute('aria-busy', 'true');
      const shapes = status.querySelectorAll('[data-testid="skeleton-shape"]');
      expect(shapes).toHaveLength(2);
      expect(shapes[0].className).toContain('motion-reduce:animate-none');
    },
  },
  {
    name: 'EmptyState',
    renderNode: () => (
      <EmptyState
        title="Sin registros"
        description="Todavía no hay datos para mostrar."
        actionLabel="Actualizar"
        onAction={vi.fn()}
      />
    ),
    assertRendered: () => {
      const empty = screen.getByTestId('empty-state');
      expect(empty.className).toContain('rounded-ee-lg');
      expect(screen.getByRole('heading', { name: 'Sin registros' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Actualizar' })).toBeInTheDocument();
    },
  },
];

const MATRIX_COMBINATIONS = THEME_ROUTES.flatMap(({ themeId, route }) =>
  PRIMITIVE_CASES.map((primitive) => ({
    themeId,
    route,
    primitive,
  }))
);

describe('Primitives matrix — 20 combinations (5 primitives × 4 themes) & pure polymorphism (FR-016, FR-017, SC-005)', () => {
  it('verifies matrix covers exactly 20 combinations', () => {
    expect(MATRIX_COMBINATIONS).toHaveLength(20);
  });

  it.each(MATRIX_COMBINATIONS)(
    'renders $primitive.name cleanly under theme "$themeId" ($route)',
    ({ themeId, route, primitive }) => {
      render(
        <MemoryRouter initialEntries={[route]}>
          <ThemeScopeProvider>{primitive.renderNode()}</ThemeScopeProvider>
        </MemoryRouter>
      );

      expect(document.documentElement).toHaveAttribute('data-theme', themeId);
      expect(screen.getByTestId('theme-scope-root')).toHaveAttribute('data-theme', themeId);
      primitive.assertRendered();
    }
  );

  it('guarantees zero references to role or theme names in src/components/primitives/*.tsx (FR-016)', () => {
    const primitivesDir = path.resolve(process.cwd(), 'src/components/primitives');
    const tsxFiles = fs
      .readdirSync(primitivesDir)
      .filter((file) => file.endsWith('.tsx'))
      .sort();

    expect(tsxFiles).toEqual([
      'Button.tsx',
      'EmptyState.tsx',
      'FeedbackState.tsx',
      'LoadingSkeleton.tsx',
      'StatusBadge.tsx',
    ]);

    const forbiddenPattern =
      /\b(comensal|mostrador|chef|admin|COMENSAL|ENCARGADO|CHEF|ADMINISTRADOR|RoleThemeId|EasyEatUserRole)\b/;

    for (const fileName of tsxFiles) {
      const source = fs.readFileSync(path.join(primitivesDir, fileName), 'utf8');
      expect(source, `Forbidden role/theme reference found in ${fileName}`).not.toMatch(
        forbiddenPattern
      );
    }
  });
});
