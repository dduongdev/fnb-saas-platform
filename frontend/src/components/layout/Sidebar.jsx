import { NavLink, useNavigate } from 'react-router-dom';
import {
    ShoppingCart,
    Grid,
    UtensilsCrossed,
    Layers,
    BarChart3,
    Settings,
    CreditCard,
    LogOut,
    Store,
    ClipboardList,
    Bell,
    AppWindow
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { useTenant } from '../../context/TenantContext';
import './Sidebar.css';

const menuItems = [
    {
        section: 'Bán hàng',
        items: [
            { path: '/pos', icon: ShoppingCart, label: 'Bán hàng' },
            { path: '/kds', icon: AppWindow, label: 'Màn hình bếp (KDS)' },
            { path: '/tables', icon: Grid, label: 'Sơ đồ bàn' },
            { path: '/sessions', icon: ClipboardList, label: 'Phiên phục vụ' },
            { path: '/notifications', icon: Bell, label: 'Thông báo' },
        ]
    },
    {
        section: 'Thực đơn',
        ownerOnly: true,
        items: [
            { path: '/products', icon: UtensilsCrossed, label: 'Sản phẩm' },
            { path: '/categories', icon: Layers, label: 'Danh mục' },
        ]
    },
    {
        section: 'Quản lý',
        ownerOnly: true,
        items: [
            { path: '/reports', icon: BarChart3, label: 'Báo cáo' },
        ]
    },
    {
        section: 'Cài đặt',
        ownerOnly: true,
        items: [
            { path: '/settings', icon: Settings, label: 'Cài đặt quán' },
            { path: '/settings/payment', icon: CreditCard, label: 'Thanh toán' },
            { path: '/settings/access-keys', icon: AppWindow, label: 'Khoá truy cập' },            { path: '/settings/audit', icon: ClipboardList, label: 'Audit POS' },        ]
    },
];

export function Sidebar() {
    const navigate = useNavigate();
    const { logout, user } = useAuth();
    const { tenant, isOwner, clearTenant } = useTenant();

    const isWaitstaff = user?.isWaitstaff;
    const isKitchen = user?.accessKeyRole === 'KITCHEN';

    const handleSwitchTenant = () => {
        clearTenant();
        navigate('/dashboard');
    };

    return (
        <aside className="sidebar">
            <div className="sidebar-header">
                <Store size={24} />
                <span className="sidebar-brand">{tenant?.name || 'FnB Platform'}</span>
            </div>

            <nav className="sidebar-nav">
                {(isKitchen ? [{
                    section: 'Bếp',
                    items: [
                        { path: '/kds', icon: AppWindow, label: 'Màn hình bếp (KDS)' }
                    ]
                }] : menuItems).map((section) => {
                    if (section.ownerOnly && !isOwner) return null;

                    return (
                        <div key={section.section} className="sidebar-section">
                            <span className="sidebar-section-title">{section.section}</span>
                            <ul className="sidebar-menu">
                                {section.items.map((item) => (
                                    <li key={item.path}>
                                        <NavLink
                                            to={item.path}
                                            end
                                            className={({ isActive }) =>
                                                `sidebar-link ${isActive ? 'sidebar-link-active' : ''}`
                                            }
                                            onMouseDown={(e) => {
                                                // Prevent browser from assigning focus and auto-scrolling on click
                                                e.preventDefault();
                                            }}
                                            onClick={(e) => {
                                                e.currentTarget.blur();
                                            }}
                                        >
                                            <item.icon size={20} />
                                            <span>{item.label}</span>
                                        </NavLink>
                                    </li>
                                ))}
                            </ul>
                        </div>
                    );
                })}
            </nav>

            <div className="sidebar-footer">
                {!isWaitstaff && (
                    <button className="sidebar-footer-btn" onClick={handleSwitchTenant}>
                        <Store size={18} />
                        <span>Đổi quán</span>
                    </button>
                )}
                {isKitchen && (
                    <div className="sidebar-footer-note">Chỉ truy cập KDS</div>
                )}
                <button className="sidebar-footer-btn sidebar-footer-btn-danger" onClick={logout}>
                    <LogOut size={18} />
                    <span>Đăng xuất</span>
                </button>
            </div>
        </aside>
    );
}
