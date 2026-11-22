import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AdminNotificationBell } from '@/components/notification/AdminNotificationBell';
import type { AdminNotificationResponse } from '@/types/notification';

const sampleNotifications: AdminNotificationResponse[] = [
  {
    id: 'notif-1',
    tenantId: 'tenant-1',
    type: 'DISH_SUBMITTED_FOR_REVIEW',
    title: 'Nuevo plato en revisión',
    message: 'El chef envió "Empanadas salteñas" para aprobación.',
    dishId: 'dish-1',
    chefId: 'chef-1',
    isRead: false,
    createdAt: '2026-11-21T14:30:00-03:00',
  },
  {
    id: 'notif-2',
    tenantId: 'tenant-1',
    type: 'DISH_SUBMITTED_FOR_REVIEW',
    title: 'Stock actualizado',
    message: 'Se repuso harina 000.',
    dishId: 'dish-2',
    chefId: 'chef-1',
    isRead: true,
    createdAt: '2026-11-21T12:00:00-03:00',
  },
];

describe('AdminNotificationBell WCAG 2.2 AA (FR-007, FR-010)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('exposes >= 44x44px touch target, aria-expanded, and aria-haspopup on the trigger button', () => {
    render(
      <AdminNotificationBell
        notifications={sampleNotifications}
        unreadCount={1}
        onMarkAsRead={vi.fn()}
      />
    );

    const trigger = screen.getByRole('button', { name: /notificaciones/i });
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
    expect(trigger).toHaveAttribute('aria-haspopup', 'dialog');
    expect(trigger.className).toMatch(/min-h-\[44px\]/);
    expect(trigger.className).toMatch(/min-w-\[44px\]/);
    expect(screen.getByTestId('notification-badge')).toHaveTextContent('1');
  });

  it('updates aria-expanded on open, invokes onRefresh, and closes on Escape returning focus to trigger', async () => {
    const user = userEvent.setup();
    const onRefresh = vi.fn();

    render(
      <AdminNotificationBell
        notifications={sampleNotifications}
        unreadCount={1}
        onMarkAsRead={vi.fn()}
        onRefresh={onRefresh}
      />
    );

    const trigger = screen.getByRole('button', { name: /notificaciones/i });
    await user.click(trigger);

    expect(trigger).toHaveAttribute('aria-expanded', 'true');
    expect(onRefresh).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId('notifications-dropdown')).toBeInTheDocument();

    const markReadButton = screen.getByRole('button', { name: /marcar como leída/i });
    markReadButton.focus();
    expect(markReadButton).toHaveFocus();

    await user.keyboard('{Escape}');

    expect(screen.queryByTestId('notifications-dropdown')).not.toBeInTheDocument();
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
    expect(trigger).toHaveFocus();
  });

  it('provides sr-only unread state text and WCAG 2.2 AA semantic token styling for timestamps', async () => {
    const user = userEvent.setup();
    const onMarkAsRead = vi.fn();

    render(
      <AdminNotificationBell
        notifications={sampleNotifications}
        unreadCount={1}
        onMarkAsRead={onMarkAsRead}
      />
    );

    await user.click(screen.getByRole('button', { name: /notificaciones/i }));

    const unreadSrText = screen.getByText(/no leída/i);
    expect(unreadSrText).toHaveClass('sr-only');

    const timestamps = screen.getAllByTestId('notification-timestamp');
    expect(timestamps).toHaveLength(2);
    for (const timestamp of timestamps) {
      expect(timestamp.className).toContain('text-ee-text-secondary');
      expect(timestamp.className).not.toContain('text-zinc-400');
    }

    await user.click(screen.getByRole('button', { name: /marcar como leída/i }));
    expect(onMarkAsRead).toHaveBeenCalledWith('notif-1');
  });

  it('closes when clicking outside and shows empty state when there are no notifications', async () => {
    const user = userEvent.setup();

    render(
      <div>
        <div data-testid="outside">Outside</div>
        <AdminNotificationBell
          notifications={[]}
          unreadCount={0}
          onMarkAsRead={vi.fn()}
        />
      </div>
    );

    const trigger = screen.getByRole('button', { name: /notificaciones/i });
    await user.click(trigger);

    expect(screen.getByText('No hay notificaciones disponibles.')).toBeInTheDocument();

    fireEvent.mouseDown(screen.getByTestId('outside'));
    expect(screen.queryByTestId('notifications-dropdown')).not.toBeInTheDocument();
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
  });

  it('displays an assertive error alert with retry action instead of empty state when error is present (T046 P1-1)', async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();

    render(
      <AdminNotificationBell
        notifications={[]}
        unreadCount={0}
        error="No pudimos cargar las notificaciones."
        onMarkAsRead={vi.fn()}
        onRefresh={onRetry}
      />
    );

    await user.click(screen.getByRole('button', { name: /notificaciones/i }));

    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('No pudimos cargar las notificaciones.');
    expect(screen.queryByText('No hay notificaciones disponibles.')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /reintentar/i }));
    expect(onRetry).toHaveBeenCalledTimes(2); // 1 on open + 1 on retry
  });

  it('provides an explicit close button that restores focus and closes when focus leaves the panel via Tab (T046 P1-2)', async () => {
    const user = userEvent.setup();

    render(
      <div>
        <AdminNotificationBell
          notifications={sampleNotifications}
          unreadCount={1}
          onMarkAsRead={vi.fn()}
        />
        <button type="button">Siguiente control</button>
      </div>
    );

    const trigger = screen.getByRole('button', { name: /notificaciones/i });
    await user.click(trigger);

    const closeBtn = screen.getByRole('button', { name: /cerrar notificaciones/i });
    await user.click(closeBtn);
    expect(screen.queryByTestId('notifications-dropdown')).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();

    // Reopen and move focus outside via blur
    await user.click(trigger);
    const panel = screen.getByTestId('notifications-dropdown');
    expect(panel).toBeInTheDocument();

    const outsideButton = screen.getByRole('button', { name: 'Siguiente control' });
    fireEvent.blur(panel, { relatedTarget: outsideButton });
    expect(screen.queryByTestId('notifications-dropdown')).not.toBeInTheDocument();
  });

  it('keeps existing notifications visible when an inline error banner is displayed (T046 P1-3)', async () => {
    const user = userEvent.setup();

    render(
      <AdminNotificationBell
        notifications={sampleNotifications}
        unreadCount={1}
        error="No pudimos marcar la notificación como leída. Intente nuevamente."
        onMarkAsRead={vi.fn()}
        onRefresh={vi.fn()}
      />
    );

    await user.click(screen.getByRole('button', { name: /notificaciones/i }));

    expect(screen.getByRole('alert')).toHaveTextContent(
      'No pudimos marcar la notificación como leída. Intente nuevamente.'
    );
    expect(screen.getByText('Nuevo plato en revisión')).toBeInTheDocument();
    expect(screen.getByText('Stock actualizado')).toBeInTheDocument();
  });
});
