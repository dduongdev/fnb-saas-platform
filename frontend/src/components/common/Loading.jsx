import './Loading.css';

export function Loading({ text = 'Đang tải...', fullPage = false }) {
    if (fullPage) {
        return (
            <div className="loading-fullpage">
                <div className="loading-content">
                    <div className="loading-spinner" />
                    <span className="loading-text">{text}</span>
                </div>
            </div>
        );
    }

    return (
        <div className="loading-inline">
            <div className="loading-spinner" />
            <span className="loading-text">{text}</span>
        </div>
    );
}
