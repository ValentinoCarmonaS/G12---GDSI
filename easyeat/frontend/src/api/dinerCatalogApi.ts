import { EstablishmentSummary } from '@/types/dinerCatalog';

export const dinerCatalogApi = {
  searchEstablishments: async (_query: string): Promise<EstablishmentSummary[]> => {
    throw new Error('TDD RED: not implemented');
  },
};
