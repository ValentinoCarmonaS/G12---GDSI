import React from 'react';
import { AlertCircle, AlertTriangle, Info } from 'lucide-react';
import { FeedbackState, type FeedbackVariant } from '../primitives/FeedbackState';

interface PaymentAlertBannerProps {
  type: 'error' | 'warning' | 'info';
  message: string;
  onClose?: () => void;
}

const VARIANT_MAP: Record<PaymentAlertBannerProps['type'], FeedbackVariant> = {
  error: 'danger',
  warning: 'warning',
  info: 'info',
};

export const PaymentAlertBanner: React.FC<PaymentAlertBannerProps> = ({
  type,
  message,
  onClose,
}) => {
  if (!message) return null;

  const icon =
    type === 'error' ? (
      <AlertCircle className="w-5 h-5" />
    ) : type === 'warning' ? (
      <AlertTriangle className="w-5 h-5" />
    ) : (
      <Info className="w-5 h-5" />
    );

  return (
    <FeedbackState
      variant={VARIANT_MAP[type]}
      role="alert"
      icon={icon}
      message={message}
      onDismiss={onClose}
      dismissLabel="Cerrar alerta"
    />
  );
};
