import { useState, useCallback } from 'react';
import { useTenant } from '../../context/TenantContext';
import { useNotificationWebSocket } from '../../hooks/useWebSocket';
import { Card, Button, Empty, Badge } from '../../components/common';
import { PageLayout } from '../../components/layout';
import { Bell, Clock, CheckCircle } from 'lucide-react';
import './NotificationsPage.css';

export function NotificationsPage() {
    const { tenant } = useTenant();
    const [notifications, setNotifications] = useState([]);

    // Subscribe to tenant notifications
    const handleNotification = useCallback((message) => {
        console.log('Received notification:', message);
        // Add new notification to top of list
        setNotifications(prev => [{
            id: Date.now(), // Generate temp ID if not from server
            ...message,
            read: false,
            timestamp: new Date()
        }, ...prev]);

        // Play sound
        playNotificationSound();
    }, []);

    useNotificationWebSocket(handleNotification);

    const playNotificationSound = () => {
        try {
            const audio = new Audio('/assets/sounds/notification.mp3');
            audio.play().catch(e => console.log('Audio play failed', e));
        } catch (e) {
            console.error(e);
        }
    };

    const markAsRead = (id) => {
        setNotifications(prev => prev.map(n =>
            n.id === id ? { ...n, read: true } : n
        ));
    };

    const markAllRead = () => {
        setNotifications(prev => prev.map(n => ({ ...n, read: true })));
    };

    const formatTime = (date) => {
        return new Intl.DateTimeFormat('vi-VN', {
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        }).format(date);
    };

    const headerActions = (
        <Button variant="outline" onClick={markAllRead}>
            Đánh dấu tất cả đã đọc
        </Button>
    );

    return (
        <PageLayout
            title="Thông báo"
            icon={<Bell className="text-primary" />}
            actions={headerActions}
        >
            <div className="notifications-page">
                <div className="notifications-list">
                    {notifications.length === 0 ? (
                        <Empty title="Chưa có thông báo nào" />
                    ) : (
                        notifications.map(notif => (
                            <div
                                key={notif.id}
                                className={`notification-item ${notif.read ? 'read' : 'unread'} ${notif.type?.toLowerCase()}`}
                                onClick={() => markAsRead(notif.id)}
                            >
                                <div className="notif-icon">
                                    <Bell size={20} />
                                </div>
                                <div className="notif-content">
                                    <h4 className="notif-message">{notif.message ?? notif.content}</h4>
                                    <div className="notif-meta">
                                        <span className="notif-time">
                                            <Clock size={14} />
                                            {formatTime(notif.timestamp)}
                                        </span>
                                        {notif.tableName && (
                                            <span className="notif-table">
                                                {notif.tableName}
                                            </span>
                                        )}
                                    </div>
                                </div>
                                {!notif.read && (
                                    <div className="read-indicator"></div>
                                )}
                            </div>
                        ))
                    )}
                </div>
            </div>
        </PageLayout>
    );
}
