import { Sidebar } from './Sidebar';
import { Header } from './Header';
import './PageLayout.css';

export function PageLayout({ title, children, actions }) {
    return (
        <div className="page-layout">
            <Sidebar />
            <div className="page-main">
                <Header title={title} />
                <main className="page-content">
                    {actions && (
                        <div className="page-actions">
                            {actions}
                        </div>
                    )}
                    {children}
                </main>
            </div>
        </div>
    );
}
