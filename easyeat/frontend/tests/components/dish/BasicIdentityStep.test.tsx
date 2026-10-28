import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BasicIdentityStep } from '@/components/dish/BasicIdentityStep';
import { DISH_CATEGORIES } from '@/types/dish';

describe('BasicIdentityStep Component (T023, CHF-2.2.1)', () => {
  const mockOnSave = vi.fn();
  const mockOnPhotoDelete = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders all required form fields, closed categories, and initial character counters', () => {
    render(<BasicIdentityStep onSave={mockOnSave} />);

    expect(screen.getByLabelText(/nombre del plato/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/categoría/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/descripción/i)).toBeInTheDocument();
    expect(screen.getByText(/0\/100/)).toBeInTheDocument();
    expect(screen.getByText(/0\/500/)).toBeInTheDocument();

    const categorySelect = screen.getByLabelText(/categoría/i);
    expect(categorySelect).toBeInTheDocument();
    DISH_CATEGORIES.forEach((category) => {
      expect(screen.getByRole('option', { name: category })).toBeInTheDocument();
    });

    expect(screen.getByRole('button', { name: /guardar/i })).toBeInTheDocument();
  });

  it('populates initial data when editing an existing draft', () => {
    render(
      <BasicIdentityStep
        initialData={{
          name: 'Risotto de Hongos',
          category: 'Platos principales',
          description: 'Cremoso arroz con hongos',
          photoUrl: '/api/v1/dishes/123/photo',
        }}
        onSave={mockOnSave}
        onPhotoDelete={mockOnPhotoDelete}
      />
    );

    expect(screen.getByDisplayValue('Risotto de Hongos')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Platos principales')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Cremoso arroz con hongos')).toBeInTheDocument();
    expect(screen.getByText(/17\/100/)).toBeInTheDocument();
    expect(screen.getByText(/24\/500/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /eliminar foto/i })).toBeInTheDocument();
  });

  it('updates character counters reactively on user typing', async () => {
    const user = userEvent.setup();
    render(<BasicIdentityStep onSave={mockOnSave} />);

    const nameInput = screen.getByLabelText(/nombre del plato/i);
    await user.type(nameInput, 'Ceviche Mixto');

    expect(screen.getByText(/13\/100/)).toBeInTheDocument();

    const descInput = screen.getByLabelText(/descripción/i);
    await user.type(descInput, 'Con lima y cilantro');

    expect(screen.getByText(/19\/500/)).toBeInTheDocument();
  });

  it('shows error when dish name is submitted blank or contains only whitespace', async () => {
    const user = userEvent.setup();
    render(<BasicIdentityStep onSave={mockOnSave} />);

    const saveButton = screen.getByRole('button', { name: /guardar/i });
    await user.click(saveButton);

    expect(screen.getByText(/el nombre del plato es obligatorio/i)).toBeInTheDocument();
    expect(mockOnSave).not.toHaveBeenCalled();
  });

  it('rejects photo upload when file exceeds 5 MB size limit', async () => {
    render(<BasicIdentityStep onSave={mockOnSave} />);

    // Create 5.5 MB mock file
    const oversizedFile = new File(['x'.repeat(5.5 * 1024 * 1024)], 'large.jpg', {
      type: 'image/jpeg',
    });

    const fileInput = screen.getByLabelText(/fotografía/i, { selector: 'input[type="file"]' });
    fireEvent.change(fileInput, { target: { files: [oversizedFile] } });

    await waitFor(() => {
      expect(screen.getByText(/no debe superar los 5 mb/i)).toBeInTheDocument();
    });

    expect(mockOnSave).not.toHaveBeenCalled();
  });

  it('rejects unsupported image format', async () => {
    render(<BasicIdentityStep onSave={mockOnSave} />);

    const invalidFile = new File(['plain text'], 'document.pdf', {
      type: 'application/pdf',
    });

    const fileInput = screen.getByLabelText(/fotografía/i, { selector: 'input[type="file"]' });
    fireEvent.change(fileInput, { target: { files: [invalidFile] } });

    await waitFor(() => {
      expect(screen.getByText(/formatos soportados: jpeg, png, webp/i)).toBeInTheDocument();
    });

    expect(mockOnSave).not.toHaveBeenCalled();
  });

  it('calls onPhotoDelete when user clicks remove photo button', async () => {
    const user = userEvent.setup();
    render(
      <BasicIdentityStep
        initialData={{
          name: 'Risotto',
          category: 'Platos principales',
          photoUrl: '/api/v1/dishes/123/photo',
        }}
        onSave={mockOnSave}
        onPhotoDelete={mockOnPhotoDelete}
      />
    );

    const deletePhotoButton = screen.getByRole('button', { name: /eliminar foto/i });
    await user.click(deletePhotoButton);

    expect(mockOnPhotoDelete).toHaveBeenCalledTimes(1);
  });

  it('submits trimmed dish name, category, description, and selected photo file on valid submission', async () => {
    const user = userEvent.setup();
    render(<BasicIdentityStep onSave={mockOnSave} />);

    const nameInput = screen.getByLabelText(/nombre del plato/i);
    await user.type(nameInput, '   Pastel de Papas Artesanal   ');

    const categorySelect = screen.getByLabelText(/categoría/i);
    await user.selectOptions(categorySelect, 'Platos principales');

    const descInput = screen.getByLabelText(/descripción/i);
    await user.type(descInput, 'Receta clásica de la abuela.');

    const photoFile = new File(['valid photo data'], 'pastel.jpg', { type: 'image/jpeg' });
    const fileInput = screen.getByLabelText(/fotografía/i, { selector: 'input[type="file"]' });
    fireEvent.change(fileInput, { target: { files: [photoFile] } });

    const saveButton = screen.getByRole('button', { name: /guardar/i });
    await user.click(saveButton);

    expect(mockOnSave).toHaveBeenCalledWith(
      {
        name: 'Pastel de Papas Artesanal',
        category: 'Platos principales',
        description: 'Receta clásica de la abuela.',
      },
      photoFile
    );
  });
});
