import React from 'react';

export interface PendingEmailBannerProps {
  pendingEmail: string;
  onResend: () => Promise<void>;
  onCancel: () => Promise<void>;
  initialCooldownSeconds?: number;
}

export const PendingEmailBanner: React.FC<PendingEmailBannerProps> = () => {
  return <div>Stub PendingEmailBanner</div>;
};
