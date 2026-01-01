import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { TenantProvider, useTenant } from './context/TenantContext';
import { ToastProvider } from './context/ToastContext';
import { Loading } from './components/common';

// Pages
import { SelectTenantPage } from './pages/auth/SelectTenantPage';
import { POSPage } from './pages/pos/POSPage';
import { TableGridPage } from './pages/pos/TableGridPage';
import { SessionListPage } from './pages/pos/SessionListPage';
import { OrderSessionPage } from './pages/pos/OrderSessionPage';
import { ProductListPage } from './pages/menu/ProductListPage';
import { CategoryListPage } from './pages/menu/CategoryListPage';
import { StaffListPage } from './pages/hrm/StaffListPage';
import { ReportsPage } from './pages/reports/ReportsPage';
import { TenantSettingsPage } from './pages/settings/TenantSettingsPage';
import { PaymentSettingsPage } from './pages/settings/PaymentSettingsPage';
import { JobListPage } from './pages/hrm/JobListPage';
import { ApplicationListPage } from './pages/hrm/ApplicationListPage';
import { CustomerMenuPage } from './pages/customer/CustomerMenuPage';
import { UserProfilePage } from './pages/auth/UserProfilePage';
import { PublicTenantListPage } from './pages/auth/PublicTenantListPage';
import { NotificationsPage } from './pages/notifications/NotificationsPage';

import './styles/global.css';

// Protected Route wrapper - Shows login page instead of auto-redirecting
function ProtectedRoute({ children }) {
  const { isAuthenticated, loading, initialized, login } = useAuth();

  if (!initialized || loading) {
    return <Loading fullPage text="Đang xác thực..." />;
  }

  if (!isAuthenticated) {
    // Show a login page instead of auto-redirecting
    return (
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        height: '100vh',
        gap: '24px',
        backgroundColor: 'var(--bg-main)',
      }}>
        <h1 style={{ fontSize: '24px', fontWeight: 600, color: 'var(--text-primary)' }}>
          F&B Management
        </h1>
        <p style={{ color: 'var(--text-secondary)' }}>
          Vui lòng đăng nhập để tiếp tục
        </p>
        <button
          onClick={login}
          style={{
            padding: '12px 32px',
            backgroundColor: 'var(--primary)',
            color: 'white',
            border: 'none',
            borderRadius: '6px',
            fontSize: '16px',
            fontWeight: 500,
            cursor: 'pointer',
          }}
        >
          Đăng nhập
        </button>
      </div>
    );
  }

  return children;
}

// Tenant Required Route wrapper
function TenantRoute({ children }) {
  const { tenant, loading } = useTenant();

  if (loading) {
    return <Loading fullPage text="Đang tải..." />;
  }

  if (!tenant) {
    return <Navigate to="/select-tenant" replace />;
  }

  return children;
}

// Owner Only Route wrapper
function OwnerRoute({ children }) {
  const { isOwner } = useTenant();

  if (!isOwner) {
    return <Navigate to="/pos" replace />;
  }

  return children;
}

function AppRoutes() {
  return (
    <Routes>
      {/* Public Routes */}
      <Route path="/shops" element={<PublicTenantListPage />} />
      <Route path="/table/:tableId" element={<CustomerMenuPage />} />
      <Route path="/menu/:tenantId/:tableId" element={<CustomerMenuPage />} />

      {/* Auth Routes */}
      <Route
        path="/select-tenant"
        element={
          <ProtectedRoute>
            <SelectTenantPage />
          </ProtectedRoute>
        }
      />

      {/* POS Routes */}
      <Route
        path="/pos"
        element={
          <ProtectedRoute>
            <TenantRoute>
              <POSPage />
            </TenantRoute>
          </ProtectedRoute>
        }
      />
      <Route
        path="/notifications"
        element={
          <ProtectedRoute>
            <TenantRoute>
              <NotificationsPage />
            </TenantRoute>
          </ProtectedRoute>
        }
      />
      <Route
        path="/tables"
        element={
          <ProtectedRoute>
            <TenantRoute>
              <TableGridPage />
            </TenantRoute>
          </ProtectedRoute>
        }
      />
      <Route
        path="/sessions"
        element={
          <ProtectedRoute>
            <TenantRoute>
              <SessionListPage />
            </TenantRoute>
          </ProtectedRoute>
        }
      />
      <Route
        path="/session/:sessionId"
        element={
          <ProtectedRoute>
            <TenantRoute>
              <OrderSessionPage />
            </TenantRoute>
          </ProtectedRoute>
        }
      />

      {/* Menu Routes */}
      <Route
        path="/products"
        element={
          <ProtectedRoute>
            <TenantRoute>
              <ProductListPage />
            </TenantRoute>
          </ProtectedRoute>
        }
      />
      <Route
        path="/categories"
        element={
          <ProtectedRoute>
            <TenantRoute>
              <CategoryListPage />
            </TenantRoute>
          </ProtectedRoute>
        }
      />

      {/* HRM Routes (Owner Only) */}
      <Route
        path="/staff"
        element={
          <ProtectedRoute>
            <TenantRoute>
              <OwnerRoute>
                <StaffListPage />
              </OwnerRoute>
            </TenantRoute>
          </ProtectedRoute>
        }
      />
      <Route
        path="/jobs"
        element={
          <ProtectedRoute>
            <TenantRoute>
              <OwnerRoute>
                <JobListPage />
              </OwnerRoute>
            </TenantRoute>
          </ProtectedRoute>
        }
      />
      <Route
        path="/jobs/:jobId/applications"
        element={
          <ProtectedRoute>
            <TenantRoute>
              <OwnerRoute>
                <ApplicationListPage />
              </OwnerRoute>
            </TenantRoute>
          </ProtectedRoute>
        }
      />

      {/* Settings Routes (Owner Only) */}
      <Route
        path="/settings"
        element={
          <ProtectedRoute>
            <TenantRoute>
              <OwnerRoute>
                <TenantSettingsPage />
              </OwnerRoute>
            </TenantRoute>
          </ProtectedRoute>
        }
      />
      <Route
        path="/settings/payment"
        element={
          <ProtectedRoute>
            <TenantRoute>
              <OwnerRoute>
                <PaymentSettingsPage />
              </OwnerRoute>
            </TenantRoute>
          </ProtectedRoute>
        }
      />

      {/* Reports (Owner Only) */}
      <Route
        path="/reports"
        element={
          <ProtectedRoute>
            <TenantRoute>
              <OwnerRoute>
                <ReportsPage />
              </OwnerRoute>
            </TenantRoute>
          </ProtectedRoute>
        }
      />

      {/* Default redirect */}
      <Route path="/" element={<Navigate to="/pos" replace />} />
      <Route path="*" element={<Navigate to="/pos" replace />} />
    </Routes>
  );
}

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ToastProvider>
          <TenantProvider>
            <AppRoutes />
          </TenantProvider>
        </ToastProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
