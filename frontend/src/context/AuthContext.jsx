import { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import { loginWithCredentials, getAccessKeyInfo } from '../api/auth';

const AuthContext = createContext(null);


export function AuthProvider({ children }) {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);
    const [initialized, setInitialized] = useState(false);
    const [accessKeyRole, setAccessKeyRole] = useState(localStorage.getItem('pos_access_key_role'));
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
        // No refresh token support in local JWT implementation.
        localStorage.removeItem('access_token');
        setUser(null);
        setInitialized(false);
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
        const initialize = async () => {
            const storedToken = localStorage.getItem('access_token');
            const accessKey = localStorage.getItem('pos_access_key');

            if (storedToken) {
                localStorage.removeItem('pos_access_key');
                localStorage.removeItem('pos_access_key_role');

                scheduleRefresh(storedToken);
                const parsed = parseJwt(storedToken);
                if (parsed) {
                    setUser({
                        id: parsed.sub,
                        username: parsed.preferred_username || parsed.username,
                        email: parsed.email,
                        fullName: parsed.name,
                    });
                }
            } else if (accessKey) {
                try {
                    const info = await getAccessKeyInfo();
                    localStorage.setItem('pos_access_key_role', info.role);
                    setAccessKeyRole(info.role);
                } catch (error) {
                    localStorage.removeItem('pos_access_key');
                    localStorage.removeItem('pos_access_key_role');
                }
            }

            setInitialized(true);
            setLoading(false);
        };

        initialize();

        return () => {
            clearRefreshTimer();
        };
    }, [clearRefreshTimer]);

    const directLogin = useCallback(async (username, password) => {
        const data = await loginWithCredentials({ username, password });
        if (!data?.accessToken) {
            throw new Error('Login failed');
        }

        localStorage.removeItem('pos_access_key_role');
        localStorage.removeItem('pos_access_key');
        setAccessKeyRole(null);

        localStorage.setItem('access_token', data.accessToken);
        scheduleRefresh(data.accessToken);

        const parsed = parseJwt(data.accessToken);
        if (parsed) {
            setUser({
                id: parsed.sub,
                username: parsed.preferred_username || parsed.username,
                email: parsed.email,
                fullName: parsed.name,
            });
        }

        setInitialized(true);
        setLoading(false);
        return data;
    }, []);

    const login = useCallback((username, password) => {
        return directLogin(username, password);
    }, [directLogin]);

    const isWaitstaff = !!localStorage.getItem('pos_access_key');
    const hasToken = !!localStorage.getItem('access_token');
    const isAuthenticated = (hasToken || isWaitstaff) ?? false;
    const isKitchen = isWaitstaff && accessKeyRole === 'KITCHEN';

    const handleLogout = useCallback(() => {
        clearRefreshTimer();
        localStorage.removeItem('pos_access_key_role');
        if (localStorage.getItem('pos_access_key')) {
            localStorage.removeItem('pos_access_key');
            window.location.href = '/access-key-login';
        } else {
            localStorage.removeItem('access_token');
            localStorage.removeItem('tenant_id');
            setUser(null);
            window.location.href = '/login';
        }
    }, [clearRefreshTimer]);

    const value = {
        user: user || (isWaitstaff ? { name: 'Nhân viên POS', isWaitstaff: true, accessKeyRole } : null),
        loading,
        initialized,
        isAuthenticated,
        isKitchen,
        isWaitstaff,
        login,
        directLogin,
        logout: handleLogout,
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
