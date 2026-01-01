import './Card.css';

export function Card({
    children,
    className = '',
    padding = true,
    onClick,
    ...props
}) {
    return (
        <div
            className={`card ${padding ? 'card-padded' : ''} ${onClick ? 'card-clickable' : ''} ${className}`}
            onClick={onClick}
            {...props}
        >
            {children}
        </div>
    );
}

export function CardHeader({ children, className = '' }) {
    return (
        <div className={`card-header ${className}`}>
            {children}
        </div>
    );
}

export function CardTitle({ children, className = '' }) {
    return (
        <h3 className={`card-title ${className}`}>
            {children}
        </h3>
    );
}

export function CardContent({ children, className = '' }) {
    return (
        <div className={`card-content ${className}`}>
            {children}
        </div>
    );
}
