import { Modal, ModalFooter } from './Modal';
import { Button } from './Button';
import { AlertTriangle, AlertCircle } from 'lucide-react';

export function ConfirmModal({
    isOpen,
    onClose,
    onConfirm,
    title,
    message,
    confirmText = 'Xác nhận',
    cancelText = 'Hủy',
    variant = 'primary', // 'primary' | 'danger'
    loading = false,
    description
}) {
    const Icon = variant === 'danger' ? AlertCircle : AlertTriangle;
    const iconColor = variant === 'danger' ? 'var(--danger)' : 'var(--warning)';

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={title}
            maxWidth="400px"
        >
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                <div style={{ display: 'flex', gap: '16px', alignItems: 'flex-start' }}>
                    <div style={{
                        padding: '12px',
                        borderRadius: '50%',
                        backgroundColor: variant === 'danger' ? 'var(--danger-light)' : 'var(--warning-light)',
                        color: iconColor,
                        flexShrink: 0
                    }}>
                        <Icon size={24} />
                    </div>
                    <div>
                        <p style={{ margin: '0 0 8px 0', fontWeight: 500, color: 'var(--text-primary)' }}>
                            {message}
                        </p>
                        {description && (
                            <p style={{ margin: 0, fontSize: '14px', color: 'var(--text-secondary)' }}>
                                {description}
                            </p>
                        )}
                    </div>
                </div>
            </div>

            <ModalFooter>
                <Button
                    variant="secondary"
                    onClick={onClose}
                    disabled={loading}
                >
                    {cancelText}
                </Button>
                <Button
                    variant={variant}
                    onClick={onConfirm}
                    loading={loading}
                >
                    {confirmText}
                </Button>
            </ModalFooter>
        </Modal>
    );
}
