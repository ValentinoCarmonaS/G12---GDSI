import { apiClient } from './apiClient';
import type {
  StaffMemberResponse,
  StaffRegistrationRequest,
  StaffRegistrationResponse,
  StaffUpdateRequest,
} from '../types/staff';

export const staffApi = {
  /**
   * Retrieves the full staff roster for the administrator's establishment.
   */
  async listStaff(): Promise<StaffMemberResponse[]> {
    const response = await apiClient.get<StaffMemberResponse[]>('/admin/staff');
    return response.data;
  },

  /**
   * Registers a new staff member, returning the one-time temporary credential.
   */
  async createStaff(request: StaffRegistrationRequest): Promise<StaffRegistrationResponse> {
    const response = await apiClient.post<StaffRegistrationResponse>('/admin/staff', request);
    return response.data;
  },

  /**
   * Updates an existing staff member's profile and assigned role.
   */
  async updateStaff(id: string, request: StaffUpdateRequest): Promise<StaffMemberResponse> {
    const response = await apiClient.put<StaffMemberResponse>(`/admin/staff/${id}`, request);
    return response.data;
  },
};
