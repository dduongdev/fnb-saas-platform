import { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import Keycloak from 'keycloak-js';
import { syncUser } from '../api/auth';

const AuthContext = createContext(null);

// Keycloak configuration
const keycloakConfig = {
    url: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8080',
    realm: import.meta.env.VITE_KEYCLOAK_REALM || 'fnb-platform',
    clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'fnb-web',
};

export function AuthProvider({ children }) {
    const [keycloak, setKeycloak] = useState(null);
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);
    const [initialized, setInitialized] = useState(false);
    const initRef = useRef(false);

    // Initialize Keycloak ONCE
    useEffect(() => {
        // Prevent double init in React StrictMode
        if (initRef.current) return;
        initRef.current = true;

        const kc = new Keycloak(keycloakConfig);

        const initKeycloak = async () => {
            try {
                // Use check-sso: checks if user is already logged in without redirecting
                // checkLoginIframe: false to avoid iframe issues with some browsers/configs
                const authenticated = await kc.init({
                    onLoad: 'check-sso',
                    checkLoginIframe: false,
                    pkceMethod: 'S256',
                    redirectUri: window.location.origin + window.location.pathname,
                });

                setKeycloak(kc);
                setInitialized(true);

                if (authenticated && kc.token) {
                    localStorage.setItem('access_token', kc.token);

                    // Sync user to backend
                    try {
                        const userData = await syncUser();
                        setUser({
                            id: kc.subject,
                            username: kc.tokenParsed?.preferred_username,
                            email: kc.tokenParsed?.email,
                            fullName: kc.tokenParsed?.name,
                            ...userData,
                        });
                    } catch (error) {
                        console.error('Failed to sync user:', error);
                        // Still set basic user info from token
                        setUser({
                            id: kc.subject,
                            username: kc.tokenParsed?.preferred_username,
                            email: kc.tokenParsed?.email,
                            fullName: kc.tokenParsed?.name,
                        });
                    }
                }

                setLoading(false);
            } catch (error) {
                console.error('Keycloak init failed:', error);
                setKeycloak(kc);
                setInitialized(true);
                setLoading(false);
            }
        };

        initKeycloak();

        // Token refresh interval
        const refreshInterval = setInterval(() => {
            if (kc?.authenticated) {
                kc.updateToken(60).then((refreshed) => {
                    if (refreshed && kc.token) {
                        localStorage.setItem('access_token', kc.token);
                    }
                }).catch(() => {
                    console.warn('Token refresh failed, user may need to re-login');
                });
            }
        }, 30000);

        return () => clearInterval(refreshInterval);
    }, []);

    // Login - redirects to Keycloak
    const login = useCallback(() => {
        if (keycloak) {
            // Redirect to Keycloak login page
            keycloak.login({
                redirectUri: window.location.origin + '/select-tenant',
            });
        }
    }, [keycloak]);

    // Logout
    const logout = useCallback(() => {
        if (keycloak) {
            localStorage.removeItem('access_token');
            localStorage.removeItem('tenant_id');
            keycloak.logout({
                redirectUri: window.location.origin,
            });
        }
    }, [keycloak]);

    const isAuthenticated = keycloak?.authenticated ?? false;

    const value = {
        user,
        loading,
        initialized,
        isAuthenticated,
        login,
        logout,
        keycloak,
    };

    return (
        <AuthContext.Provider value={value}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth must be used within AuthProvider');
    }
    return context;
}
