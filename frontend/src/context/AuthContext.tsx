import React, { createContext, useContext, useState, useEffect } from 'react';
import { UserPrincipal } from '../types';
import { authenticateSession } from '../api/client';

interface AuthContextType {
  currentUser: UserPrincipal;
  switchRole: (role: 'GLOBAL_ADMIN' | 'TENANT_ADMIN', tenantId?: string, tenantName?: string) => void;
}

const defaultUser: UserPrincipal = {
  userId: 'usr-global-admin',
  email: 'admin@umis.local',
  role: 'GLOBAL_ADMIN',
  tenantName: 'System Core',
};

const AuthContext = createContext<AuthContextType>({
  currentUser: defaultUser,
  switchRole: () => {},
});

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [currentUser, setCurrentUser] = useState<UserPrincipal>(defaultUser);

  useEffect(() => {
    // Acquire active JWT bearer token on initial mount
    authenticateSession('GLOBAL_ADMIN');
  }, []);

  const switchRole = (
    role: 'GLOBAL_ADMIN' | 'TENANT_ADMIN',
    tenantId?: string,
    tenantName?: string
  ) => {
    localStorage.removeItem('umis_auth_token');
    authenticateSession(role);

    if (role === 'GLOBAL_ADMIN') {
      setCurrentUser({
        userId: 'usr-global-admin',
        email: 'admin@umis.local',
        role: 'GLOBAL_ADMIN',
        tenantName: 'System Core',
      });
    } else {
      setCurrentUser({
        userId: 'usr-tenant-admin',
        email: `admin@${tenantName ? tenantName.toLowerCase().replace(/\s+/g, '') : 'tenant'}.com`,
        role: 'TENANT_ADMIN',
        tenantId: tenantId || '11111111-1111-1111-1111-111111111111',
        tenantName: tenantName || 'Acme Corporation',
        tenantCode: 'tenant-a',
      });
    }
  };

  return (
    <AuthContext.Provider value={{ currentUser, switchRole }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
