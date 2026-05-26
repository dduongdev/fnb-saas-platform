/**
 * Service quản lý WebSocket connection cho Kitchen Display System (KDS).
 * 
 * Features:
 * - Connect/disconnect từ WebSocket server
 * - Subscribe to KDS updates via STOMP
 * - Handle incoming messages
 * - Auto-reconnect logic (optional)
 * 
 * @author FNB Team
 * @version 2.0
 */

import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8081';
const WS_URL_ENV = import.meta.env.VITE_WS_URL;

const WS_HTTP_BASE_URL = WS_URL_ENV
  ? WS_URL_ENV.replace(/^ws:/, 'http:').replace(/^wss:/, 'https:')
  : API_BASE_URL;

const WS_ENDPOINT = `${WS_HTTP_BASE_URL.replace(/\/+$/, '')}/ws/kds`;

class KdsWebSocketService {
  constructor() {
    this.client = null;
    this.connected = false;
    this.subscription = null;
    this.messageHandlers = [];
    this.connectionPromise = null;
    this.tenantId = null;
    this.connectInProgress = false;  // Prevent duplicate connection attempts
  }

  /**
   * Connect tới WebSocket server.
   * 
   * @param {number} tenantId - Tenant ID
   * @returns {Promise} - Resolves khi connected
   */
  async connect(tenantId) {
    // Prevent duplicate connection attempts
    if (this.connectInProgress) {
      console.log('Connection already in progress');
      return this.connectionPromise;
    }

    // If already connected with same parameters, return immediately
    if (this.connected && this.tenantId === tenantId) {
      console.log('Already connected with same parameters');
      return Promise.resolve();
    }

    // Disconnect any existing connection with different parameters
    if (this.connected && this.tenantId !== tenantId) {
      console.log('Disconnecting previous connection due to parameter change');
      this.disconnect();
    }

    this.connectInProgress = true;

    this.connectionPromise = new Promise((resolve, reject) => {
      try {
        this.tenantId = tenantId;

        this.client = new Client({
          // STOMP over SockJS (backend endpoint expects /ws/kds via SockJS)
          connectHeaders: {
            'Authorization': `Bearer ${localStorage.getItem('token') || ''}`,
            'X-Access-Key': localStorage.getItem('pos_access_key') || '',
          },
          onConnect: () => {
            console.log('WebSocket connected');
            this.connected = true;
            this.connectInProgress = false;
            // Give internal STOMP state a tick before publish/subscribe operations
            setTimeout(() => this.subscribe(), 0);
            resolve();
          },
          onDisconnect: () => {
            console.log('WebSocket disconnected');
            this.connected = false;
            this.connectInProgress = false;
          },
          onStompError: (frame) => {
            console.error('STOMP error:', frame);
            this.connectInProgress = false;
            reject(new Error('STOMP connection error'));
          },
          reconnectDelay: 5000,
          heartbeatIncoming: 4000,
          heartbeatOutgoing: 4000,
        });

        // Use SockJS transport
        this.client.webSocketFactory = () => new SockJS(WS_ENDPOINT);

        this.client.activate();
      } catch (error) {
        console.error('Error connecting WebSocket:', error);
        this.connectInProgress = false;
        reject(error);
      }
    });

    return this.connectionPromise;
  }

  /**
   * Subscribe tới KDS topic và register message handler.
   */
  subscribe() {
    if (!this.client || !this.client.active || !this.client.connected) {
      console.warn('STOMP client not ready yet, will retry');
      // Retry after a delay if not ready
      setTimeout(() => this.subscribe(), 500);
      return;
    }

    try {
      // Send subscription request tới backend
      const destination = `/app/kds/subscribe/${this.tenantId}`;
      
      console.log('Subscribing to:', destination);
      
      this.client.publish({
        destination: destination,
        body: JSON.stringify({
          tenantId: this.tenantId,
        }),
      });

      // Subscribe tới topic để receive updates
      const topic = `/topic/kds/${this.tenantId}`;
      console.log('Subscribing to topic:', topic);

      const subscription = this.client.subscribe(topic, (message) => {
        try {
          const payload = JSON.parse(message.body);
          console.log('Received KDS update:', payload);
          // Notify all registered handlers
          this.messageHandlers.forEach((handler) => {
            handler(payload);
          });
        } catch (error) {
          console.error('Error parsing message:', error);
        }
      });

      this.subscription = [subscription];
    } catch (error) {
      console.error('Error subscribing:', error);
    }
  }

  /**
   * Register a message handler callback.
   * 
   * @param {Function} handler - Callback function to handle messages
   * @returns {Function} - Unsubscribe function
   */
  onMessage(handler) {
    this.messageHandlers.push(handler);
    
    // Return unsubscribe function
    return () => {
      const index = this.messageHandlers.indexOf(handler);
      if (index > -1) {
        this.messageHandlers.splice(index, 1);
      }
    };
  }

  /**
   * Disconnect từ WebSocket server.
   */
  disconnect() {
    if (this.connectInProgress) {
      console.log('Connection in progress, waiting before disconnect');
      this.connectInProgress = false;  // Mark as should not process onConnect
    }

    if (this.subscription) {
      if (Array.isArray(this.subscription)) {
        this.subscription.forEach((sub) => {
          try {
            sub.unsubscribe();
          } catch (e) {
            console.warn('Error unsubscribing:', e);
          }
        });
      } else {
        try {
          this.subscription.unsubscribe();
        } catch (e) {
          console.warn('Error unsubscribing:', e);
        }
      }
      this.subscription = null;
    }

    if (this.client) {
      try {
        // Only deactivate if not already disconnected
        if (this.client.connected || this.client.active) {
          this.client.deactivate();
        }
      } catch (e) {
        console.warn('Error deactivating client:', e);
      }
      this.client = null;
      this.connected = false;
    }
  }

  /**
   * Check nếu client đã connected.
   * 
   * @returns {boolean}
   */
  isConnected() {
    return this.connected && this.client && this.client.connected;
  }
}

// Export singleton instance
export default new KdsWebSocketService();
