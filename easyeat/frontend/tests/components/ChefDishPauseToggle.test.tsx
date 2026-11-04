import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import ChefDishPauseToggle from '../../src/components/chef/ChefDishPauseToggle';

describe('ChefDishPauseToggle (T040, INV-16, SC-010)', () => {
  const defaultProps = {
    dishId: 'dish-123',
    initialPaused: false,
    onToggle: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders correctly with active state when initialPaused is false', () => {
    render(<ChefDishPauseToggle {...defaultProps} initialPaused={false} />);
    const toggleButton = screen.getByRole('switch', { name: /pausa/i });
    expect(toggleButton).toBeInTheDocument();
    expect(toggleButton).toHaveAttribute('aria-checked', 'false');
  });

  it('renders correctly with paused state when initialPaused is true', () => {
    render(<ChefDishPauseToggle {...defaultProps} initialPaused={true} />);
    const toggleButton = screen.getByRole('switch', { name: /pausa/i });
    expect(toggleButton).toHaveAttribute('aria-checked', 'true');
  });

  it('triggers optimistic update and calls onToggle callback upon click', async () => {
    const onToggleMock = vi.fn().mockResolvedValue(true);
    render(<ChefDishPauseToggle {...defaultProps} onToggle={onToggleMock} />);

    const toggleButton = screen.getByRole('switch', { name: /pausa/i });
    fireEvent.click(toggleButton);

    expect(toggleButton).toHaveAttribute('aria-checked', 'true');
    expect(onToggleMock).toHaveBeenCalledWith('dish-123', true);
  });

  it('reverts optimistic update if onToggle fails', async () => {
    const onToggleMock = vi.fn().mockRejectedValue(new Error('Network error'));
    render(<ChefDishPauseToggle {...defaultProps} onToggle={onToggleMock} />);

    const toggleButton = screen.getByRole('switch', { name: /pausa/i });
    fireEvent.click(toggleButton);

    // Initial optimistic flip
    expect(toggleButton).toHaveAttribute('aria-checked', 'true');

    // Rollback after error
    await waitFor(() => {
      expect(toggleButton).toHaveAttribute('aria-checked', 'false');
    });
  });
});
