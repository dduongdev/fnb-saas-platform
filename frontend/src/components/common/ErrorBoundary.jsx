import { Component } from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';
import { Button } from './Button';
import './ErrorBoundary.css';

/**
 * Error Boundary - Catches and displays React errors gracefully
 */
export class ErrorBoundary extends Component {
    constructor(props) {
        super(props);
        this.state = {
            hasError: false,
            error: null,
            errorInfo: null
        };
    }

    static getDerivedStateFromError(error) {
        return { hasError: true };
    }

    componentDidCatch(error, errorInfo) {
        console.error('ErrorBoundary caught an error:', error, errorInfo);
        this.setState({
            error,
            errorInfo
        });
    }

    handleReset = () => {
        this.setState({
            hasError: false,
            error: null,
            errorInfo: null
        });
    };

    render() {
        if (this.state.hasError) {
            return (
                <div className="error-boundary">
                    <div className="error-boundary-content">
                        <AlertTriangle size={64} className="error-icon" />
                        <h1>Đã có lỗi xảy ra</h1>
                        <p className="error-message">
                            {this.state.error?.message || 'Một lỗi không mong muốn đã xảy ra'}
                        </p>
                        
                        {process.env.NODE_ENV === 'development' && this.state.errorInfo && (
                            <details className="error-details">
                                <summary>Chi tiết lỗi (Development only)</summary>
                                <pre>{this.state.errorInfo.componentStack}</pre>
                            </details>
                        )}
                        
                        <div className="error-actions">
                            <Button 
                                variant="primary" 
                                icon={<RefreshCw size={18} />}
                                onClick={() => window.location.reload()}
                            >
                                Tải lại trang
                            </Button>
                            <Button 
                                variant="secondary"
                                onClick={this.handleReset}
                            >
                                Thử lại
                            </Button>
                        </div>
                    </div>
                </div>
            );
        }

        return this.props.children;
    }
}
