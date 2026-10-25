import { useState, useEffect, useCallback } from 'react';
import { profileApi } from '../api/profileApi';
import type {
  ProfileResponse,
  PersonalDataRequest,
  EmailChangeInitiatedResponse,
} from '../types/profile';

export function useProfile() {
  const [profile, setProfile] = useState<ProfileResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [cooldownSeconds, setCooldownSeconds] = useState<number>(0);

  const fetchProfile = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await profileApi.getProfile();
      setProfile(data);
    } catch (err: any) {
      const msg = err.response?.data?.detail || err.message || 'Error al cargar perfil';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchProfile();
  }, [fetchProfile]);

  useEffect(() => {
    if (cooldownSeconds <= 0) return;
    const interval = setInterval(() => {
      setCooldownSeconds((prev) => Math.max(0, prev - 1));
    }, 1000);
    return () => clearInterval(interval);
  }, [cooldownSeconds]);

  const updatePersonalData = async (data: PersonalDataRequest): Promise<ProfileResponse> => {
    setError(null);
    try {
      const updated = await profileApi.updatePersonalData(data);
      setProfile(updated);
      return updated;
    } catch (err: any) {
      const msg = err.response?.data?.detail || err.message || 'Error al actualizar datos personales';
      setError(msg);
      throw err;
    }
  };

  const requestEmailChange = async (newEmail: string): Promise<EmailChangeInitiatedResponse> => {
    setError(null);
    try {
      const response = await profileApi.requestEmailChange({ newEmail });
      setCooldownSeconds(60);
      await fetchProfile();
      return response;
    } catch (err: any) {
      const msg = err.response?.data?.detail || err.message || 'Error al solicitar cambio de email';
      setError(msg);
      throw err;
    }
  };

  const resendVerification = async (): Promise<EmailChangeInitiatedResponse> => {
    setError(null);
    try {
      const response = await profileApi.resendVerificationEmail();
      setCooldownSeconds(60);
      return response;
    } catch (err: any) {
      if (err.response?.status === 429) {
        const remaining = err.response?.data?.retryAfterSeconds || 60;
        setCooldownSeconds(remaining);
      }
      const msg = err.response?.data?.detail || err.message || 'Error al reenviar verificación';
      setError(msg);
      throw err;
    }
  };

  const cancelEmailChange = async (): Promise<void> => {
    setError(null);
    try {
      await profileApi.cancelPendingEmailChange();
      setCooldownSeconds(0);
      await fetchProfile();
    } catch (err: any) {
      const msg = err.response?.data?.detail || err.message || 'Error al cancelar solicitud';
      setError(msg);
      throw err;
    }
  };

  return {
    profile,
    loading,
    error,
    cooldownSeconds,
    setCooldownSeconds,
    refreshProfile: fetchProfile,
    updatePersonalData,
    requestEmailChange,
    resendVerification,
    cancelEmailChange,
  };
}

export default useProfile;
