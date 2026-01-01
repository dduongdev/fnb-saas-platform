import React from 'react';
import './Badge.css';

export const Badge = ({ count, max = 99, variant = 'danger', showZero = false, children }) => {
    if (!showZero && count === 0) return children;

    const displayCount = count > max ? `${max}+` : count;

    return (
        <div className="badge-wrapper">
            {children}
            <span className={`badge-indicator badge-${variant}`}>
                {displayCount}
            </span>
        </div>
    );
};
