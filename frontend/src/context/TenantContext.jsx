import { createContext, useContext, useState, useCallback, useEffect } from 'react';
import { getMyTenants, getTenantDetail } from '../api/tenant';
import { useAuth } from './AuthContext';

const TenantContext = createContext(null);

export function TenantProvider({ children }) {
    const { user, isAuthenticated } = useAuth();
    const [tenant, setTenant] = useState(null);
    const [tenants, setTenants] = useState([]);
    const [loading, setLoading] = useState(true);

    // Load tenants when user is authenticated
    useEffect(() => {
        if (isAuthenticated) {
            loadTenants();
        } else {
            setTenants([]);
            setTenant(null);
            setLoading(false);
        }
    }, [isAuthenticated]);

    // Restore selected tenant from localStorage
    useEffect(() => {
        const savedTenantId = localStorage.getItem('tenant_id');
        if (savedTenantId && tenants.length > 0) {
            const savedTenant = tenants.find(t => t.id === savedTenantId);
            if (savedTenant) {
                setTenant(savedTenant);
            }
        }
    }, [tenants]);

    const loadTenants = useCallback(async () => {
        try {
            setLoading(true);
            const data = await getMyTenants();
            setTenants(data || []);
        } catch (error) {
            console.error('Failed to load tenants:', error);
            setTenants([]);
        } finally {
            setLoading(false);
        }
    }, []);

    const selectTenant = useCallback(async (tenantId) => {
        try {
            const tenantData = await getTenantDetail(tenantId);
            setTenant(tenantData);
            localStorage.setItem('tenant_id', tenantId);
        } catch (error) {
            console.error('Failed to select tenant:', error);
        }
    }, []);

    const clearTenant = useCallback(() => {
        setTenant(null);
        localStorage.removeItem('tenant_id');
    }, []);

    const refreshTenant = useCallback(async () => {
        if (tenant?.id) {
            try {
                const tenantData = await getTenantDetail(tenant.id);
                setTenant(tenantData);
            } catch (error) {
                console.error('Failed to refresh tenant:', error);
            }
        }
    }, [tenant?.id]);

    // Check if current user is the owner of selected tenant
    const isOwner = tenant && user && tenant.ownerId === user.id;

    return (
        <TenantContext.Provider value={{
            tenant,
            tenants,
            loading,
            isOwner,
            selectTenant,
            clearTenant,
            refreshTenant,
            reloadTenants: loadTenants,
        }}>
            {children}
        </TenantContext.Provider>
    );
}

export function useTenant() {
    const context = useContext(TenantContext);
    if (!context) {
        throw new Error('useTenant must be used within TenantProvider');
    }
    return context;
}
