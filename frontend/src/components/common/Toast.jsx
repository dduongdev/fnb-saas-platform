import { X, CheckCircle, AlertCircle, Info, AlertTriangle } from 'lucide-react';
import './Toast.css';

const icons = {
    success: <CheckCircle size={20} />,
    error: <AlertCircle size={20} />,
    info: <Info size={20} />,
    warning: <AlertTriangle size={20} />
};

export function Toast({ message, type = 'info', onClose }) {
    return (
        <div className={`toast toast-${type}`}>
            <div className="toast-icon">
                {icons[type]}
            </div>
            <div className="toast-message">
                {message}
            </div>
            <button className="toast-close" onClick={onClose}>
                <X size={16} />
            </button>
        </div>
    );
}
