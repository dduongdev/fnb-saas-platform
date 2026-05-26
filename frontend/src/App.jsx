import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { TenantProvider, useTenant } from './context/TenantContext';
import { ToastProvider } from './context/ToastContext';
import { KdsProvider } from './context/KdsContext';
import { Loading } from './components/common';

// Pages
import { DashboardPage } from './pages/dashboard';
import { POSPage } from './pages/pos/POSPage';
import { TableGridPage } from './pages/pos/TableGridPage';
import { SessionListPage } from './pages/pos/SessionListPage';
import { SessionHistoryPage } from './pages/pos/SessionHistoryPage';
import { SessionDetailPage } from './pages/pos/SessionDetailPage';
import { ProductListPage } from './pages/menu/ProductListPage';
import { CategoryListPage } from './pages/menu/CategoryListPage';
import { ReportsPage } from './pages/reports/ReportsPage';
import { TenantSettingsPage } from './pages/settings/TenantSettingsPage';
import { PaymentSettingsPage } from './pages/settings/PaymentSettingsPage';
import { AccessKeySettingsPage } from './pages/settings/AccessKeySettingsPage';
import { PosAuditPage } from './pages/settings/PosAuditPage';
import { MenuPage } from './pages/customer/MenuPage';
import { CustomerMenuPage } from './pages/customer/CustomerMenuPage';
import { AccessKeyLoginPage } from './pages/auth/AccessKeyLoginPage';
import { LoginPage } from './pages/auth/LoginPage';
import { RegisterPage } from './pages/auth/RegisterPage';
import { PublicTenantListPage } from './pages/auth/PublicTenantListPage';
import { NotificationsPage } from './pages/notifications/NotificationsPage';
import KdsPage from './pages/kds/KdsPage';

import './styles/global.css';

function ProtectedRoute({ children }) {
  const { isAuthenticated, loading, initialized, isKitchen } = useAuth();
  const location = useLocation();

  if (!initialized || loading) {
    return <Loading fullPage text="Đang xác thực..." />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (isKitchen && location.pathname !== '/kds') {
    return <Navigate to="/kds" replace />;
  }

  return children;
}

// Tenant Required Route wrapper
function TenantRoute({ children }) {
  const { tenant, loading } = useTenant();
  const { isWaitstaff } = useAuth();

  if (loading) {
    return <Loading fullPage text="Đang tải..." />;
  }

  if (!tenant) {
    if (isWaitstaff) {
      return <Navigate to="/access-key-login" replace />;
    }
    return <Navigate to="/dashboard" replace />;
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

function OwnerDashboardRoute({ children }) {
  const { isWaitstaff } = useAuth();
  
  if (isWaitstaff) {
    return <Navigate to="/pos" replace />;
  }
  
  return children;
}

function KdsRoute({ children }) {
  const { isWaitstaff, isKitchen } = useAuth();
  
  if (isWaitstaff && !isKitchen) {
    return <Navigate to="/pos" replace />;
  }
  
  return children;
}

function AppRoutes() {
  const { tenant } = useTenant();

  return (
    <Routes>
      {/* Public Routes */}
      <Route path="/shops" element={<PublicTenantListPage />} />
      <Route path="/table/:tableId" element={<CustomerMenuPage />} />
      <Route path="/menu/:tenantId" element={<MenuPage />} />
      <Route path="/menu/:tenantId/:tableId" element={<CustomerMenuPage />} />
      <Route path="/access-key-login" element={<AccessKeyLoginPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      {/* Dashboard Routes (Protected, No Tenant Required) */}
      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <OwnerDashboardRoute>
              <DashboardPage />
            </OwnerDashboardRoute>
          </ProtectedRoute>
        }
      />

      {/* Auth Routes */}
      {/* /select-tenant removed - use /dashboard instead */}

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
        path="/kds"
        element={
          <ProtectedRoute>
            <TenantRoute>
              <KdsRoute>
                <KdsProvider tenantId={tenant?.id}>
                  <KdsPage />
                </KdsProvider>
              </KdsRoute>
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
        path="/sessions/history"
        element={
          <ProtectedRoute>
            <TenantRoute>
              <SessionHistoryPage />
            </TenantRoute>
          </ProtectedRoute>
        }
      />
      <Route
        path="/sessions/:sessionId"
        element={
          <ProtectedRoute>
            <TenantRoute>
              <SessionDetailPage />
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
              <OwnerRoute>
                <ProductListPage />
              </OwnerRoute>
            </TenantRoute>
          </ProtectedRoute>
        }
      />
      <Route
        path="/categories"
        element={
          <ProtectedRoute>
            <TenantRoute>
              <OwnerRoute>
                <CategoryListPage />
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
      <Route
        path="/settings/access-keys"
        element={
          <ProtectedRoute>
            <TenantRoute>
              <OwnerRoute>
                <AccessKeySettingsPage />
              </OwnerRoute>
            </TenantRoute>
          </ProtectedRoute>
        }
      />
      <Route
        path="/settings/audit"
        element={
          <ProtectedRoute>
            <TenantRoute>
              <OwnerRoute>
                <PosAuditPage />
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

      {/* Default: show login page first */}
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="*" element={<Navigate to="/login" replace />} />
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
