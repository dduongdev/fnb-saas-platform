import { useEffect, useRef, useCallback } from 'react';
import { useTenant } from '../context/TenantContext';

/**
 * WebSocket Hook for real-time updates (US-16, US-17, US-18, US-19)
 * 
 * Backend uses STOMP over SockJS with Spring WebSocketMessageBroker.
 * Topics:
 * - /topic/tenant/{tenantId}/table/{tableId} - Session updates for a table
 * - /topic/tenant/{tenantId}/tables - All tables update
 * - /topic/tenant/{tenantId}/pending-sessions - Pending sessions for staff
 * - /topic/tenant/{tenantId}/notifications - General notifications
 */

const API_BASE_URL = import.meta.env.VITE_API_URL || '';

/**
 * Create STOMP client using native WebSocket fallback
 * Note: For production, use sockjs-client + @stomp/stompjs packages
 */
function createStompConnection(url, subscriptions, onConnect, onError) {
    let ws = null;
    let connected = false;
    let subscriptionId = 0;

    // Simple STOMP frame parser/builder
    const buildFrame = (command, headers = {}, body = '') => {
        let frame = command + '\n';
        Object.keys(headers).forEach(key => {
            frame += `${key}:${headers[key]}\n`;
        });
        frame += '\n' + body + '\0';
        return frame;
    };

    const parseFrame = (data) => {
        const lines = data.split('\n');
        const command = lines[0];
        const headers = {};
        let i = 1;
        while (i < lines.length && lines[i] !== '') {
            const [key, ...value] = lines[i].split(':');
            headers[key] = value.join(':');
            i++;
        }
        const body = lines.slice(i + 1).join('\n').replace(/\0$/, '');
        return { command, headers, body };
    };

    const connect = () => {
        try {
            // Use SockJS transport simulation or plain WebSocket
            const wsUrl = url.replace('http', 'ws');
            ws = new WebSocket(wsUrl);

            ws.onopen = () => {
                // Send CONNECT frame
                ws.send(buildFrame('CONNECT', {
                    'accept-version': '1.1,1.0',
                    'heart-beat': '10000,10000'
                }));
            };

            ws.onmessage = (event) => {
                const frame = parseFrame(event.data);

                if (frame.command === 'CONNECTED') {
                    connected = true;
                    // Subscribe to all topics
                    subscriptions.forEach(({ destination, callback }) => {
                        ws.send(buildFrame('SUBSCRIBE', {
                            id: `sub-${subscriptionId++}`,
                            destination
                        }));
                    });
                    if (onConnect) onConnect();
                }

                if (frame.command === 'MESSAGE') {
                    const sub = subscriptions.find(s => s.destination === frame.headers.destination);
                    if (sub && sub.callback) {
                        try {
                            const data = JSON.parse(frame.body);
                            sub.callback(data);
                        } catch (e) {
                            console.error('[STOMP] Parse error:', e);
                        }
                    }
                }
            };

            ws.onclose = () => {
                connected = false;
                // Auto-reconnect after 3 seconds
                setTimeout(connect, 3000);
            };

            ws.onerror = (error) => {
                if (onError) onError(error);
            };
        } catch (error) {
            console.error('[STOMP] Connection error:', error);
        }
    };

    const disconnect = () => {
        if (ws) {
            if (connected) {
                ws.send(buildFrame('DISCONNECT'));
            }
            ws.close();
            ws = null;
        }
    };

    connect();

    return { disconnect };
}

/**
 * Hook for session real-time updates
 * Subscribe to session changes for a specific table
 * 
 * Events received:
 * - SESSION_UPDATED: Full session state
 * - ORDER_ITEM_ADDED: New item added
 * - ORDER_ITEM_DELETED: Item deleted
 * - ORDER_ITEM_SERVED: Item marked as served
 * - ORDER_ITEM_UPDATED: Item quantity updated
 */
export function useSessionWebSocket(tableId, onSessionUpdate, onItemEvent) {
    const { currentTenant } = useTenant();
    const connectionRef = useRef(null);

    const handleMessage = useCallback((data) => {
        // Check if it's an event-based message
        if (data.type && ['ORDER_ITEM_ADDED', 'ORDER_ITEM_DELETED', 'ORDER_ITEM_SERVED', 'ORDER_ITEM_UPDATED'].includes(data.type)) {
            // Event-based update
            if (onItemEvent) {
                onItemEvent(data);
            }
        } else {
            // Full session update (backward compatible)
            if (onSessionUpdate) {
                onSessionUpdate(data);
            }
        }
    }, [onSessionUpdate, onItemEvent]);

    useEffect(() => {
        if (!currentTenant?.id || !tableId) return;

        const wsUrl = `${API_BASE_URL}/ws`;
        const subscriptions = [
            {
                destination: `/topic/tenant/${currentTenant.id}/table/${tableId}`,
                callback: handleMessage
            }
        ];

        connectionRef.current = createStompConnection(
            wsUrl,
            subscriptions,
            () => console.log('[WS] Session subscription active'),
            (err) => console.error('[WS] Session error:', err)
        );

        return () => {
            if (connectionRef.current) {
                connectionRef.current.disconnect();
            }
        };
    }, [currentTenant?.id, tableId, handleMessage]);
}

/**
 * Hook for session real-time updates by sessionId
 * Use this when you have sessionId but not tableId
 */
export function useSessionByIdWebSocket(sessionId, onSessionUpdate, onItemEvent) {
    const { currentTenant } = useTenant();
    const connectionRef = useRef(null);

    const handleMessage = useCallback((data) => {
        if (data.type && ['ORDER_ITEM_ADDED', 'ORDER_ITEM_DELETED', 'ORDER_ITEM_SERVED', 'ORDER_ITEM_UPDATED'].includes(data.type)) {
            if (onItemEvent) {
                onItemEvent(data);
            }
        } else {
            if (onSessionUpdate) {
                onSessionUpdate(data);
            }
        }
    }, [onSessionUpdate, onItemEvent]);

    useEffect(() => {
        if (!currentTenant?.id || !sessionId) return;

        const wsUrl = `${API_BASE_URL}/ws`;
        const subscriptions = [
            {
                destination: `/topic/tenant/${currentTenant.id}/session/${sessionId}`,
                callback: handleMessage
            }
        ];

        connectionRef.current = createStompConnection(
            wsUrl,
            subscriptions,
            () => console.log('[WS] Session by ID subscription active'),
            (err) => console.error('[WS] Session by ID error:', err)
        );

        return () => {
            if (connectionRef.current) {
                connectionRef.current.disconnect();
            }
        };
    }, [currentTenant?.id, sessionId, handleMessage]);
}

/**
 * Hook for table grid real-time updates
 * Subscribe to all tables changes
 */
export function useTableWebSocket(onTableUpdate) {
    const { currentTenant } = useTenant();
    const connectionRef = useRef(null);

    useEffect(() => {
        if (!currentTenant?.id) return;

        const wsUrl = `${API_BASE_URL}/ws`;
        const subscriptions = [
            {
                destination: `/topic/tenant/${currentTenant.id}/tables`,
                callback: onTableUpdate
            }
        ];

        connectionRef.current = createStompConnection(
            wsUrl,
            subscriptions,
            () => console.log('[WS] Tables subscription active'),
            (err) => console.error('[WS] Tables error:', err)
        );

        return () => {
            if (connectionRef.current) {
                connectionRef.current.disconnect();
            }
        };
    }, [currentTenant?.id, onTableUpdate]);
}

/**
 * Hook for pending sessions real-time updates (staff notifications)
 */
export function usePendingSessionsWebSocket(onPendingUpdate) {
    const { currentTenant } = useTenant();
    const connectionRef = useRef(null);

    useEffect(() => {
        if (!currentTenant?.id) return;

        const wsUrl = `${API_BASE_URL}/ws`;
        const subscriptions = [
            {
                destination: `/topic/tenant/${currentTenant.id}/pending-sessions`,
                callback: onPendingUpdate
            }
        ];

        connectionRef.current = createStompConnection(
            wsUrl,
            subscriptions,
            () => console.log('[WS] Pending sessions subscription active'),
            (err) => console.error('[WS] Pending sessions error:', err)
        );

        return () => {
            if (connectionRef.current) {
                connectionRef.current.disconnect();
            }
        };
    }, [currentTenant?.id, onPendingUpdate]);
}

/**
 * Hook for general notifications
 */
export function useNotificationWebSocket(onNotification) {
    const { currentTenant } = useTenant();
    const connectionRef = useRef(null);

    useEffect(() => {
        if (!currentTenant?.id) return;

        const wsUrl = `${API_BASE_URL}/ws`;
        const subscriptions = [
            {
                destination: `/topic/tenant/${currentTenant.id}/notifications`,
                callback: onNotification
            }
        ];

        connectionRef.current = createStompConnection(
            wsUrl,
            subscriptions,
            () => console.log('[WS] Notifications subscription active'),
            (err) => console.error('[WS] Notifications error:', err)
        );

        return () => {
            if (connectionRef.current) {
                connectionRef.current.disconnect();
            }
        };
    }, [currentTenant?.id, onNotification]);
}
