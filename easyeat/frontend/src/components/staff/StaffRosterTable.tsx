import React, { useState, useMemo } from 'react';
import { StaffMemberResponse, StaffRole, StaffAccountStatus } from '../../types/staff';
import { GlassCard } from '../admin/GlassCard';

export interface StaffRosterTableProps {
  staffMembers: StaffMemberResponse[];
  onEdit: (staff: StaffMemberResponse) => void;
  onToggleStatus?: (staff: StaffMemberResponse) => void;
  isLoading?: boolean;
}

const ROLE_LABELS: Record<StaffRole, string> = {
  ADMINISTRADOR: 'Administrador',
  CHEF: 'Chef',
  ENCARGADO: 'Encargado de Mostrador',
};

const ROLE_BADGE_CLASSES: Record<StaffRole, string> = {
  ADMINISTRADOR: 'bg-ee-primary/10 text-ee-primary border-ee-primary/25',
  CHEF: 'bg-amber-50 text-amber-800 border-amber-200',
  ENCARGADO: 'bg-sky-50 text-sky-800 border-sky-200',
};

const STATUS_LABELS: Record<StaffAccountStatus, string> = {
  PENDING_FIRST_LOGIN: 'Pendiente de primer login',
  ACTIVE: 'Activo',
  SUSPENDED: 'Suspendido',
};

const STATUS_BADGE_CLASSES: Record<StaffAccountStatus, string> = {
  PENDING_FIRST_LOGIN: 'bg-amber-50 text-amber-800 border-amber-200',
  ACTIVE: 'bg-emerald-50 text-emerald-800 border-emerald-200',
  SUSPENDED: 'bg-rose-50 text-rose-800 border-rose-200',
};

export const StaffRosterTable: React.FC<StaffRosterTableProps> = ({
  staffMembers,
  onEdit,
  onToggleStatus,
  isLoading = false,
}) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [roleFilter, setRoleFilter] = useState<string>('ALL');

  const filteredMembers = useMemo(() => {
    return staffMembers.filter((member) => {
      const matchesSearch =
        member.fullName.toLowerCase().includes(searchTerm.toLowerCase()) ||
        member.email.toLowerCase().includes(searchTerm.toLowerCase());
      const matchesRole = roleFilter === 'ALL' || member.role === roleFilter;
      return matchesSearch && matchesRole;
    });
  }, [staffMembers, searchTerm, roleFilter]);

  return (
    <div className="space-y-4">
      <GlassCard variant="subtle" padding="md" className="flex flex-col sm:flex-row gap-3 items-stretch sm:items-center justify-between">
        <div className="w-full sm:w-80">
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Buscar por nombre o email..."
            aria-label="Buscar por nombre o email"
            className="w-full min-h-[44px] px-3 py-2 border border-ee-border-subtle rounded-ee-sm text-sm text-ee-text placeholder-ee-text-muted focus:outline-none focus:ring-2 focus:ring-ee-focus-ring bg-white/90"
          />
        </div>

        <div className="flex items-center gap-2 w-full sm:w-auto">
          <label htmlFor="roleFilterSelect" className="text-xs text-ee-text-muted font-medium whitespace-nowrap">
            Filtrar por rol:
          </label>
          <select
            id="roleFilterSelect"
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value)}
            className="min-h-[44px] px-3 py-2 border border-ee-border-subtle rounded-ee-sm text-sm text-ee-text bg-white/90 focus:outline-none focus:ring-2 focus:ring-ee-focus-ring"
          >
            <option value="ALL">Todos los roles</option>
            <option value="ADMINISTRADOR">Administrador</option>
            <option value="CHEF">Chef</option>
            <option value="ENCARGADO">Encargado de Mostrador</option>
          </select>
        </div>
      </GlassCard>

      {isLoading ? (
        <GlassCard variant="elevated" padding="lg" className="text-center text-sm text-ee-text-muted">
          Cargando nómina de empleados...
        </GlassCard>
      ) : filteredMembers.length === 0 ? (
        <GlassCard variant="elevated" padding="lg" className="text-center text-sm text-ee-text-muted">
          No se encontraron empleados registrados.
        </GlassCard>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {filteredMembers.map((member) => {
            const isSuspended = member.status === 'SUSPENDED';
            return (
              <GlassCard
                key={member.id}
                variant="interactive"
                padding="md"
                as="article"
                className="flex flex-col justify-between gap-4"
              >
                <div className="space-y-3">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <h3 className="text-base font-semibold text-ee-text truncate">
                        {member.fullName}
                      </h3>
                      <p className="text-xs text-ee-text-muted truncate">{member.email}</p>
                    </div>
                  </div>

                  <div className="flex flex-wrap items-center gap-2">
                    <span
                      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border ${
                        ROLE_BADGE_CLASSES[member.role] || 'bg-slate-100 text-slate-800 border-slate-200'
                      }`}
                    >
                      {ROLE_LABELS[member.role] || member.role}
                    </span>
                    <span
                      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border ${
                        STATUS_BADGE_CLASSES[member.status] || 'bg-slate-100 text-slate-800 border-slate-200'
                      }`}
                    >
                      {STATUS_LABELS[member.status] || member.status}
                    </span>
                  </div>
                </div>

                <div className="flex flex-wrap items-center justify-end gap-2 pt-3 border-t border-ee-border-subtle">
                  <button
                    type="button"
                    onClick={() => onEdit(member)}
                    className="min-h-[44px] px-3.5 py-2 text-xs font-semibold text-ee-primary bg-ee-primary/5 hover:bg-ee-primary/10 rounded-ee-sm border border-ee-primary/25 transition-colors focus:outline-none focus:ring-2 focus:ring-ee-focus-ring"
                  >
                    Editar
                  </button>
                  {onToggleStatus && (
                    <button
                      type="button"
                      onClick={() => onToggleStatus(member)}
                      className={`min-h-[44px] px-3.5 py-2 text-xs font-semibold rounded-ee-sm border transition-colors focus:outline-none focus:ring-2 focus:ring-ee-focus-ring ${
                        isSuspended
                          ? 'text-emerald-800 bg-emerald-50 hover:bg-emerald-100 border-emerald-200'
                          : 'text-rose-800 bg-rose-50 hover:bg-rose-100 border-rose-200'
                      }`}
                    >
                      {isSuspended ? 'Reactivar' : 'Suspender'}
                    </button>
                  )}
                </div>
              </GlassCard>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default StaffRosterTable;
