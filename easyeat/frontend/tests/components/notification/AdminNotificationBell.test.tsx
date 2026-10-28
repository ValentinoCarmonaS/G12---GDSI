import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AdminNotificationBell } from '@/components/notification/AdminNotificationBell';
import type { AdminNotificationResponse } from '@/types/notification';

describe('AdminNotificationBell Component (T063, CHF-2.2.4)', () => {
  const mockOnMarkAsRead = vi.fn();

  const mockNotifications: AdminNotificationResponse[] = [
    {
      id: 'n1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
      tenantId: 't1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
      type: 'DISH_SUBMITTED_FOR_REVIEW',
      title: 'Nuevo plato enviado a revisión',
      message: "El Chef ha enviado la ficha técnica 'Risotto de Hongos' para revisión de precios.",
      dishId: 'd1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
      chefId: 'c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
      isRead: false,
      createdAt: '2026-10-28T01:00:00Z',
    },
    {
      id: 'n2eebc99-9c0b-4ef8-bb6d-6bb9bd380a02',
      tenantId: 't1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
      type: 'DISH_SUBMITTED_FOR_REVIEW',
      title: 'Plato enviado',
      message: 'Ficha técnica Tarta de Manzana enviada.',
      dishId: 'd2eebc99-9c0b-4ef8-bb6d-6bb9bd380a02',
      chefId: 'c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01',
      isRead: true,
      createdAt: '2026-10-28T00:30:00Z',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders bell button and badge with unread alert count', () => {
    render(
      <AdminNotificationBell
        notifications={mockNotifications}
        unreadCount={1}
        onMarkAsRead={mockOnMarkAsRead}
      />
    );

    const bellBtn = screen.getByRole('button', { name: /notificaciones/i });
    expect(bellBtn).toBeInTheDocument();

    const badge = screen.getByTestId('notification-badge');
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveTextContent('1');
  });

  it('does not render count badge when unreadCount is 0', () => {
    render(
      <AdminNotificationBell
        notifications={[]}
        unreadCount={0}
        onMarkAsRead={mockOnMarkAsRead}
      />
    );

    expect(screen.queryByTestId('notification-badge')).not.toBeInTheDocument();
  });

  it('opens notifications dropdown when clicking the bell and lists items', async () => {
    const user = userEvent.setup();
    render(
      <AdminNotificationBell
        notifications={mockNotifications}
        unreadCount={1}
        onMarkAsRead={mockOnMarkAsRead}
      />
    );

    const bellBtn = screen.getByRole('button', { name: /notificaciones/i });
    await user.click(bellBtn);

    expect(screen.getByText('Nuevo plato enviado a revisión')).toBeInTheDocument();
    expect(screen.getByText(/Risotto de Hongos/i)).toBeInTheDocument();
  });

  it('calls onMarkAsRead when clicking "Marcar como leída"', async () => {
    const user = userEvent.setup();
    render(
      <AdminNotificationBell
        notifications={mockNotifications}
        unreadCount={1}
        onMarkAsRead={mockOnMarkAsRead}
      />
    );

    const bellBtn = screen.getByRole('button', { name: /notificaciones/i });
    await user.click(bellBtn);

    const markReadBtn = screen.getByRole('button', { name: /Marcar como leída/i });
    await user.click(markReadBtn);

    expect(mockOnMarkAsRead).toHaveBeenCalledWith('n1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01');
  });
});
