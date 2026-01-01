import { useEffect } from 'react';
import { X } from 'lucide-react';
import './Modal.css';

export function Modal({
    isOpen,
    onClose,
    title,
    children,
    size = 'md',
    showClose = true
}) {
    // Close on Escape key
    useEffect(() => {
        const handleEscape = (e) => {
            if (e.key === 'Escape') onClose?.();
        };

        if (isOpen) {
            document.addEventListener('keydown', handleEscape);
            document.body.style.overflow = 'hidden';
        }

        return () => {
            document.removeEventListener('keydown', handleEscape);
            document.body.style.overflow = '';
        };
    }, [isOpen, onClose]);

    if (!isOpen) return null;

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div
                className={`modal modal-${size}`}
                onClick={(e) => e.stopPropagation()}
            >
                {title && (
                    <div className="modal-header">
                        <h2 className="modal-title">{title}</h2>
                        {showClose && (
                            <button className="modal-close" onClick={onClose}>
                                <X size={20} />
                            </button>
                        )}
                    </div>
                )}
                <div className="modal-body">
                    {children}
                </div>
            </div>
        </div>
    );
}

export function ModalFooter({ children }) {
    return <div className="modal-footer">{children}</div>;
}
