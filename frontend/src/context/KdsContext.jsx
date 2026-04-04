/**
 * Context API cho Kitchen Display System (KDS).
 * 
 * Quản lý:
 * - Danh sách sessions đang hiển thị
 * - Real-time updates từ WebSocket
 * - Event handling (session created, item added, etc.)
 * 
 * @author FNB Team
 * @version 1.0
 */

import React, { createContext, useContext, useReducer, useCallback, useEffect } from 'react';
import KdsWebSocketService from '../services/KdsWebSocketService';
import { getKdsSessions } from '../api/pos';

const KdsContext = createContext();

/**
 * Action types cho reducer
 */
export const KDS_ACTIONS = {
  SET_SESSIONS: 'SET_SESSIONS',
  ADD_SESSION: 'ADD_SESSION',
  REMOVE_SESSION: 'REMOVE_SESSION',
  ADD_ITEM: 'ADD_ITEM',
  REMOVE_ITEM: 'REMOVE_ITEM',
  UPDATE_ITEM_STATUS: 'UPDATE_ITEM_STATUS',
  UPDATE_ITEM: 'UPDATE_ITEM',
  REFRESH: 'REFRESH',
  SET_ERROR: 'SET_ERROR',
  CLEAR_ERROR: 'CLEAR_ERROR',
  SET_CONNECTED: 'SET_CONNECTED',
  SET_DISCONNECTED: 'SET_DISCONNECTED',
};

/**
 * Initial state
 */
const initialState = {
  sessions: [],
  error: null,
  loading: false,
  connected: false,
};

/**
 * Reducer function để xử lý state updates
 */
function kdsReducer(state, action) {
  switch (action.type) {
    case KDS_ACTIONS.SET_SESSIONS:
      return {
        ...state,
        sessions: action.payload || [],
        loading: false,
      };

    case KDS_ACTIONS.SET_CONNECTED:
      return {
        ...state,
        connected: true,
        loading: false,
      };

    case KDS_ACTIONS.SET_DISCONNECTED:
      return {
        ...state,
        connected: false,
      };

    case KDS_ACTIONS.ADD_SESSION: {
      const newSession = action.payload;
      return {
        ...state,
        sessions: [newSession, ...state.sessions],
      };
    }

    case KDS_ACTIONS.REMOVE_SESSION:
      return {
        ...state,
        sessions: state.sessions.filter((s) => s.sessionId !== action.payload),
      };

    case KDS_ACTIONS.ADD_ITEM: {
      const { sessionId, item } = action.payload;
      return {
        ...state,
        sessions: state.sessions.map((session) =>
          session.sessionId === sessionId
            ? {
                ...session,
                items: [item, ...session.items],
                pendingItemCount:
                  item.status === 'PENDING'
                    ? (session.pendingItemCount || 0) + 1
                    : session.pendingItemCount,
              }
            : session
        ),
      };
    }

    case KDS_ACTIONS.REMOVE_ITEM: {
      const { sessionId, itemId } = action.payload;
      return {
        ...state,
        sessions: state.sessions.map((session) =>
          session.sessionId === sessionId
            ? {
                ...session,
                items: session.items.filter((item) => item.itemId !== itemId),
                pendingItemCount: Math.max(0, session.pendingItemCount - 1),
              }
            : session
        ),
      };
    }

    case KDS_ACTIONS.UPDATE_ITEM_STATUS:
    case KDS_ACTIONS.UPDATE_ITEM: {
      const { sessionId, item } = action.payload;
      
      return {
        ...state,
        sessions: state.sessions.map((session) => {
          if (session.sessionId !== sessionId) return session;

          // Update item in items list and re-sort
          const updatedItems = session.items.map((i) =>
            i.itemId === item.itemId ? item : i
          );

          // Re-sort: PENDING first, then SERVED
          const sortedItems = updatedItems.sort((a, b) => {
            if (a.status === 'PENDING' && b.status !== 'PENDING') return -1;
            if (a.status !== 'PENDING' && b.status === 'PENDING') return 1;
            return new Date(a.createdAt) - new Date(b.createdAt);
          });

          return {
            ...session,
            items: sortedItems,
            pendingItemCount: sortedItems.filter((i) => i.status === 'PENDING').length,
          };
        }),
      };
    }

    case KDS_ACTIONS.REFRESH:
      return {
        ...state,
        sessions: action.payload || [],
      };

    case KDS_ACTIONS.SET_ERROR:
      return {
        ...state,
        error: action.payload,
      };

    case KDS_ACTIONS.CLEAR_ERROR:
      return {
        ...state,
        error: null,
      };

    default:
      return state;
  }
}

/**
 * KdsProvider component
 */
export function KdsProvider({ children, tenantId, kitchenAreaId }) {
  const [state, dispatch] = useReducer(kdsReducer, initialState);

  // Handle incoming WebSocket messages
  const handleMessage = useCallback((payload) => {
    if (!payload || !payload.eventType) return;

    console.log('Processing KDS event:', payload.eventType, payload);

    switch (payload.eventType) {
      case 'REFRESH':
        // payload.data should contain array of KdsSessionDto
        dispatch({
          type: KDS_ACTIONS.REFRESH,
          payload: Array.isArray(payload.data) ? payload.data : [],
        });
        break;

      case 'SESSION_CREATED':
        dispatch({
          type: KDS_ACTIONS.ADD_SESSION,
          payload: payload.data,
        });
        break;

      case 'SESSION_CANCELLED':
        dispatch({
          type: KDS_ACTIONS.REMOVE_SESSION,
          payload: payload.sessionId,
        });
        break;

      case 'ITEM_ADDED':
        dispatch({
          type: KDS_ACTIONS.ADD_ITEM,
          payload: {
            sessionId: payload.sessionId,
            item: payload.data,
          },
        });
        break;

      case 'ITEM_REMOVED':
        dispatch({
          type: KDS_ACTIONS.REMOVE_ITEM,
          payload: {
            sessionId: payload.sessionId,
            itemId: payload.data,
          },
        });
        break;

      case 'ITEM_STATUS_CHANGED':
        dispatch({
          type: KDS_ACTIONS.UPDATE_ITEM_STATUS,
          payload: {
            sessionId: payload.sessionId,
            item: payload.data,
          },
        });
        break;

      case 'ITEM_UPDATED':
        dispatch({
          type: KDS_ACTIONS.UPDATE_ITEM,
          payload: {
            sessionId: payload.sessionId,
            item: payload.data,
          },
        });
        break;

      default:
        console.warn('Unknown event type:', payload.eventType);
    }
  }, []);

  // Connect to WebSocket on mount
  useEffect(() => {
    if (tenantId === null || tenantId === undefined) return;

    let unsubscribeFn = null;
    let canceled = false;

    const loadAndConnect = async () => {
      try {
        console.log('Loading KDS data...');
        dispatch({ type: KDS_ACTIONS.SET_SESSIONS, payload: [] });

        // STEP 1: Load initial sessions via HTTP using proper API client
        // The API client automatically handles auth headers (access_token) and tenant_id
        const sessions = await getKdsSessions();
        console.log('Loaded', sessions.length, 'sessions via HTTP');

        if (canceled) return;

        // Display initial sessions immediately
        dispatch({
          type: KDS_ACTIONS.SET_SESSIONS,
          payload: sessions,
        });

        // STEP 2: Register WebSocket message handler
        unsubscribeFn = KdsWebSocketService.onMessage(handleMessage);

        if (canceled) {
          if (unsubscribeFn) unsubscribeFn();
          return;
        }

        // STEP 3: Connect to WebSocket for real-time updates
        console.log('Connecting to KDS WebSocket for real-time updates...');
        await KdsWebSocketService.connect(tenantId, kitchenAreaId);

        if (canceled) {
          KdsWebSocketService.disconnect();
          if (unsubscribeFn) unsubscribeFn();
          return;
        }

        dispatch({ type: KDS_ACTIONS.SET_CONNECTED });
        console.log('KDS WebSocket connected, ready for real-time updates');
      } catch (error) {
        console.error('Error loading KDS:', error);
        if (!canceled) {
          dispatch({
            type: KDS_ACTIONS.SET_ERROR,
            payload: `Lỗi tải dữ liệu KDS: ${error.message}`,
          });
          dispatch({ type: KDS_ACTIONS.SET_DISCONNECTED });
        }
      }
    };

    loadAndConnect();

    // Cleanup on unmount
    return () => {
      canceled = true;
      if (unsubscribeFn) unsubscribeFn();
      KdsWebSocketService.disconnect();
      dispatch({ type: KDS_ACTIONS.SET_DISCONNECTED });
    };
  }, [tenantId, kitchenAreaId, handleMessage]);

  const value = {
    sessions: state.sessions,
    error: state.error,
    loading: state.loading,
    connected: state.connected,
    dispatch,
  };

  return (
    <KdsContext.Provider value={value}>
      {children}
    </KdsContext.Provider>
  );
}

/**
 * Hook để sử dụng KdsContext
 */
export function useKds() {
  const context = useContext(KdsContext);
  if (!context) {
    throw new Error('useKds must be used within KdsProvider');
  }
  return context;
}
