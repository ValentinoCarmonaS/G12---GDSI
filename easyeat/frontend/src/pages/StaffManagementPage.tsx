import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { WorkspaceLayout } from '../components/common/WorkspaceLayout';
import { StaffRosterTable } from '../components/staff/StaffRosterTable';
import { StaffFormModal } from '../components/staff/StaffFormModal';
import { TemporaryCredentialsModal } from '../components/staff/TemporaryCredentialsModal';
import { staffApi } from '../api/staffApi';
import {
  StaffMemberResponse,
  StaffRegistrationRequest,
  StaffUpdateRequest,
} from '../types/staff';

export const StaffManagementPage: React.FC = () => {
  const [staffMembers, setStaffMembers] = useState<StaffMemberResponse[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [generalError, setGeneralError] = useState<string | null>(null);

  // Form modal state
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [selectedStaff, setSelectedStaff] = useState<StaffMemberResponse | null>(null);

  // Temporary credentials modal state
  const [credentialModalData, setCredentialModalData] = useState<{
    staffName: string;
    email: string;
    temporaryCredential: string;
  } | null>(null);

  const fetchStaff = useCallback(async () => {
    setIsLoading(true);
    setGeneralError(null);
    try {
      const data = await staffApi.listStaff();
      setStaffMembers(data);
    } catch {
      setGeneralError('No se pudo cargar la nómina de empleados. Intente nuevamente.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchStaff();
  }, [fetchStaff]);

  const handleOpenCreate = () => {
    setSelectedStaff(null);
    setIsFormOpen(true);
  };

  const handleOpenEdit = (staff: StaffMemberResponse) => {
    setSelectedStaff(staff);
    setIsFormOpen(true);
  };

  const handleFormSubmit = async (data: StaffRegistrationRequest | StaffUpdateRequest) => {
    if (selectedStaff) {
      // Edit mode
      await staffApi.updateStaff(selectedStaff.id, data as StaffUpdateRequest);
      await fetchStaff();
    } else {
      // Create mode
      const response = await staffApi.createStaff(data as StaffRegistrationRequest);
      await fetchStaff();
      setCredentialModalData({
        staffName: response.fullName,
        email: response.email,
        temporaryCredential: response.temporaryCredential,
      });
    }
  };

  return (
    <WorkspaceLayout
      title="Gestión del Personal"
      subtitle="Administración de empleados, asignación de roles y emisión de credenciales"
    >
      <div className="space-y-6">
        {/* Navigation & Action Bar */}
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div>
            <Link
              to="/admin"
              className="inline-flex items-center text-xs font-semibold text-blue-600 hover:text-blue-800 mb-2"
            >
              ← Volver al Panel de Administración
            </Link>
            <h2 className="text-2xl font-bold text-gray-900 tracking-tight">
              Nómina del Establecimiento
            </h2>
          </div>

          <button
            type="button"
            onClick={handleOpenCreate}
            className="inline-flex items-center px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-md shadow-sm transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
          >
            + Nuevo Empleado
          </button>
        </div>

        {generalError && (
          <div
            role="alert"
            className="p-3 text-sm rounded-md bg-red-50 border border-red-200 text-red-700 animate-fade-in"
          >
            {generalError}
          </div>
        )}

        {/* Staff Table */}
        <StaffRosterTable
          staffMembers={staffMembers}
          onEdit={handleOpenEdit}
          isLoading={isLoading}
        />

        {/* Staff Form Modal */}
        <StaffFormModal
          isOpen={isFormOpen}
          onClose={() => setIsFormOpen(false)}
          onSubmit={handleFormSubmit}
          initialData={selectedStaff}
        />

        {/* Temporary Credentials Modal */}
        {credentialModalData && (
          <TemporaryCredentialsModal
            isOpen={Boolean(credentialModalData)}
            onClose={() => setCredentialModalData(null)}
            staffName={credentialModalData.staffName}
            email={credentialModalData.email}
            temporaryCredential={credentialModalData.temporaryCredential}
          />
        )}
      </div>
    </WorkspaceLayout>
  );
};

export default StaffManagementPage;

