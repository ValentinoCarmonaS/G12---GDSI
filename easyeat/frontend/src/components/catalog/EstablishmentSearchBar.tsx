import React from 'react';

export interface EstablishmentSearchBarProps {
  onSelect?: (establishmentId: string) => void;
  className?: string;
}

export const EstablishmentSearchBar: React.FC<EstablishmentSearchBarProps> = () => {
  return (
    <div data-testid="establishment-search-bar">
      {/* TDD RED: Stub */}
    </div>
  );
};
