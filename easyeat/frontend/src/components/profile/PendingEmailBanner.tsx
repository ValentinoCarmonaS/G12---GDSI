import React, { useState, useEffect } from 'react';
import { Mail } from 'lucide-react';
import { BentoCard } from '../diner/BentoCard';
import { Button } from '../primitives/Button';
import { StatusBadge } from '../primitives/StatusBadge';

export interface PendingEmailBannerProps {
  pendingEmail: string;
  onResend: () => Promise<void>;
  onCancel: () => Promise<void>;
  initialCooldownSeconds?: number;
}

export const PendingEmailBanner: React.FC<PendingEmailBannerProps> = ({
  pendingEmail,
  onResend,
  onCancel,
  initialCooldownSeconds = 0,
}) => {
  const [cooldown, setCooldown] = useState<number>(initialCooldownSeconds);
  const [isResending, setIsResending] = useState<boolean>(false);
  const [isCanceling, setIsCanceling] = useState<boolean>(false);

  useEffect(() => {
    setCooldown(initialCooldownSeconds);
  }, [initialCooldownSeconds]);

  useEffect(() => {
    if (cooldown <= 0) return;
    const interval = setInterval(() => {
      setCooldown((prev) => Math.max(0, prev - 1));
    }, 1000);
    return () => clearInterval(interval);
  }, [cooldown]);

  const handleResend = async () => {
    if (cooldown > 0 || isResending) return;
    setIsResending(true);
    try {
      await onResend();
      setCooldown(60);
    } finally {
      setIsResending(false);
    }
  };

  const handleCancel = async () => {
    if (isCanceling) return;
    setIsCanceling(true);
    try {
      await onCancel();
    } finally {
      setIsCanceling(false);
    }
  };

  return (
    <BentoCard
      variant="subtle"
      padding="md"
      className="border-ee-warning-border bg-ee-warning-bg"
      role="region"
      aria-label="Verificación de correo pendiente"
    >
      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
        <div className="flex items-start gap-3.5">
          <div className="mt-0.5 rounded-ee-sm border border-ee-warning-border bg-ee-surface-elevated p-2 text-ee-warning-text">
            <Mail className="h-5 w-5" aria-hidden="true" />
          </div>
          <div className="space-y-1">
            <div className="flex flex-wrap items-center gap-2">
              <StatusBadge tone="warning" label="Verificación pendiente" />
              <span className="text-xs font-medium text-ee-warning-text">
                Enlace válido por 24 horas
              </span>
            </div>
            <p className="text-sm font-medium text-ee-text">
              Hemos enviado un enlace de confirmación a:{' '}
              <span className="font-semibold underline">{pendingEmail}</span>
            </p>
            <p className="text-xs text-ee-text-secondary">
              Tu correo actual seguirá activo para iniciar sesión hasta que confirmes la nueva dirección.
            </p>
          </div>
        </div>

        <div className="flex shrink-0 flex-wrap items-center gap-2.5 sm:self-center">
          <Button
            type="button"
            variant="primary"
            size="sm"
            onClick={handleResend}
            disabled={cooldown > 0 || isResending}
            className="min-h-[44px]"
          >
            {cooldown > 0 ? `Reenviar en ${cooldown}s` : isResending ? 'Enviando...' : 'Reenviar enlace'}
          </Button>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={handleCancel}
            disabled={isCanceling}
            className="min-h-[44px]"
          >
            {isCanceling ? 'Cancelando...' : 'Cancelar solicitud'}
          </Button>
        </div>
      </div>
    </BentoCard>
  );
};

export default PendingEmailBanner;
