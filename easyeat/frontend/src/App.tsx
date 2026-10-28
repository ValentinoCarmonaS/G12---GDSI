import React from 'react';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { TenantProvider } from './context/TenantContext';
import { CartProvider } from './context/CartContext';
import { AppRoutes } from './router/AppRoutes';

export const App: React.FC = () => {
  return (
    <BrowserRouter>
      <AuthProvider>
        <TenantProvider>
          <CartProvider>
            <AppRoutes />
          </CartProvider>
        </TenantProvider>
      </AuthProvider>
    </BrowserRouter>
  );
};

export default App;
