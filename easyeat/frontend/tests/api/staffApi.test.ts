import { describe, it, expect, beforeEach, vi } from 'vitest';
import { staffApi } from '@/api/staffApi';
import { apiClient } from '@/api/apiClient';
import type {
  StaffMemberResponse,
  StaffRegistrationRequest,
  StaffRegistrationResponse,
  StaffUpdateRequest,
} from '@/types/staff';

describe('staffApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('calls GET /admin/staff and returns list of staff members (FR-004, FR-028)', async () => {
    const mockStaff: StaffMemberResponse[] = [
      {
        id: 'user-uuid-1',
        fullName: 'Carlos Gómez',
        email: 'carlos.chef@laesquina.com',
        role: 'CHEF',
        status: 'ACTIVE',
        createdAt: '2026-10-24T12:00:00Z',
        updatedAt: '2026-10-24T12:00:00Z',
      },
    ];

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce({
      data: mockStaff,
    });

    const result = await staffApi.listStaff();

    expect(getSpy).toHaveBeenCalledWith('/admin/staff');
    expect(result).toEqual(mockStaff);
  });

  it('calls POST /admin/staff with registration request and returns temporary credential (FR-001, FR-003, FR-029)', async () => {
    const request: StaffRegistrationRequest = {
      fullName: 'Laura Martínez',
      email: 'laura.encargada@laesquina.com',
      role: 'ENCARGADO',
    };

    const mockResponse: StaffRegistrationResponse = {
      id: 'new-user-uuid',
      fullName: 'Laura Martínez',
      email: 'laura.encargada@laesquina.com',
      role: 'ENCARGADO',
      status: 'PENDING_FIRST_LOGIN',
      temporaryCredential: 'TempPass123!@#',
      createdAt: '2026-10-24T12:05:00Z',
    };

    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce({
      data: mockResponse,
    });

    const result = await staffApi.createStaff(request);

    expect(postSpy).toHaveBeenCalledWith('/admin/staff', request);
    expect(result).toEqual(mockResponse);
  });

  it('calls PUT /admin/staff/{id} with update request and returns updated staff member (FR-005)', async () => {
    const updateRequest: StaffUpdateRequest = {
      fullName: 'Carlos Gómez Actualizado',
      email: 'carlos.chef@laesquina.com',
      role: 'CHEF',
    };

    const mockResponse: StaffMemberResponse = {
      id: 'user-uuid-1',
      fullName: 'Carlos Gómez Actualizado',
      email: 'carlos.chef@laesquina.com',
      role: 'CHEF',
      status: 'ACTIVE',
      createdAt: '2026-10-24T12:00:00Z',
      updatedAt: '2026-10-24T12:10:00Z',
    };

    const putSpy = vi.spyOn(apiClient, 'put').mockResolvedValueOnce({
      data: mockResponse,
    });

    const result = await staffApi.updateStaff('user-uuid-1', updateRequest);

    expect(putSpy).toHaveBeenCalledWith('/admin/staff/user-uuid-1', updateRequest);
    expect(result).toEqual(mockResponse);
  });
});

