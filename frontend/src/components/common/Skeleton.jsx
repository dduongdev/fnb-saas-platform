import './Skeleton.css';

/**
 * Skeleton loading component for better UX
 */
export function Skeleton({ 
    width = '100%', 
    height = '20px', 
    variant = 'rect',
    className = '',
    count = 1,
    ...props 
}) {
    const skeletons = Array.from({ length: count }, (_, i) => (
        <div
            key={i}
            className={`skeleton skeleton-${variant} ${className}`}
            style={{ width, height }}
            {...props}
        />
    ));

    return count > 1 ? <>{skeletons}</> : skeletons[0];
}

export function TableSkeleton({ rows = 5 }) {
    return (
        <div className="skeleton-table">
            <div className="skeleton-table-header">
                <Skeleton height="40px" />
            </div>
            <div className="skeleton-table-body">
                {Array.from({ length: rows }, (_, i) => (
                    <div key={i} className="skeleton-table-row">
                        <Skeleton height="60px" />
                    </div>
                ))}
            </div>
        </div>
    );
}

export function CardSkeleton({ count = 1 }) {
    return (
        <div className="skeleton-cards">
            {Array.from({ length: count }, (_, i) => (
                <div key={i} className="skeleton-card">
                    <Skeleton variant="rect" height="200px" className="skeleton-card-image" />
                    <div className="skeleton-card-content">
                        <Skeleton height="24px" width="70%" />
                        <Skeleton height="16px" width="40%" />
                    </div>
                </div>
            ))}
        </div>
    );
}
