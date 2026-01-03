import { User, ChevronDown } from 'lucide-react';
import { useState, useRef, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { NotificationBell } from '../common';
import './Header.css';

export function Header({ title }) {
    const { user, logout } = useAuth();
    const [dropdownOpen, setDropdownOpen] = useState(false);
    const dropdownRef = useRef(null);

    // Close dropdown when clicking outside
    useEffect(() => {
        const handleClickOutside = (e) => {
            if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
                setDropdownOpen(false);
            }
        };

        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    return (
        <header className="header">
            <h1 className="header-title">{title}</h1>

            <div className="header-right">
                {/* Notification Bell */}
                <NotificationBell />

                {/* User Menu */}
                <div className="header-user" ref={dropdownRef}>
                    <button
                        className="header-user-btn"
                        onClick={() => setDropdownOpen(!dropdownOpen)}
                    >
                        {user?.avatarUrl ? (
                            <img src={user.avatarUrl} alt="Avatar" className="header-avatar" />
                        ) : (
                            <div className="header-avatar-placeholder">
                                <User size={18} />
                            </div>
                        )}
                        <span className="header-username">{user?.fullName || user?.username}</span>
                        <ChevronDown size={16} />
                    </button>

                    {dropdownOpen && (
                        <div className="header-dropdown">
                            <div className="header-dropdown-info">
                                <span className="header-dropdown-name">{user?.fullName}</span>
                                <span className="header-dropdown-email">{user?.email}</span>
                            </div>
                            <hr className="header-dropdown-divider" />
                            <button className="header-dropdown-item" onClick={() => window.location.href = '/profile'}>
                                Hồ sơ cá nhân
                            </button>
                            <button className="header-dropdown-item" onClick={logout}>
                                Đăng xuất
                            </button>
                        </div>
                    )}
                </div>
            </div>
        </header>
    );
}
