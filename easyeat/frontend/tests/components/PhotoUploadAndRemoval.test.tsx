import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PersonalDataForm } from '@/components/profile/PersonalDataForm';

describe('PhotoUploadAndRemoval Component Tests (T011, FR-024, FR-025, COM-1.4.2)', () => {
  const mockOnSubmit = vi.fn();
  const mockOnUploadAvatar = vi.fn();
  const mockOnRemoveAvatar = vi.fn();

  const sampleDataWithPhoto = {
    firstName: 'Valentino',
    lastName: 'Carmona',
    email: 'valentino@example.com',
    phone: '+54 9 11 2345-6789',
    avatarUrl: '/api/v1/profile/avatar',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('rejects file exceeding 5 MB client-side limit', async () => {
    const user = userEvent.setup();
    render(
      <PersonalDataForm
        initialData={sampleDataWithPhoto}
        onSubmit={mockOnSubmit}
        onUploadAvatar={mockOnUploadAvatar}
        onRemoveAvatar={mockOnRemoveAvatar}
      />
    );

    const oversizedFile = new File(['x'.repeat(100)], 'huge.jpg', { type: 'image/jpeg' });
    Object.defineProperty(oversizedFile, 'size', { value: 5 * 1024 * 1024 + 1 });

    const fileInput = screen.getByLabelText(/subir foto|cambiar foto/i);
    await user.upload(fileInput, oversizedFile);

    expect(await screen.findByText(/el archivo supera el tamaño máximo permitido de 5 mb/i)).toBeInTheDocument();
    expect(mockOnUploadAvatar).not.toHaveBeenCalled();
  });

  it('rejects file with unsupported MIME format', async () => {
    const user = userEvent.setup();
    render(
      <PersonalDataForm
        initialData={sampleDataWithPhoto}
        onSubmit={mockOnSubmit}
        onUploadAvatar={mockOnUploadAvatar}
        onRemoveAvatar={mockOnRemoveAvatar}
      />
    );

    const pdfFile = new File(['sample'], 'document.pdf', { type: 'application/pdf' });

    const fileInput = screen.getByLabelText(/subir foto|cambiar foto/i);
    await user.upload(fileInput, pdfFile);

    expect(await screen.findByText(/formato no soportado/i)).toBeInTheDocument();
    expect(mockOnUploadAvatar).not.toHaveBeenCalled();
  });

  it('opens confirmation modal on delete photo click and cancels removal when dismissed', async () => {
    const user = userEvent.setup();
    render(
      <PersonalDataForm
        initialData={sampleDataWithPhoto}
        onSubmit={mockOnSubmit}
        onUploadAvatar={mockOnUploadAvatar}
        onRemoveAvatar={mockOnRemoveAvatar}
      />
    );

    const deleteBtn = screen.getByRole('button', { name: /eliminar foto/i });
    await user.click(deleteBtn);

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText(/¿desea eliminar su foto de perfil\?/i)).toBeInTheDocument();

    const cancelModalBtn = screen.getByRole('button', { name: /cancelar/i });
    await user.click(cancelModalBtn);

    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
    expect(mockOnRemoveAvatar).not.toHaveBeenCalled();
  });

  it('defers avatar removal until form submission upon modal confirmation', async () => {
    const user = userEvent.setup();
    render(
      <PersonalDataForm
        initialData={sampleDataWithPhoto}
        onSubmit={mockOnSubmit}
        onUploadAvatar={mockOnUploadAvatar}
        onRemoveAvatar={mockOnRemoveAvatar}
      />
    );

    // 1. Click delete photo button
    const deleteBtn = screen.getByRole('button', { name: /eliminar foto/i });
    await user.click(deleteBtn);

    // 2. Confirm in modal
    const confirmModalBtn = screen.getByRole('button', { name: /confirmar|eliminar/i });
    await user.click(confirmModalBtn);

    // 3. Deletion is provisional (not immediately called to backend)
    expect(mockOnRemoveAvatar).not.toHaveBeenCalled();

    // 4. Save form changes
    const submitBtn = screen.getByRole('button', { name: /guardar cambios/i });
    await user.click(submitBtn);

    // 5. Consolidated execution
    expect(mockOnRemoveAvatar).toHaveBeenCalledTimes(1);
    expect(mockOnSubmit).toHaveBeenCalled();
  });
});
