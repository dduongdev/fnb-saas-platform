import './Input.css';

export function Input({
    label,
    error,
    helper,
    required,
    className = '',
    ...props
}) {
    return (
        <div className={`form-field ${className}`}>
            {label && (
                <label className="form-label">
                    {label}
                    {required && <span className="form-required">*</span>}
                </label>
            )}
            <input
                className={`form-input ${error ? 'form-input-error' : ''}`}
                {...props}
            />
            {error && <span className="form-error">{error}</span>}
            {helper && !error && <span className="form-helper">{helper}</span>}
        </div>
    );
}

export function Textarea({
    label,
    error,
    helper,
    required,
    className = '',
    rows = 4,
    ...props
}) {
    return (
        <div className={`form-field ${className}`}>
            {label && (
                <label className="form-label">
                    {label}
                    {required && <span className="form-required">*</span>}
                </label>
            )}
            <textarea
                className={`form-input form-textarea ${error ? 'form-input-error' : ''}`}
                rows={rows}
                {...props}
            />
            {error && <span className="form-error">{error}</span>}
            {helper && !error && <span className="form-helper">{helper}</span>}
        </div>
    );
}

export function Select({
    label,
    error,
    helper,
    required,
    options = [],
    placeholder,
    className = '',
    children,
    ...props
}) {
    return (
        <div className={`form-field ${className}`}>
            {label && (
                <label className="form-label">
                    {label}
                    {required && <span className="form-required">*</span>}
                </label>
            )}
            <select
                className={`form-input form-select ${error ? 'form-input-error' : ''}`}
                {...props}
            >
                {children ? children : (
                    <>
                        {placeholder && <option value="">{placeholder}</option>}
                        {options.map(opt => (
                            <option key={opt.value} value={opt.value}>
                                {opt.label}
                            </option>
                        ))}
                    </>
                )}
            </select>
            {error && <span className="form-error">{error}</span>}
            {helper && !error && <span className="form-helper">{helper}</span>}
        </div>
    );
}
