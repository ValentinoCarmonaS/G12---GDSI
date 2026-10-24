export type StaffRole = 'ADMINISTRADOR' | 'CHEF' | 'ENCARGADO';

export type StaffAccountStatus = 'PENDING_FIRST_LOGIN' | 'ACTIVE' | 'SUSPENDED';

export interface StaffRegistrationRequest {
  fullName: string;
  email: string;
  role: StaffRole;
}

export interface StaffRegistrationResponse {
  id: string;
  fullName: string;
  email: string;
  role: StaffRole;
  status: StaffAccountStatus;
  temporaryCredential: string;
  createdAt: string;
}

export interface StaffUpdateRequest {
  fullName: string;
  email: string;
  role: StaffRole;
}

export interface StaffMemberResponse {
  id: string;
  fullName: string;
  email: string;
  role: StaffRole;
  status: StaffAccountStatus;
  createdAt: string;
  updatedAt: string;
}

/**
 * Normalizes backend role names or Spring Security role identifiers to canonical frontend StaffRole.
 */
export function normalizeStaffRole(role: string): StaffRole {
  const clean = role.replace('ROLE_', '').toUpperCase();
  if (clean === 'ADMIN' || clean === 'ADMINISTRADOR') {
    return 'ADMINISTRADOR';
  }
  if (clean === 'CHEF') {
    return 'CHEF';
  }
  if (clean === 'COUNTER_CLERK' || clean === 'ENCARGADO') {
    return 'ENCARGADO';
  }
  return 'ENCARGADO';
}
