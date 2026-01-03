import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell, X, ShoppingCart, CreditCard, Plus, Minus, Edit, CheckCircle, AlertTriangle, Users } from 'lucide-react';
import { useNotificationWebSocket } from '../../hooks/useWebSocket';
import { useToast } from '../../context/ToastContext';
import { useTenant } from '../../context/TenantContext';
import { getUnreadCount } from '../../api/notification';
import './NotificationBell.css';

// Notification type to icon mapping
const NOTIFICATION_ICONS = {
    CUSTOMER_ORDER: ShoppingCart,
    NEW_SESSION: Users,
    NEW_ITEM: Plus,
    REMOVE_ITEM: Minus,
    UPDATE_ITEM: Edit,
    SERVE_ITEM: CheckCircle,
    PAYMENT_REQUEST: CreditCard,
    PAYMENT_REQUESTED: CreditCard,
    PAYMENT_SUCCESS: CheckCircle,
    SESSION_CONFIRMED: CheckCircle,
    SESSION_REJECTED: AlertTriangle,
};

/**
 * Global Notification Bell Component
 * 
 * Đặt trong Header/Layout để:
 * 1. Hiển thị badge số notifications chưa đọc
 * 2. Nhận WebSocket notifications và hiển thị toast
 * 3. Navigate đến trang notifications khi click
 */
export function NotificationBell() {
    const navigate = useNavigate();
    const { tenant } = useTenant();
    const toast = useToast();
    const [unreadCount, setUnreadCount] = useState(0);
    const [recentNotifications, setRecentNotifications] = useState([]);
    const [showDropdown, setShowDropdown] = useState(false);

    // Load unread count on mount
    useEffect(() => {
        if (tenant?.id) {
            loadUnreadCount();
        }
    }, [tenant?.id]);

    const loadUnreadCount = async () => {
        try {
            const count = await getUnreadCount();
            setUnreadCount(count || 0);
        } catch (error) {
            console.error('Failed to load unread count:', error);
        }
    };

    // Play notification sound
    const playSound = () => {
        try {
            const audio = new Audio('/assets/sounds/notification.mp3');
            audio.volume = 0.5;
            audio.play().catch(e => console.log('Audio play failed', e));
        } catch (e) {
            console.error('Sound error:', e);
        }
    };

    // Handle incoming WebSocket notification
    const handleNotification = useCallback((message) => {
        console.log('[NotificationBell] Received:', message);
        
        // Increment unread count
        setUnreadCount(prev => prev + 1);

        // Add to recent notifications (keep last 5)
        setRecentNotifications(prev => [{
            id: message.id || Date.now(),
            ...message,
            timestamp: new Date()
        }, ...prev].slice(0, 5));

        // Show toast based on priority
        const priority = message.priority || 'MEDIUM';
        const content = message.content || message.title || 'Thông báo mới';
        
        if (priority === 'HIGH') {
            toast.warning(`🔔 ${content}`, { duration: 10000 });
            // Play sound for high priority
            playSound();
        } else {
            toast.info(content, { duration: 5000 });
        }

        // Play sound for specific types
        if (['CUSTOMER_ORDER', 'PAYMENT_REQUEST', 'PAYMENT_REQUESTED'].includes(message.type)) {
            playSound();
        }
    }, [toast]);

    // Subscribe to WebSocket
    useNotificationWebSocket(handleNotification);

    const handleBellClick = () => {
        // Navigate to notifications page using SPA navigation
        // This preserves the tenant context from the current URL
        navigate('/notifications');
    };

    return (
        <div className="notification-bell-wrapper">
            <button 
                className="notification-bell-btn"
                onClick={handleBellClick}
                title="Thông báo"
            >
                <Bell size={20} />
                {unreadCount > 0 && (
                    <span className="notification-badge">
                        {unreadCount > 99 ? '99+' : unreadCount}
                    </span>
                )}
            </button>
        </div>
    );
}
