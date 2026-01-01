import { Inbox } from 'lucide-react';
import './Empty.css';

export function Empty({
    message = 'Không có dữ liệu',
    description,
    icon: Icon = Inbox,
    action
}) {
    return (
        <div className="empty-state">
            <Icon size={48} className="empty-icon" />
            <p className="empty-message">{message}</p>
            {description && <p className="empty-description">{description}</p>}
            {action && <div className="empty-action">{action}</div>}
        </div>
    );
}
