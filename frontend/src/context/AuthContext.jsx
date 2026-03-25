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
    const refreshTimerRef = useRef(null);

    const parseJwt = (token) => {
        try {
            const payload = token.split('.')[1];
            const decoded = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
            return JSON.parse(decodeURIComponent(escape(decoded)));
        } catch (e) {
            return null;
        }
    };

    const clearRefreshTimer = useCallback(() => {
        if (refreshTimerRef.current) {
            clearTimeout(refreshTimerRef.current);
            refreshTimerRef.current = null;
        }
    }, []);

    const refreshToken = async () => {
        const storedRefreshToken = localStorage.getItem('refresh_token');
        if (!storedRefreshToken) {
            return;
        }

        const tokenUrl = `${keycloakConfig.url}/realms/${keycloakConfig.realm}/protocol/openid-connect/token`;
        const params = new URLSearchParams();
        params.append('grant_type', 'refresh_token');
        params.append('client_id', keycloakConfig.clientId);
        params.append('refresh_token', storedRefreshToken);

        try {
            const res = await fetch(tokenUrl, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: params.toString(),
            });

            if (!res.ok) {
                throw new Error('refresh token failed');
            }

            const data = await res.json();
            if (data.access_token) {
                localStorage.setItem('access_token', data.access_token);
                if (data.refresh_token) {
                    localStorage.setItem('refresh_token', data.refresh_token);
                }

                const parsed = parseJwt(data.access_token);
                setUser((prev) => ({
                    ...prev,
                    id: parsed?.sub,
                    username: parsed?.preferred_username || parsed?.username,
                    email: parsed?.email,
                    fullName: parsed?.name,
                }));

                scheduleRefresh(data.access_token);
            }
        } catch (error) {
            console.warn('Token refresh failed', error);
            localStorage.removeItem('access_token');
            localStorage.removeItem('refresh_token');
            setUser(null);
        }
    };

    const scheduleRefresh = (token) => {
        if (!token) return;
        const parsed = parseJwt(token);
        if (!parsed?.exp) return;

        const expiresAt = parsed.exp * 1000;
        const refreshAt = expiresAt - 60000;
        const delay = refreshAt - Date.now();

        if (delay <= 0) {
            refreshToken();
            return;
        }

        clearRefreshTimer();
        refreshTimerRef.current = setTimeout(() => {
            refreshToken();
        }, delay);
    };

    useEffect(() => {
        if (initRef.current) return;
        initRef.current = true;

        const kc = new Keycloak(keycloakConfig);

        const initKeycloak = async () => {
            try {
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
                    if (kc.refreshToken) {
                        localStorage.setItem('refresh_token', kc.refreshToken);
                    }
                    scheduleRefresh(kc.token);

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
                        setUser({
                            id: kc.subject,
                            username: kc.tokenParsed?.preferred_username,
                            email: kc.tokenParsed?.email,
                            fullName: kc.tokenParsed?.name,
                        });
                    }
                } else {
                    const savedToken = localStorage.getItem('access_token');
                    if (savedToken) {
                        scheduleRefresh(savedToken);
                        const parsed = parseJwt(savedToken);
                        if (parsed) {
                            try {
                                const userData = await syncUser();
                                setUser({
                                    id: parsed.sub,
                                    username: parsed.preferred_username || parsed.username,
                                    email: parsed.email,
                                    fullName: parsed.name,
                                    ...userData,
                                });
                            } catch (err) {
                                setUser({
                                    id: parsed.sub,
                                    username: parsed.preferred_username || parsed.username,
                                    email: parsed.email,
                                    fullName: parsed.name,
                                });
                            }
                        }
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

        return () => {
            clearInterval(refreshInterval);
            clearRefreshTimer();
        };
    }, [clearRefreshTimer]);

    const login = useCallback(() => {
        if (keycloak) {
            keycloak.login({
                redirectUri: window.location.origin + '/dashboard',
            });
        }
    }, [keycloak]);

    const directLogin = useCallback(async (username, password) => {
        const tokenUrl = `${keycloakConfig.url}/realms/${keycloakConfig.realm}/protocol/openid-connect/token`;
        const params = new URLSearchParams();
        params.append('grant_type', 'password');
        params.append('client_id', keycloakConfig.clientId);
        params.append('username', username);
        params.append('password', password);
        params.append('scope', 'openid');

        const res = await fetch(tokenUrl, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params.toString(),
        });

        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.error_description || err.error || 'Login failed');
        }

        const data = await res.json();
        if (data.access_token) {
            localStorage.setItem('access_token', data.access_token);
            if (data.refresh_token) localStorage.setItem('refresh_token', data.refresh_token);
            scheduleRefresh(data.access_token);

            const parsed = parseJwt(data.access_token);
            try {
                const userData = await syncUser();
                setUser({
                    id: parsed?.sub,
                    username: parsed?.preferred_username || parsed?.username,
                    email: parsed?.email,
                    fullName: parsed?.name,
                    ...userData,
                });
            } catch (err) {
                setUser({
                    id: parsed?.sub,
                    username: parsed?.preferred_username || parsed?.username,
                    email: parsed?.email,
                    fullName: parsed?.name,
                });
            }

            setInitialized(true);
            setLoading(false);

            return data;
        }

        throw new Error('Login failed');
    }, []);

    const isWaitstaff = !!localStorage.getItem('pos_access_key');
    const hasToken = !!localStorage.getItem('access_token');
    const isAuthenticated = (keycloak?.authenticated || hasToken || isWaitstaff) ?? false;

    const handleLogout = useCallback(() => {
        clearRefreshTimer();
        if (localStorage.getItem('pos_access_key')) {
            localStorage.removeItem('pos_access_key');
            window.location.href = '/waiter-login';
        } else if (keycloak) {
            localStorage.removeItem('access_token');
            localStorage.removeItem('refresh_token');
            localStorage.removeItem('tenant_id');
            keycloak.logout({
                redirectUri: window.location.origin,
            });
        } else {
            localStorage.removeItem('access_token');
            localStorage.removeItem('refresh_token');
            localStorage.removeItem('tenant_id');
            setUser(null);
            window.location.href = '/login';
        }
    }, [keycloak, clearRefreshTimer]);

    const value = {
        user: user || (isWaitstaff ? { name: 'Nhân viên POS', isWaitstaff: true } : null),
        loading,
        initialized,
        isAuthenticated,
        login,
        directLogin,
        logout: handleLogout,
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
