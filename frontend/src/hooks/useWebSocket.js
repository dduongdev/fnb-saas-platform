import { useEffect, useRef, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useTenant } from '../context/TenantContext';

/**
 * WebSocket Hook for real-time updates (US-16, US-17, US-18, US-19)
 * 
 * Backend uses STOMP over SockJS with Spring WebSocketMessageBroker.
 * Topics:
 * - /topic/tenant/{tenantId}/session/{sessionId} - Session updates
 * - /topic/tenant/{tenantId}/table/{tableId} - Session updates for a table
 * - /topic/tenant/{tenantId}/tables - All tables update
 * - /topic/tenant/{tenantId}/pending-sessions - Pending sessions for staff
 * - /topic/tenant/{tenantId}/notifications - General notifications
 */

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8081';

/**
 * Create STOMP client using SockJS
 */
function createStompClient(url, subscriptions, onConnect, onError) {
    const client = new Client({
        // Use SockJS for connection
        webSocketFactory: () => new SockJS(url),
        
        // Connection options
        connectHeaders: {},
        
        // Heartbeat (10 seconds)
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        
        // Reconnect settings
        reconnectDelay: 3000,
        
        // Debug output
        debug: (str) => {
            console.log('[STOMP Debug]', str);
        },
        
        // On successful connection
        onConnect: (frame) => {
            console.log('[STOMP] Connected:', frame);
            
            // Subscribe to all topics
            subscriptions.forEach(({ destination, callback }) => {
                client.subscribe(destination, (message) => {
                    try {
                        const data = JSON.parse(message.body);
                        callback(data);
                    } catch (e) {
                        console.error('[STOMP] Parse error:', e);
                    }
                });
                console.log('[STOMP] Subscribed to:', destination);
            });
            
            if (onConnect) onConnect();
        },
        
        // On connection error
        onStompError: (frame) => {
            console.error('[STOMP] Error:', frame);
            if (onError) onError(frame);
        },
        
        // On WebSocket error
        onWebSocketError: (event) => {
            console.error('[WS] Error:', event);
            if (onError) onError(event);
        },
        
        // On disconnect
        onDisconnect: () => {
            console.log('[STOMP] Disconnected');
        }
    });
    
    // Activate the client
    client.activate();
    
    return client;
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
    const { tenant } = useTenant();
    const clientRef = useRef(null);

    const handleMessage = useCallback((data) => {
        console.log('[WS] Received message:', data);
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
        if (!tenant?.id || !tableId) return;

        const wsUrl = `${API_BASE_URL}/ws`;
        const subscriptions = [
            {
                destination: `/topic/tenant/${tenant.id}/table/${tableId}`,
                callback: handleMessage
            }
        ];

        clientRef.current = createStompClient(
            wsUrl,
            subscriptions,
            () => console.log('[WS] Session subscription active'),
            (err) => console.error('[WS] Session error:', err)
        );

        return () => {
            if (clientRef.current) {
                clientRef.current.deactivate();
            }
        };
    }, [tenant?.id, tableId, handleMessage]);
}

/**
 * Hook for session real-time updates by sessionId
 * Use this when you have sessionId but not tableId
 */
export function useSessionByIdWebSocket(sessionId, onSessionUpdate, onItemEvent, tenantIdOverride = null) {
    const { tenant } = useTenant();
    const clientRef = useRef(null);

    const handleMessage = useCallback((data) => {
        console.log('[WS ByID] Received message:', data);
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

    const activeTenantId = tenantIdOverride || tenant?.id;

    useEffect(() => {
        if (!activeTenantId || !sessionId) return;

        const wsUrl = `${API_BASE_URL}/ws`;
        const subscriptions = [
            {
                destination: `/topic/tenant/${activeTenantId}/session/${sessionId}`,
                callback: handleMessage
            }
        ];

        clientRef.current = createStompClient(
            wsUrl,
            subscriptions,
            () => console.log('[WS ByID] Session subscription active for session:', sessionId),
            (err) => console.error('[WS ByID] Session error:', err)
        );

        return () => {
            if (clientRef.current) {
                clientRef.current.deactivate();
            }
        };
    }, [activeTenantId, sessionId, handleMessage]);
}

/**
 * Hook for public customer page - subscribe by tableId
 * This is for customers who scan QR code (no login required)
 * Subscribes to table topic to receive session updates
 */
export function usePublicTableWebSocket(tableId, tenantId, onSessionUpdate, onItemEvent, onTableTransferred) {
    const clientRef = useRef(null);

    const handleMessage = useCallback((data) => {
        console.log('[WS Public Table] Received message:', data);
        
        // Handle TABLE_TRANSFERRED event - bàn đã được chuyển đi
        if (data.type === 'TABLE_TRANSFERRED') {
            console.log('[WS Public Table] Table transferred event received');
            if (onTableTransferred) {
                onTableTransferred(data);
            }
            return;
        }
        
        if (data.type && ['ORDER_ITEM_ADDED', 'ORDER_ITEM_DELETED', 'ORDER_ITEM_SERVED', 'ORDER_ITEM_UPDATED'].includes(data.type)) {
            if (onItemEvent) {
                onItemEvent(data);
            }
        } else {
            // Full session update (status changes like PENDING->ACTIVE, ACTIVE->COMPLETED)
            if (onSessionUpdate) {
                onSessionUpdate(data);
            }
        }
    }, [onSessionUpdate, onItemEvent, onTableTransferred]);

    useEffect(() => {
        if (!tenantId || !tableId) {
            console.log('[WS Public Table] Missing tenantId or tableId, skipping');
            return;
        }

        const wsUrl = `${API_BASE_URL}/ws`;
        const subscriptions = [
            {
                destination: `/topic/tenant/${tenantId}/table/${tableId}`,
                callback: handleMessage
            }
        ];

        console.log('[WS Public Table] Subscribing to:', `/topic/tenant/${tenantId}/table/${tableId}`);

        clientRef.current = createStompClient(
            wsUrl,
            subscriptions,
            () => console.log('[WS Public Table] Subscription active for table:', tableId),
            (err) => console.error('[WS Public Table] Error:', err)
        );

        return () => {
            if (clientRef.current) {
                clientRef.current.deactivate();
            }
        };
    }, [tenantId, tableId, handleMessage]);
}

/**
 * Hook for table grid real-time updates
 * Subscribe to all tables changes
 */
export function useTableWebSocket(onTableUpdate) {
    const { tenant } = useTenant();
    const clientRef = useRef(null);

    useEffect(() => {
        if (!tenant?.id) return;

        const wsUrl = `${API_BASE_URL}/ws`;
        const subscriptions = [
            {
                destination: `/topic/tenant/${tenant.id}/tables`,
                callback: onTableUpdate
            }
        ];

        clientRef.current = createStompClient(
            wsUrl,
            subscriptions,
            () => console.log('[WS] Tables subscription active'),
            (err) => console.error('[WS] Tables error:', err)
        );

        return () => {
            if (clientRef.current) {
                clientRef.current.deactivate();
            }
        };
    }, [tenant?.id, onTableUpdate]);
}

/**
 * Hook for pending sessions real-time updates (staff notifications)
 */
export function usePendingSessionsWebSocket(onPendingUpdate) {
    const { tenant } = useTenant();
    const clientRef = useRef(null);

    useEffect(() => {
        console.log('[WS Pending] Effect triggered - tenantId:', tenant?.id);
        if (!tenant?.id) {
            console.log('[WS Pending] No tenant, skipping');
            return;
        }

        const wsUrl = `${API_BASE_URL}/ws`;
        const topic = `/topic/tenant/${tenant.id}/pending-sessions`;
        console.log('[WS Pending] Subscribing to:', topic);
        
        const subscriptions = [
            {
                destination: topic,
                callback: (data) => {
                    console.log('[WS Pending] Received update:', data);
                    onPendingUpdate(data);
                }
            }
        ];

        clientRef.current = createStompClient(
            wsUrl,
            subscriptions,
            () => console.log('[WS Pending] Subscription active for:', topic),
            (err) => console.error('[WS Pending] Error:', err)
        );

        return () => {
            if (clientRef.current) {
                clientRef.current.deactivate();
            }
        };
    }, [tenant?.id, onPendingUpdate]);
}

/**
 * Hook for general notifications
 */
export function useNotificationWebSocket(onNotification) {
    const { tenant } = useTenant();
    const clientRef = useRef(null);

    useEffect(() => {
        if (!tenant?.id) return;

        const wsUrl = `${API_BASE_URL}/ws`;
        const subscriptions = [
            {
                destination: `/topic/tenant/${tenant.id}/notifications`,
                callback: onNotification
            }
        ];

        clientRef.current = createStompClient(
            wsUrl,
            subscriptions,
            () => console.log('[WS] Notifications subscription active'),
            (err) => console.error('[WS] Notifications error:', err)
        );

        return () => {
            if (clientRef.current) {
                clientRef.current.deactivate();
            }
        };
    }, [tenant?.id, onNotification]);
}

/**
 * Hook to subscribe multiple sessions at once (for pending sessions monitoring)
 * This creates ONE client subscribing to MULTIPLE session topics
 */
export function useMultipleSessionsWebSocket(sessionIds, onSessionUpdate, onItemEvent) {
    const { tenant } = useTenant();
    const clientRef = useRef(null);
    const sessionIdsRef = useRef([]);

    const handleMessage = useCallback((data) => {
        console.log('[WS Multi] Received:', data);
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
        console.log('[WS Multi] Effect triggered - sessionIds:', sessionIds);
        
        if (!tenant?.id || !sessionIds || sessionIds.length === 0) {
            console.log('[WS Multi] No tenant or empty sessions, cleaning up');
            // Cleanup if no sessions
            if (clientRef.current) {
                clientRef.current.deactivate();
                clientRef.current = null;
            }
            sessionIdsRef.current = [];
            return;
        }

        // Check if sessionIds actually changed (deep comparison)
        const idsChanged = JSON.stringify(sessionIdsRef.current.sort()) !== JSON.stringify([...sessionIds].sort());
        console.log('[WS Multi] IDs changed?', idsChanged, 'Previous:', sessionIdsRef.current, 'New:', sessionIds);
        
        if (!idsChanged && clientRef.current) {
            console.log('[WS Multi] No change and client exists, skipping');
            return; // No change, keep existing connection
        }

        sessionIdsRef.current = sessionIds;

        // Deactivate old client
        if (clientRef.current) {
            console.log('[WS Multi] Deactivating old client');
            clientRef.current.deactivate();
        }

        console.log('[WS Multi] Creating new client for sessions:', sessionIds);
        const wsUrl = `${API_BASE_URL}/ws`;
        const subscriptions = sessionIds.map(sessionId => ({
            destination: `/topic/tenant/${tenant.id}/session/${sessionId}`,
            callback: handleMessage
        }));

        clientRef.current = createStompClient(
            wsUrl,
            subscriptions,
            () => console.log('[WS Multi] Subscribed to', sessionIds.length, 'sessions:', sessionIds),
            (err) => console.error('[WS Multi] Error:', err)
        );

        return () => {
            if (clientRef.current) {
                console.log('[WS Multi] Cleanup - deactivating client');
                clientRef.current.deactivate();
            }
        };
    }, [tenant?.id, JSON.stringify([...sessionIds].sort()), handleMessage]);
}
