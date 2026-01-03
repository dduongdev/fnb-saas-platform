import { useState, useCallback, useEffect } from 'react';
import { useTenant } from '../../context/TenantContext';
import { useToast } from '../../context/ToastContext';
import { useNotificationWebSocket } from '../../hooks/useWebSocket';
import { Card, Button, Empty, Badge } from '../../components/common';
import { PageLayout } from '../../components/layout';
import { Bell, Clock, CheckCircle, AlertTriangle, ShoppingCart, CreditCard, Plus, Minus, Edit, Users, Filter, RefreshCw } from 'lucide-react';
import { getNotifications, markNotificationAsRead, markAllNotificationsAsRead, getUnreadCount } from '../../api/notification';
import './NotificationsPage.css';

// Notification type to icon and color mapping
const NOTIFICATION_CONFIG = {
    CUSTOMER_ORDER: { icon: ShoppingCart, color: 'var(--warning)', label: 'Đơn hàng mới', category: 'order' },
    NEW_SESSION: { icon: Users, color: 'var(--success)', label: 'Mở bàn', category: 'session' },
    NEW_ITEM: { icon: Plus, color: 'var(--primary)', label: 'Thêm món', category: 'order' },
    REMOVE_ITEM: { icon: Minus, color: 'var(--danger)', label: 'Xóa món', category: 'order' },
    UPDATE_ITEM: { icon: Edit, color: 'var(--info)', label: 'Sửa món', category: 'order' },
    SERVE_ITEM: { icon: CheckCircle, color: 'var(--success)', label: 'Mang món', category: 'order' },
    PAYMENT_REQUEST: { icon: CreditCard, color: 'var(--warning)', label: 'Yêu cầu thanh toán', category: 'payment' },
    PAYMENT_REQUESTED: { icon: CreditCard, color: 'var(--warning)', label: 'Yêu cầu thanh toán', category: 'payment' },
    PAYMENT_SUCCESS: { icon: CheckCircle, color: 'var(--success)', label: 'Thanh toán thành công', category: 'payment' },
    SESSION_CONFIRMED: { icon: CheckCircle, color: 'var(--success)', label: 'Xác nhận', category: 'session' },
    SESSION_REJECTED: { icon: AlertTriangle, color: 'var(--danger)', label: 'Từ chối', category: 'session' },
    ATTACH_TABLE: { icon: Users, color: 'var(--info)', label: 'Gộp bàn', category: 'session' },
    DETACH_TABLE: { icon: Users, color: 'var(--info)', label: 'Tách bàn', category: 'session' },
};

const CATEGORIES = [
    { key: 'all', label: 'Tất cả' },
    { key: 'order', label: 'Đơn hàng' },
    { key: 'payment', label: 'Thanh toán' },
    { key: 'session', label: 'Phiên phục vụ' },
];

const PRIORITIES = {
    HIGH: { color: 'var(--danger)', label: 'Khẩn cấp' },
    MEDIUM: { color: 'var(--warning)', label: 'Bình thường' },
    LOW: { color: 'var(--text-secondary)', label: 'Thấp' },
};

export function NotificationsPage() {
    const { tenant } = useTenant();
    const toast = useToast();
    const [notifications, setNotifications] = useState([]);
    const [loading, setLoading] = useState(true);
    const [unreadCount, setUnreadCount] = useState(0);
    const [selectedCategory, setSelectedCategory] = useState('all');
    const [showUnreadOnly, setShowUnreadOnly] = useState(false);

    // Load notifications from API
    const loadNotifications = async () => {
        try {
            setLoading(true);
            const [data, count] = await Promise.all([
                getNotifications(0, 50),
                getUnreadCount()
            ]);
            setNotifications(data.content || []);
            setUnreadCount(count || 0);
        } catch (error) {
            console.error('Failed to load notifications:', error);
            toast.error('Không thể tải thông báo');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadNotifications();
    }, []);

    // Subscribe to tenant notifications via WebSocket
    const handleNotification = useCallback((message) => {
        console.log('Received notification:', message);
        
        // Add new notification to top of list
        setNotifications(prev => [{
            id: message.id || Date.now(),
            ...message,
            isRead: false,
            createdAt: message.createdAt || new Date().toISOString()
        }, ...prev]);

        setUnreadCount(prev => prev + 1);

        // Show toast notification
        const config = NOTIFICATION_CONFIG[message.type] || {};
        const priority = message.priority || 'MEDIUM';
        
        if (priority === 'HIGH') {
            toast.warning(message.content || message.title, { duration: 8000 });
        } else {
            toast.info(message.content || message.title, { duration: 5000 });
        }

        // Play sound
        playNotificationSound();
    }, [toast]);

    useNotificationWebSocket(handleNotification);

    const playNotificationSound = () => {
        try {
            const audio = new Audio('/assets/sounds/notification.mp3');
            audio.play().catch(e => console.log('Audio play failed', e));
        } catch (e) {
            console.error(e);
        }
    };

    const markAsRead = async (id) => {
        try {
            await markNotificationAsRead(id);
            setNotifications(prev => prev.map(n =>
                n.id === id ? { ...n, isRead: true } : n
            ));
            setUnreadCount(prev => Math.max(0, prev - 1));
        } catch (error) {
            console.error('Failed to mark as read:', error);
        }
    };

    const markAllRead = async () => {
        try {
            await markAllNotificationsAsRead();
            setNotifications(prev => prev.map(n => ({ ...n, isRead: true })));
            setUnreadCount(0);
            toast.success('Đã đánh dấu tất cả đã đọc');
        } catch (error) {
            console.error('Failed to mark all as read:', error);
            toast.error('Không thể đánh dấu đã đọc');
        }
    };

    const formatTime = (dateStr) => {
        const date = new Date(dateStr);
        const now = new Date();
        const diffMs = now - date;
        const diffMins = Math.floor(diffMs / 60000);
        const diffHours = Math.floor(diffMs / 3600000);
        const diffDays = Math.floor(diffMs / 86400000);

        if (diffMins < 1) return 'Vừa xong';
        if (diffMins < 60) return `${diffMins} phút trước`;
        if (diffHours < 24) return `${diffHours} giờ trước`;
        if (diffDays < 7) return `${diffDays} ngày trước`;
        
        return new Intl.DateTimeFormat('vi-VN', {
            hour: '2-digit',
            minute: '2-digit',
            day: '2-digit',
            month: '2-digit'
        }).format(date);
    };

    // Filter notifications
    const filteredNotifications = notifications.filter(n => {
        if (showUnreadOnly && n.isRead) return false;
        if (selectedCategory === 'all') return true;
        const config = NOTIFICATION_CONFIG[n.type];
        return config?.category === selectedCategory;
    });

    // Group by priority for HIGH priority items
    const highPriorityNotifs = filteredNotifications.filter(n => n.priority === 'HIGH' && !n.isRead);
    const otherNotifs = filteredNotifications.filter(n => n.priority !== 'HIGH' || n.isRead);

    const headerActions = (
        <div className="header-actions">
            <Button variant="ghost" onClick={loadNotifications} title="Làm mới">
                <RefreshCw size={18} />
            </Button>
            <Button variant="outline" onClick={markAllRead} disabled={unreadCount === 0}>
                Đánh dấu tất cả đã đọc
            </Button>
        </div>
    );

    const NotificationItem = ({ notif, onClick }) => {
        const config = NOTIFICATION_CONFIG[notif.type] || { icon: Bell, color: 'var(--primary)', label: notif.type };
        const IconComponent = config.icon;
        const priorityConfig = PRIORITIES[notif.priority] || PRIORITIES.MEDIUM;

        return (
            <div
                className={`notification-item ${notif.isRead ? 'read' : 'unread'} ${notif.type?.toLowerCase()} priority-${(notif.priority || 'medium').toLowerCase()}`}
                onClick={() => onClick(notif)}
            >
                <div className="notif-icon" style={{ backgroundColor: `${config.color}20`, color: config.color }}>
                    <IconComponent size={20} />
                </div>
                <div className="notif-content">
                    <div className="notif-header">
                        <span className="notif-type-badge" style={{ color: config.color }}>
                            {config.label}
                        </span>
                        {notif.priority === 'HIGH' && (
                            <span className="priority-badge high">Khẩn cấp</span>
                        )}
                    </div>
                    <h4 className="notif-message">{notif.content || notif.title}</h4>
                    <div className="notif-meta">
                        <span className="notif-time">
                            <Clock size={14} />
                            {formatTime(notif.createdAt)}
                        </span>
                        {notif.tableName && (
                            <span className="notif-table">
                                🪑 {notif.tableName}
                            </span>
                        )}
                    </div>
                </div>
                {!notif.isRead && (
                    <div className="read-indicator"></div>
                )}
            </div>
        );
    };

    return (
        <PageLayout
            title={`Thông báo ${unreadCount > 0 ? `(${unreadCount} chưa đọc)` : ''}`}
            icon={<Bell className="text-primary" />}
            actions={headerActions}
        >
            <div className="notifications-page">
                {/* Filter Bar */}
                <div className="filter-bar">
                    <div className="category-tabs">
                        {CATEGORIES.map(cat => (
                            <button
                                key={cat.key}
                                className={`category-tab ${selectedCategory === cat.key ? 'active' : ''}`}
                                onClick={() => setSelectedCategory(cat.key)}
                            >
                                {cat.label}
                            </button>
                        ))}
                    </div>
                    <label className="unread-filter">
                        <input
                            type="checkbox"
                            checked={showUnreadOnly}
                            onChange={(e) => setShowUnreadOnly(e.target.checked)}
                        />
                        Chỉ hiện chưa đọc
                    </label>
                </div>

                {/* High Priority Section */}
                {highPriorityNotifs.length > 0 && (
                    <div className="notification-section urgent">
                        <h3 className="section-title">
                            <AlertTriangle size={18} />
                            Cần xử lý ngay ({highPriorityNotifs.length})
                        </h3>
                        <div className="notifications-list">
                            {highPriorityNotifs.map(notif => (
                                <NotificationItem
                                    key={notif.id}
                                    notif={notif}
                                    onClick={(n) => markAsRead(n.id)}
                                />
                            ))}
                        </div>
                    </div>
                )}

                {/* Other Notifications */}
                <div className="notification-section">
                    {highPriorityNotifs.length > 0 && otherNotifs.length > 0 && (
                        <h3 className="section-title">Thông báo khác</h3>
                    )}
                    <div className="notifications-list">
                        {loading ? (
                            <div className="loading-state">Đang tải...</div>
                        ) : filteredNotifications.length === 0 ? (
                            <Empty 
                                title="Chưa có thông báo nào" 
                                description={showUnreadOnly ? "Không có thông báo chưa đọc" : "Thông báo sẽ xuất hiện khi có hoạt động mới"}
                            />
                        ) : (
                            otherNotifs.map(notif => (
                                <NotificationItem
                                    key={notif.id}
                                    notif={notif}
                                    onClick={(n) => markAsRead(n.id)}
                                />
                            ))
                        )}
                    </div>
                </div>
            </div>
        </PageLayout>
    );
}
