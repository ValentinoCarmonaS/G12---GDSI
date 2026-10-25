import React, { useState, useEffect } from 'react';
import { Mail } from 'lucide-react';

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
    <div className="bg-amber-50 border border-amber-200/80 rounded-xl p-5 mb-6 shadow-sm">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex items-start gap-3.5">
          <div className="p-2 bg-amber-100 rounded-lg text-amber-700 mt-0.5">
            <Mail className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-amber-200/80 text-amber-900">
                Verificación pendiente
              </span>
              <span className="text-xs text-amber-700">Enlace válido por 24 horas</span>
            </div>
            <p className="text-sm font-medium text-amber-950 mt-1">
              Hemos enviado un enlace de confirmación a: <span className="font-semibold underline">{pendingEmail}</span>
            </p>
            <p className="text-xs text-amber-800 mt-0.5">
              Tu correo actual seguirá activo para iniciar sesión hasta que confirmes la nueva dirección.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2.5 sm:self-center shrink-0">
          <button
            type="button"
            onClick={handleResend}
            disabled={cooldown > 0 || isResending}
            className="px-3.5 py-2 text-xs font-semibold rounded-lg text-white bg-amber-600 hover:bg-amber-700 disabled:opacity-50 disabled:bg-amber-400 disabled:cursor-not-allowed transition-colors shadow-sm"
          >
            {cooldown > 0 ? `Reenviar en ${cooldown}s` : isResending ? 'Enviando...' : 'Reenviar enlace'}
          </button>
          <button
            type="button"
            onClick={handleCancel}
            disabled={isCanceling}
            className="px-3.5 py-2 text-xs font-medium rounded-lg text-neutral-700 bg-white border border-neutral-300 hover:bg-neutral-50 transition-colors disabled:opacity-50"
          >
            {isCanceling ? 'Cancelando...' : 'Cancelar solicitud'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default PendingEmailBanner;
