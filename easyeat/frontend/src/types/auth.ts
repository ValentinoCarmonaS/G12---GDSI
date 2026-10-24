export type Role = 'COMENSAL' | 'ENCARGADO' | 'CHEF' | 'ADMINISTRADOR';

export interface AuthenticatedUser {
  id: string;
  email: string;
  role: Role;
  tenantId: string | null;
  tokenVersion: number;
}

export interface JwtClaims {
  sub: string;
  email: string;
  role: Role;
  tenant_id: string | null;
  token_version: number;
  iat: number;
  exp: number;
}

export interface LoginCredentials {
  email: string;
  password: string;
}

export interface RegisterCredentials {
  email: string;
  password: string;
}

export interface RegisterResponse {
  message: string;
}

export interface PasswordResetRequest {
  email: string;
}

export interface PasswordResetConfirm {
  token: string;
  new_password: string;
}

export interface PasswordResetResponse {
  message: string;
}

export interface LoginResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
}

export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  timestamp?: string;
}
