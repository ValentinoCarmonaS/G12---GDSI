import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Store, AlertOctagon, ArrowLeft } from 'lucide-react';
import { BentoCard } from '@/components/diner/BentoCard';
import { Button } from '@/components/ui/Button';
import { StatusBadge } from '@/components/ui/StatusBadge';

export interface EstablishmentBlockingNoticeProps {
  establishmentName?: string;
  status?: string;
  onBackToSearch?: () => void;
}

export const EstablishmentBlockingNotice: React.FC<EstablishmentBlockingNoticeProps> = ({
  establishmentName,
  status,
  onBackToSearch,
}) => {
  const navigate = useNavigate();

  const handleBack = () => {
    if (onBackToSearch) {
      onBackToSearch();
    } else {
      navigate('/descubrimiento');
    }
  };

  return (
    <div
      role="region"
      aria-label="Aviso de inactividad de establecimiento"
      className="min-h-screen flex items-center justify-center bg-[var(--ee-surface)] px-4 py-12"
    >
      <BentoCard variant="elevated" padding="lg" radius="lg" className="max-w-md w-full text-center">
        <div className="relative mx-auto w-20 h-20 mb-6 flex items-center justify-center bg-[var(--ee-danger-bg)] border border-[var(--ee-danger-border)] rounded-[var(--ee-radius-lg)]">
          <Store className="w-10 h-10 text-[var(--ee-danger-fg)]" aria-hidden="true" />
          <div className="absolute -bottom-1 -right-1 p-1.5 bg-[var(--ee-danger-solid)] text-[var(--ee-on-danger)] rounded-full">
            <AlertOctagon className="w-4 h-4" aria-hidden="true" />
          </div>
        </div>

        <h2 className="text-2xl font-bold text-[var(--ee-text)] mb-2">
          Establecimiento inactivo
        </h2>

        {establishmentName && (
          <p className="text-base font-semibold text-[var(--ee-primary-border)] mb-3">
            {establishmentName}
          </p>
        )}

        <p className="text-sm text-[var(--ee-text-secondary)] leading-relaxed mb-6">
          Este establecimiento se encuentra temporalmente inactivo y no está recibiendo pedidos
          en este momento.
        </p>

        {status && (
          <div className="mb-6 flex justify-center">
            <StatusBadge tone="danger" label={`Estado: ${status}`} />
          </div>
        )}

        <Button
          type="button"
          variant="primary"
          onClick={handleBack}
          className="w-full min-h-[44px]"
        >
          <ArrowLeft className="w-4 h-4" aria-hidden="true" />
          <span>Volver a buscar restaurantes</span>
        </Button>
      </BentoCard>
    </div>
  );
};
