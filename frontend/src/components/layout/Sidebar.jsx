import { NavLink, useNavigate } from 'react-router-dom';
import {
    ShoppingCart,
    Grid,
    UtensilsCrossed,
    Layers,
    Users,
    Briefcase,
    BarChart3,
    Settings,
    CreditCard,
    LogOut,
    Store,
    ClipboardList,
    Bell
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { useTenant } from '../../context/TenantContext';
import './Sidebar.css';

const menuItems = [
    {
        section: 'Bán hàng',
        items: [
            { path: '/pos', icon: ShoppingCart, label: 'Bán hàng' },
            { path: '/tables', icon: Grid, label: 'Sơ đồ bàn' },
            { path: '/sessions', icon: ClipboardList, label: 'Phiên phục vụ' },
            { path: '/notifications', icon: Bell, label: 'Thông báo' },
        ]
    },
    {
        section: 'Thực đơn',
        items: [
            { path: '/products', icon: UtensilsCrossed, label: 'Sản phẩm' },
            { path: '/categories', icon: Layers, label: 'Danh mục' },
        ]
    },
    {
        section: 'Quản lý',
        ownerOnly: true,
        items: [
            { path: '/staff', icon: Users, label: 'Nhân viên' },
            { path: '/jobs', icon: Briefcase, label: 'Tuyển dụng' },
            { path: '/reports', icon: BarChart3, label: 'Báo cáo' },
        ]
    },
    {
        section: 'Cài đặt',
        ownerOnly: true,
        items: [
            { path: '/settings', icon: Settings, label: 'Cài đặt quán' },
            { path: '/settings/payment', icon: CreditCard, label: 'Thanh toán' },
        ]
    },
];

export function Sidebar() {
    const navigate = useNavigate();
    const { logout } = useAuth();
    const { tenant, isOwner, clearTenant } = useTenant();

    const handleSwitchTenant = () => {
        clearTenant();
        navigate('/select-tenant');
    };

    return (
        <aside className="sidebar">
            <div className="sidebar-header">
                <Store size={24} />
                <span className="sidebar-brand">{tenant?.name || 'FnB Platform'}</span>
            </div>

            <nav className="sidebar-nav">
                {menuItems.map((section) => {
                    // Skip owner-only sections for non-owners
                    if (section.ownerOnly && !isOwner) return null;

                    return (
                        <div key={section.section} className="sidebar-section">
                            <span className="sidebar-section-title">{section.section}</span>
                            <ul className="sidebar-menu">
                                {section.items.map((item) => (
                                    <li key={item.path}>
                                        <NavLink
                                            to={item.path}
                                            className={({ isActive }) =>
                                                `sidebar-link ${isActive ? 'sidebar-link-active' : ''}`
                                            }
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
                <button className="sidebar-footer-btn" onClick={handleSwitchTenant}>
                    <Store size={18} />
                    <span>Đổi quán</span>
                </button>
                <button className="sidebar-footer-btn sidebar-footer-btn-danger" onClick={logout}>
                    <LogOut size={18} />
                    <span>Đăng xuất</span>
                </button>
            </div>
        </aside>
    );
}
