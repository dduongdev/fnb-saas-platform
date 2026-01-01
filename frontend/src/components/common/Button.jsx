import { motion } from 'framer-motion';
import './Button.css';

/**
 * Enhanced Button Component with animations and accessibility
 * @param {Object} props
 * @param {'primary'|'secondary'|'danger'|'success'|'ghost'} props.variant
 * @param {'xs'|'sm'|'md'|'lg'} props.size
 * @param {boolean} props.disabled
 * @param {boolean} props.loading
 * @param {React.ReactNode} props.icon
 * @param {Function} props.onClick
 * @param {string} props.className
 */
export function Button({
    children,
    variant = 'primary',
    size = 'md',
    disabled = false,
    loading = false,
    icon,
    onClick,
    type = 'button',
    className = '',
    fullWidth = false,
    ...props
}) {
    const handleClick = (e) => {
        if (!disabled && !loading && onClick) {
            onClick(e);
        }
    };

    return (
        <motion.button
            type={type}
            className={`btn btn-${variant} btn-${size} ${fullWidth ? 'btn-fullwidth' : ''} ${className}`}
            disabled={disabled || loading}
            onClick={handleClick}
            whileTap={!disabled && !loading ? { scale: 0.95 } : {}}
            whileHover={!disabled && !loading ? { scale: 1.02 } : {}}
            transition={{ duration: 0.1 }}
            {...props}
        >
            {loading && (
                <span className="btn-spinner" aria-label="Loading">
                    <span className="spinner-dot"></span>
                    <span className="spinner-dot"></span>
                    <span className="spinner-dot"></span>
                </span>
            )}
            {!loading && icon && <span className="btn-icon">{icon}</span>}
            {!loading && children && <span className="btn-text">{children}</span>}
            {loading && <span className="btn-text btn-text-loading">Đang xử lý...</span>}
        </motion.button>
    );
}
