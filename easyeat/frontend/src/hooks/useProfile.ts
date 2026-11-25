import { useState, useEffect, useCallback } from 'react';
import { profileApi } from '../api/profileApi';
import { useClientCache } from '../context/ClientCacheContext';
import type {
  ProfileResponse,
  PersonalDataRequest,
  DemographicsRequest,
  AvatarUploadResponse,
  EmailChangeInitiatedResponse,
  DietaryRestrictionItem,
} from '../types/profile';

export interface UseProfileOptions {
  enabled?: boolean;
  loadTaxonomy?: boolean;
}

const PROFILE_CACHE_KEY = 'profile:me';
const TAXONOMY_CACHE_KEY = 'taxonomy:dietary';

export function useProfile(options: UseProfileOptions = {}) {
  const { enabled = true, loadTaxonomy = true } = options;
  const cache = useClientCache();

  const cachedProfile = enabled ? cache.get<ProfileResponse>(PROFILE_CACHE_KEY)?.data ?? null : null;
  const cachedTaxonomy =
    enabled && loadTaxonomy ? cache.get<DietaryRestrictionItem[]>(TAXONOMY_CACHE_KEY)?.data ?? [] : [];

  const [profile, setProfile] = useState<ProfileResponse | null>(cachedProfile);
  const [taxonomy, setTaxonomy] = useState<DietaryRestrictionItem[]>(cachedTaxonomy);
  const [loading, setLoading] = useState<boolean>(enabled && !cachedProfile);
  const [taxonomyLoading, setTaxonomyLoading] = useState<boolean>(enabled && loadTaxonomy && cachedTaxonomy.length === 0);
  const [error, setError] = useState<string | null>(null);
  const [cooldownSeconds, setCooldownSeconds] = useState<number>(0);

  useEffect(() => {
    const unsubProfile = cache.subscribe(PROFILE_CACHE_KEY, () => {
      const entry = cache.get<ProfileResponse>(PROFILE_CACHE_KEY);
      setProfile(entry?.data ?? null);
    });
    const unsubTaxonomy = cache.subscribe(TAXONOMY_CACHE_KEY, () => {
      const entry = cache.get<DietaryRestrictionItem[]>(TAXONOMY_CACHE_KEY);
      setTaxonomy(entry?.data ?? []);
    });
    return () => {
      unsubProfile();
      unsubTaxonomy();
    };
  }, [cache]);

  const fetchProfile = useCallback(async (forceRefresh = false) => {
    const existing = cache.get<ProfileResponse>(PROFILE_CACHE_KEY)?.data;
    if (!existing) {
      setLoading(true);
    }
    setError(null);
    if (forceRefresh) {
      cache.invalidate(PROFILE_CACHE_KEY);
    }
    try {
      const data = await cache.fetchDeduped(PROFILE_CACHE_KEY, () => profileApi.getProfile());
      setProfile(data);
    } catch (err: any) {
      if (!cache.get<ProfileResponse>(PROFILE_CACHE_KEY)?.data) {
        const msg = err.response?.data?.detail || err.message || 'Error al cargar perfil';
        setError(msg);
      }
    } finally {
      setLoading(false);
    }
  }, [cache]);

  const fetchTaxonomy = useCallback(async () => {
    const existing = cache.get<DietaryRestrictionItem[]>(TAXONOMY_CACHE_KEY)?.data;
    if (!existing) {
      setTaxonomyLoading(true);
    }
    try {
      const data = await cache.fetchDeduped(TAXONOMY_CACHE_KEY, () => profileApi.getDietaryTaxonomy());
      setTaxonomy(data);
    } catch (err: any) {
      console.error('Error al cargar taxonomía dietaria', err);
    } finally {
      setTaxonomyLoading(false);
    }
  }, [cache]);

  useEffect(() => {
    if (!enabled) {
      setLoading(false);
      setTaxonomyLoading(false);
      return;
    }
    fetchProfile();
    if (loadTaxonomy) {
      fetchTaxonomy();
    } else {
      setTaxonomyLoading(false);
    }
  }, [enabled, loadTaxonomy, fetchProfile, fetchTaxonomy]);

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
      setProfile((prev) => {
        const merged = prev ? { ...prev, ...updated } : updated;
        cache.set(PROFILE_CACHE_KEY, merged);
        return merged;
      });
      return updated;
    } catch (err: any) {
      const msg = err.response?.data?.detail || err.message || 'Error al actualizar datos personales';
      setError(msg);
      throw err;
    }
  };

  const updateDemographics = async (data: DemographicsRequest): Promise<ProfileResponse> => {
    setError(null);
    try {
      const updated = await profileApi.updateDemographics(data);
      setProfile((prev) => {
        const merged = prev ? { ...prev, ...updated } : updated;
        cache.set(PROFILE_CACHE_KEY, merged);
        return merged;
      });
      return updated;
    } catch (err: any) {
      const msg = err.response?.data?.detail || err.message || 'Error al actualizar datos demográficos';
      setError(msg);
      throw err;
    }
  };

  const uploadAvatar = async (file: File): Promise<AvatarUploadResponse> => {
    setError(null);
    try {
      const response = await profileApi.uploadAvatar(file);
      setProfile((prev) => {
        const merged = prev ? { ...prev, avatarUrl: response.avatarUrl } : null;
        if (merged) cache.set(PROFILE_CACHE_KEY, merged);
        return merged;
      });
      await fetchProfile(true);
      return response;
    } catch (err: any) {
      const msg = err.response?.data?.detail || err.message || 'Error al subir foto de perfil';
      setError(msg);
      throw err;
    }
  };

  const removeAvatar = async (): Promise<void> => {
    setError(null);
    try {
      await profileApi.removeAvatar();
      setProfile((prev) => {
        const merged = prev ? { ...prev, avatarUrl: null } : null;
        if (merged) cache.set(PROFILE_CACHE_KEY, merged);
        return merged;
      });
      await fetchProfile(true);
    } catch (err: any) {
      const msg = err.response?.data?.detail || err.message || 'Error al eliminar foto de perfil';
      setError(msg);
      throw err;
    }
  };

  const updateDietaryRestrictions = async (restrictionCodes: string[]): Promise<DietaryRestrictionItem[]> => {
    setError(null);
    try {
      const updated = await profileApi.updateDietaryRestrictions(restrictionCodes);
      setProfile((prev) => {
        const merged = prev ? { ...prev, dietaryRestrictions: updated } : null;
        if (merged) cache.set(PROFILE_CACHE_KEY, merged);
        return merged;
      });
      cache.invalidate('catalog:', { prefix: true });
      return updated;
    } catch (err: any) {
      const msg = err.response?.data?.detail || err.message || 'Error al actualizar preferencias dietarias';
      setError(msg);
      throw err;
    }
  };

  const requestEmailChange = async (newEmail: string): Promise<EmailChangeInitiatedResponse> => {
    setError(null);
    try {
      const response = await profileApi.requestEmailChange({ newEmail });
      setCooldownSeconds(60);
      await fetchProfile(true);
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
      await fetchProfile(true);
    } catch (err: any) {
      const msg = err.response?.data?.detail || err.message || 'Error al cancelar solicitud';
      setError(msg);
      throw err;
    }
  };

  return {
    profile,
    taxonomy,
    loading,
    taxonomyLoading,
    error,
    cooldownSeconds,
    setCooldownSeconds,
    refreshProfile: () => fetchProfile(true),
    refreshTaxonomy: fetchTaxonomy,
    updatePersonalData,
    updateDemographics,
    uploadAvatar,
    removeAvatar,
    updateDietaryRestrictions,
    requestEmailChange,
    resendVerification,
    cancelEmailChange,
  };
}

export default useProfile;
