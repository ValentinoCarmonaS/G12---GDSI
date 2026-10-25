import React, { useState, useMemo } from 'react';
import { StaffMemberResponse, StaffRole, StaffAccountStatus } from '../../types/staff';

export interface StaffRosterTableProps {
  staffMembers: StaffMemberResponse[];
  onEdit: (staff: StaffMemberResponse) => void;
  isLoading?: boolean;
}

const ROLE_LABELS: Record<StaffRole, string> = {
  ADMINISTRADOR: 'Administrador',
  CHEF: 'Chef',
  ENCARGADO: 'Encargado de Mostrador',
};

const ROLE_BADGE_CLASSES: Record<StaffRole, string> = {
  ADMINISTRADOR: 'bg-purple-100 text-purple-800 border-purple-200',
  CHEF: 'bg-orange-100 text-orange-800 border-orange-200',
  ENCARGADO: 'bg-blue-100 text-blue-800 border-blue-200',
};

const STATUS_LABELS: Record<StaffAccountStatus, string> = {
  PENDING_FIRST_LOGIN: 'Pendiente de primer login',
  ACTIVE: 'Activo',
  SUSPENDED: 'Suspendido',
};

const STATUS_BADGE_CLASSES: Record<StaffAccountStatus, string> = {
  PENDING_FIRST_LOGIN: 'bg-amber-100 text-amber-800 border-amber-200',
  ACTIVE: 'bg-green-100 text-green-800 border-green-200',
  SUSPENDED: 'bg-red-100 text-red-800 border-red-200',
};

export const StaffRosterTable: React.FC<StaffRosterTableProps> = ({
  staffMembers,
  onEdit,
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
      {/* Search and Filters Bar */}
      <div className="flex flex-col sm:flex-row gap-3 items-center justify-between">
        <div className="w-full sm:w-80">
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Buscar por nombre o email..."
            className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 bg-white"
          />
        </div>

        <div className="flex items-center gap-2 w-full sm:w-auto">
          <label htmlFor="roleFilterSelect" className="text-xs text-gray-500 font-medium whitespace-nowrap">
            Filtrar por rol:
          </label>
          <select
            id="roleFilterSelect"
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value)}
            className="px-3 py-2 border border-gray-300 rounded-md text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="ALL">Todos los roles</option>
            <option value="ADMINISTRADOR">Administrador</option>
            <option value="CHEF">Chef</option>
            <option value="ENCARGADO">Encargado de Mostrador</option>
          </select>
        </div>
      </div>

      {/* Roster Table */}
      <div className="bg-white shadow rounded-lg border border-gray-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200 text-left text-sm">
            <thead className="bg-gray-50 text-gray-500 font-medium">
              <tr>
                <th scope="col" className="px-6 py-3">Nombre</th>
                <th scope="col" className="px-6 py-3">Correo electrónico</th>
                <th scope="col" className="px-6 py-3">Rol</th>
                <th scope="col" className="px-6 py-3">Estado</th>
                <th scope="col" className="px-6 py-3 text-right">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 bg-white">
              {isLoading ? (
                <tr>
                  <td colSpan={5} className="px-6 py-8 text-center text-gray-500">
                    Cargando nómina de empleados...
                  </td>
                </tr>
              ) : filteredMembers.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-6 py-8 text-center text-gray-500">
                    No se encontraron empleados registrados.
                  </td>
                </tr>
              ) : (
                filteredMembers.map((member) => (
                  <tr key={member.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-6 py-4 font-medium text-gray-900 whitespace-nowrap">
                      {member.fullName}
                    </td>
                    <td className="px-6 py-4 text-gray-600 whitespace-nowrap">
                      {member.email}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span
                        className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border ${
                          ROLE_BADGE_CLASSES[member.role] || 'bg-gray-100 text-gray-800'
                        }`}
                      >
                        {ROLE_LABELS[member.role] || member.role}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span
                        className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border ${
                          STATUS_BADGE_CLASSES[member.status] || 'bg-gray-100 text-gray-800'
                        }`}
                      >
                        {STATUS_LABELS[member.status] || member.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-right whitespace-nowrap">
                      <button
                        type="button"
                        onClick={() => onEdit(member)}
                        className="text-blue-600 hover:text-blue-900 font-medium text-xs px-2.5 py-1 rounded border border-blue-200 hover:bg-blue-50 transition-colors"
                      >
                        Editar
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default StaffRosterTable;

