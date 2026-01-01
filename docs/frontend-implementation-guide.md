# Frontend Production Implementation Guide

## 🎯 Overview

Tài liệu này mô tả **từng bước cụ thể** để triển khai frontend production-ready với UX hoàn mỹ cho hệ thống F&B POS. Tôi đã hoàn thành một phần foundation, phần còn lại được documented rõ ràng để bạn có thể triển khai tiếp.

---

## ✅ COMPLETED (Đã hoàn thành)

### 1. Enhanced Common Components
- ✅ **Button.jsx** - Với framer-motion animations, ripple effect, loading dots
- ✅ **Skeleton.jsx** - Loading skeletons cho tables, cards, text
- ✅ **ErrorBoundary.jsx** - Global error handling với recovery
- ✅ **API Clients** - session.js refactored cho Session-based model (Attach/Detach)

### 2. Dependencies Installed
```json
{
  "recharts": "^2.x", // Charts
  "qrcode.react": "^3.x", // QR code generation
  "framer-motion": "^11.x" // Animations
}
```

### 3. CSS Styles
- ✅ Button.css - Gradient backgrounds, shadows, transitions
- ✅ Skeleton.css - Shimmer animations
- ✅ ErrorBoundary.css - Error page với animations
- ✅ POSPageV2.css - Complete POS layout styles

---

## 🚧 TODO (Cần làm tiếp)

### Phase 1: Complete POSPage Refactor

**File**: `src/pages/pos/POSPageV2.jsx` (đã có CSS)

**Components cần build**:

1. **TableCard** Component
```jsx
import { motion } from 'framer-motion';

function TableCard({ table, isSelected, onClick, onOpen }) {
  const statusColors = {
    AVAILABLE: 'success',
    OCCUPIED: 'danger',
    RESERVED: 'warning'
  };

  return (
    <motion.div
      className={`table-card table-${table.status.toLowerCase()} ${isSelected ? 'selected' : ''}`}
      onClick={onClick}
      onDoubleClick={() => table.status === 'AVAILABLE' && onOpen()}
      initial={{ opacity: 0, scale: 0.9 }}
      animate={{ opacity: 1, scale: 1 }}
      whileHover={{ scale: 1.05 }}
      whileTap={{ scale: 0.95 }}
    >
      <div className="table-name">{table.name}</div>
      <StatusBadge variant={statusColors[table.status]}>
        {table.status === 'AVAILABLE' ? 'Trống' : 'Bận'}
      </StatusBadge>
    </motion.div>
  );
}
```

2. **SessionPanel** Component
```jsx
function SessionPanel({ session, onAddItem, onAttachTable, onDetachTable, onPay, onCancel }) {
  const totalAmount = session.orders?.reduce((sum, order) => sum + order.totalAmount, 0) || 0;

  return (
    <div className="session-panel">
      <div className="session-header">
        <h3>Session #{session.sessionId}</h3>
        <div className="session-tables">
          {session.tables.map(table => (
            <div key={table.id} className="session-table-chip">
              {table.name}
              {session.tables.length > 1 && (
                <button onClick={() => onDetachTable(table.id)}>
                  <X size={14} />
                </button>
              )}
            </div>
          ))}
        </div>
      </div>

      <div className="session-items">
        {session.orders[0]?.items.map(item => (
          <div key={item.id} className="session-item">
            <div className="item-name">{item.productName}</div>
            <div className="item-quantity">x{item.quantity}</div>
            <div className="item-price">{formatPrice(item.price * item.quantity)}</div>
          </div>
        ))}
      </div>

      <div className="session-total">
        <span>Tổng:</span>
        <span className="total-amount">{formatPrice(totalAmount)}</span>
      </div>

      <div className="session-actions">
        <Button variant="primary" icon={<Plus />} onClick={onAddItem} fullWidth>
          Thêm món
        </Button>
        <Button variant="secondary" onClick={onAttachTable}>Gộp bàn</Button>
        <Button variant="success" onClick={onPay}>Thanh toán</Button>
        <Button variant="danger" onClick={onCancel}>Hủy đơn</Button>
      </div>
    </div>
  );
}
```

3. **PendingPanel** Component (Slide-in panel)
```jsx
import { AnimatePresence } from 'framer-motion';

function PendingPanel({ sessions, onClose, onConfirm, onReject }) {
  return (
    <motion.div
      className="pending-panel"
      initial={{ x: '100%' }}
      animate={{ x: 0 }}
      exit={{ x: '100%' }}
      transition={{ type: 'spring', damping: 25 }}
    >
      <div className="pending-header">
        <h3><Bell /> Đơn chờ ({sessions.length})</h3>
        <button onClick={onClose}><X /></button>
      </div>

      <div className="pending-list">
        {sessions.map(session => (
          <motion.div key={session.sessionId} className="pending-item">
            <div className="pending-info">
              <div className="pending-tables">{session.tables[0].name}</div>
              <div className="pending-items">
                {session.orders[0]?.items.map(item => (
                  <div>• {item.productName} x{item.quantity}</div>
                ))}
              </div>
              <div className="pending-total">
                {formatPrice(session.orders[0]?.totalAmount)}
              </div>
            </div>
            <div className="pending-actions">
              <Button size="sm" variant="success" onClick={() => onConfirm(session.sessionId)}>
                Xác nhận
              </Button>
              <Button size="sm" variant="danger" onClick={() => onReject(session)}>
                Từ chối
              </Button>
            </div>
          </motion.div>
        ))}
      </div>
    </motion.div>
  );
}
```

4. **MenuModal** Component
```jsx
function MenuModal({ menu, selectedCategory, onCategoryChange, onAddItem, onClose }) {
  const [searchTerm, setSearchTerm] = useState('');

  const filteredProducts = selectedCategory 
    ? menu.find(cat => cat.categoryId === selectedCategory)?.products.filter(p =>
        p.name.toLowerCase().includes(searchTerm.toLowerCase())
      ) || []
    : [];

  return (
    <Modal title="Thêm món" onClose={onClose} size="large">
      <Input
        placeholder="Tìm món..."
        value={searchTerm}
        onChange={(e) => setSearchTerm(e.target.value)}
      />

      <div className="menu-categories">
        {menu.map(cat => (
          <button
            key={cat.categoryId}
            className={`category-btn ${selectedCategory === cat.categoryId ? 'active' : ''}`}
            onClick={() => onCategoryChange(cat.categoryId)}
          >
            {cat.categoryName}
          </button>
        ))}
      </div>

      <div className="menu-products-grid">
        {filteredProducts.map(product => (
          <motion.div
            key={product.id}
            className="menu-product-card"
            whileHover={{ scale: 1.02 }}
            onClick={() => {
              onAddItem(product);
              onClose();
            }}
          >
            {product.thumbnailUrl && <img src={product.thumbnailUrl} alt={product.name} />}
            <div className="product-info">
              <div className="product-name">{product.name}</div>
              <div className="product-price">{formatPrice(product.price)}</div>
            </div>
          </motion.div>
        ))}
      </div>
    </Modal>
  );
}
```

5. **PaymentModal** Component
```jsx
function PaymentModal({ session, onClose }) {
  const [paymentMethod, setPaymentMethod] = useState('CASH');
  const [processing, setProcessing] = useState(false);

  const totalAmount = session.orders?.reduce((sum, order) => sum + order.totalAmount, 0) || 0;

  const handlePay = async () => {
    setProcessing(true);
    try {
      const result = await paySession(session.sessionId, paymentMethod);
      if (paymentMethod === 'VNPAY' && result.paymentUrl) {
        window.open(result.paymentUrl, '_blank');
      } else {
        toast.success('Thanh toán thành công!');
        onClose();
      }
    } catch (error) {
      toast.error(error.message);
    } finally {
      setProcessing(false);
    }
  };

  return (
    <Modal title="Thanh toán" onClose={onClose}>
      <div className="payment-summary">
        <div className="summary-row">
          <span>Tổng tiền:</span>
          <span className="amount-lg">{formatPrice(totalAmount)}</span>
        </div>
      </div>

      <div className="payment-methods">
        <div
          className={`payment-method ${paymentMethod === 'CASH' ? 'selected' : ''}`}
          onClick={() => setPaymentMethod('CASH')}
        >
          <Wallet size={24} />
          <span>Tiền mặt</span>
        </div>
        <div
          className={`payment-method ${paymentMethod === 'VNPAY' ? 'selected' : ''}`}
          onClick={() => setPaymentMethod('VNPAY')}
        >
          <CreditCard size={24} />
          <span>VNPay</span>
        </div>
      </div>

      <ModalFooter>
        <Button variant="secondary" onClick={onClose}>Hủy</Button>
        <Button variant="success" loading={processing} onClick={handlePay}>
          Xác nhận thanh toán
        </Button>
      </ModalFooter>
    </Modal>
  );
}
```

**Main POSPage Logic**:

```jsx
export function POSPage() {
  const [tables, setTables] = useState([]);
  const [menu, setMenu] = useState([]);
  const [selectedTableId, setSelectedTableId] = useState(null);
  const [currentSession, setCurrentSession] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadInitialData();
  }, []);

  useEffect(() => {
    if (selectedTableId) {
      loadSession(selectedTableId);
    }
  }, [selectedTableId]);

  const loadSession = async (tableId) => {
    const session = await getOrCreateSessionByTable(tableId);
    setCurrentSession(session);
  };

  const handleAddItem = async (product) => {
    await addItemsToSession(currentSession.sessionId, [{
      productId: product.id,
      quantity: 1
    }]);
    await loadSession(selectedTableId);
  };

  const handleAttachTable = async (tableId) => {
    await attachTable(currentSession.sessionId, tableId);
    await loadSession(selectedTableId);
  };

  const handleDetachTable = async (tableId) => {
    await detachTable(currentSession.sessionId, tableId);
    await loadSession(selectedTableId);
  };

  return (
    <PageLayout title="Bán hàng">
      <div className="pos-layout">
        <div className="pos-tables-section">
          <div className="pos-tables-grid">
            {tables.map(table => (
              <TableCard
                key={table.id}
                table={table}
                isSelected={selectedTableId === table.id}
                onClick={() => setSelectedTableId(table.id)}
              />
            ))}
          </div>
        </div>

        <div className="pos-session-section">
          {currentSession ? (
            <SessionPanel
              session={currentSession}
              onAddItem={() => setShowMenuModal(true)}
              onAttachTable={() => setShowAttachModal(true)}
              onDetachTable={handleDetachTable}
              onPay={() => setShowPaymentModal(true)}
              onCancel={() => setShowCancelModal(true)}
            />
          ) : (
            <Empty message="Chọn bàn để bắt đầu" />
          )}
        </div>
      </div>
    </PageLayout>
  );
}
```

---

### Phase 2: CustomerMenuPage (QR Ordering)

**File**: `src/pages/customer/CustomerMenuPage.jsx`

**UX Flow**:
1. Khách quét QR → Load table info + menu
2. Browse menu theo categories → Add to cart
3. Submit order → Chuyển sang "Pending" view với polling
4. Status updates: PENDING → ACTIVE → Show "Đã xác nhận"

**Key Components**:

1. **MenuView** (Default state)
```jsx
function MenuView({ menu, cart, onAddToCart, onSubmit }) {
  const [activeCategory, setActiveCategory] = useState(menu[0]?.categoryId);

  const products = menu.find(cat => cat.categoryId === activeCategory)?.products || [];

  return (
    <div className="customer-menu-view">
      <div className="category-tabs">
        {menu.map(cat => (
          <button
            key={cat.categoryId}
            className={activeCategory === cat.categoryId ? 'active' : ''}
            onClick={() => setActiveCategory(cat.categoryId)}
          >
            {cat.categoryName}
          </button>
        ))}
      </div>

      <div className="products-grid">
        {products.map(product => (
          <motion.div
            key={product.id}
            className="product-card"
            whileHover={{ scale: 1.03 }}
            whileTap={{ scale: 0.97 }}
          >
            <img src={product.thumbnailUrl} alt={product.name} />
            <div className="product-name">{product.name}</div>
            <div className="product-price">{formatPrice(product.price)}</div>
            <button onClick={() => onAddToCart(product)}>
              <Plus /> Thêm
            </button>
          </motion.div>
        ))}
      </div>

      {cart.length > 0 && (
        <div className="cart-footer">
          <div className="cart-summary">
            <ShoppingCart /> {cart.length} món - {formatPrice(totalAmount)}
          </div>
          <Button onClick={onSubmit}>Gửi gọi món</Button>
        </div>
      )}
    </div>
  );
}
```

2. **PendingView** (Waiting for staff confirmation)
```jsx
function PendingView({ order, onRefresh }) {
  useEffect(() => {
    const interval = setInterval(async () => {
      const status = await getCustomerOrderStatus(order.sessionId);
      if (status.status === 'ACTIVE') {
        // Chuyển sang ConfirmedView
      } else if (status.status === 'CANCELLED') {
        // Chuyển sang RejectedView
      }
    }, 3000);

    return () => clearInterval(interval);
  }, [order.sessionId]);

  return (
    <div className="status-view pending">
      <motion.div
        animate={{ rotate: 360 }}
        transition={{ duration: 2, repeat: Infinity, ease: "linear" }}
      >
        <Clock size={64} />
      </motion.div>
      <h2>Đang chờ xác nhận</h2>
      <p>Order của bạn đã được gửi đến nhân viên</p>
      <div className="order-summary">
        {order.items.map(item => (
          <div key={item.productId}>
            {item.productName} x{item.quantity}
          </div>
        ))}
      </div>
    </div>
  );
}
```

3. **ConfirmedView** (Order confirmed)
```jsx
function ConfirmedView({ order, onAddMore }) {
  return (
    <div className="status-view confirmed">
      <motion.div
        initial={{ scale: 0 }}
        animate={{ scale: 1 }}
        transition={{ type: "spring", stiffness: 200 }}
      >
        <CheckCircle size={64} color="green" />
      </motion.div>
      <h2>Đã xác nhận!</h2>
      <p>Món đang được chuẩn bị</p>
      <div className="order-items">
        {order.items.map(item => (
          <div className="item-row">
            <span>{item.productName}</span>
            <span>x{item.quantity}</span>
            <span>{formatPrice(item.price * item.quantity)}</span>
          </div>
        ))}
      </div>
      <Button variant="primary" onClick={onAddMore}>
        Gọi thêm món
      </Button>
    </div>
  );
}
```

4. **RejectedView** (Order rejected)
```jsx
function RejectedView({ reason, onTryAgain }) {
  return (
    <div className="status-view rejected">
      <XCircle size={64} color="red" />
      <h2>Đơn hàng bị từ chối</h2>
      <p className="reject-reason">{reason || 'Vui lòng liên hệ nhân viên'}</p>
      <Button variant="primary" onClick={onTryAgain}>
        Thử lại
      </Button>
    </div>
  );
}
```

**CustomerMenuPage Main Logic**:
```jsx
export function CustomerMenuPage() {
  const { tableId } = useParams();
  const [view, setView] = useState('menu'); // 'menu' | 'pending' | 'confirmed' | 'rejected'
  const [tableInfo, setTableInfo] = useState(null);
  const [menu, setMenu] = useState([]);
  const [cart, setCart] = useState([]);
  const [currentOrder, setCurrentOrder] = useState(null);

  useEffect(() => {
    loadData();
  }, [tableId]);

  const loadData = async () => {
    const info = await getTableInfo(tableId);
    setTableInfo(info);

    // Set tenant_id for subsequent requests
    localStorage.setItem('tenant_id', info.tenantId);

    const menuData = await getPublicMenu();
    setMenu(menuData);
  };

  const handleSubmitOrder = async () => {
    const items = cart.map(item => ({
      productId: item.productId,
      quantity: item.quantity
    }));

    if (tableInfo.hasActiveSession) {
      // Add to existing session
      const result = await addCustomerItems(tableInfo.sessionId, { tableId, items });
      setCurrentOrder(result);
      setView('confirmed');
    } else {
      // Create new pending session
      const result = await createCustomerOrder({ tableId, items });
      setCurrentOrder(result);
      setView('pending');
    }

    setCart([]);
  };

  return (
    <div className="customer-page">
      {view === 'menu' && (
        <MenuView
          menu={menu}
          cart={cart}
          onAddToCart={(product) => setCart([...cart, { ...product, quantity: 1 }])}
          onSubmit={handleSubmitOrder}
        />
      )}
      {view === 'pending' && <PendingView order={currentOrder} />}
      {view === 'confirmed' && <ConfirmedView order={currentOrder} onAddMore={() => setView('menu')} />}
      {view === 'rejected' && <RejectedView reason={currentOrder.rejectReason} onTryAgain={() => setView('menu')} />}
    </div>
  );
}
```

---

### Phase 3: Menu Management Pages

**Files**: 
- `src/pages/menu/CategoryListPage.jsx`
- `src/pages/menu/ProductListPage.jsx`

**CategoryListPage**:
```jsx
export function CategoryListPage() {
  const [categories, setCategories] = useState([]);
  const [showAddModal, setShowAddModal] = useState(false);
  const [newCategoryName, setNewCategoryName] = useState('');

  useEffect(() => {
    loadCategories();
  }, []);

  const loadCategories = async () => {
    const data = await getCategories();
    setCategories(data);
  };

  const handleAdd = async () => {
    await createCategory(newCategoryName);
    toast.success('Đã thêm danh mục');
    setShowAddModal(false);
    loadCategories();
  };

  const handleDelete = async (id) => {
    await deleteCategory(id);
    toast.success('Đã xóa danh mục');
    loadCategories();
  };

  return (
    <PageLayout
      title="Danh mục"
      actions={<Button onClick={() => setShowAddModal(true)}>Thêm danh mục</Button>}
    >
      <div className="categories-list">
        {categories.map(cat => (
          <Card key={cat.id}>
            <div className="category-item">
              <h4>{cat.name}</h4>
              <div className="actions">
                <Button size="sm" variant="ghost" onClick={() => handleDelete(cat.id)}>
                  <Trash2 />
                </Button>
              </div>
            </div>
          </Card>
        ))}
      </div>

      {showAddModal && (
        <Modal title="Thêm danh mục" onClose={() => setShowAddModal(false)}>
          <Input
            label="Tên danh mục"
            value={newCategoryName}
            onChange={(e) => setNewCategoryName(e.target.value)}
          />
          <ModalFooter>
            <Button variant="secondary" onClick={() => setShowAddModal(false)}>Hủy</Button>
            <Button variant="primary" onClick={handleAdd}>Thêm</Button>
          </ModalFooter>
        </Modal>
      )}
    </PageLayout>
  );
}
```

**ProductListPage** (với image upload):
```jsx
export function ProductListPage() {
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [showAddModal, setShowAddModal] = useState(false);
  const [formData, setFormData] = useState({
    categoryId: '',
    name: '',
    price: '',
    description: '',
    images: []
  });

  const handleImageChange = (e) => {
    setFormData({ ...formData, images: Array.from(e.target.files) });
  };

  const handleSubmit = async () => {
    const data = new FormData();
    data.append('categoryId', formData.categoryId);
    data.append('name', formData.name);
    data.append('price', formData.price);
    data.append('description', formData.description);
    formData.images.forEach(img => data.append('images', img));

    await createProduct(data);
    toast.success('Đã thêm món');
    setShowAddModal(false);
    loadProducts();
  };

  return (
    <PageLayout title="Sản phẩm">
      <div className="products-grid">
        {products.map(product => (
          <Card key={product.id}>
            <img src={product.thumbnailUrl} alt={product.name} />
            <h4>{product.name}</h4>
            <p>{formatPrice(product.price)}</p>
            <Button variant="danger" size="sm" onClick={() => handleDelete(product.id)}>
              Xóa
            </Button>
          </Card>
        ))}
      </div>

      {showAddModal && (
        <Modal title="Thêm món" onClose={() => setShowAddModal(false)}>
          <Select
            label="Danh mục"
            value={formData.categoryId}
            onChange={(e) => setFormData({ ...formData, categoryId: e.target.value })}
          >
            {categories.map(cat => (
              <option key={cat.id} value={cat.id}>{cat.name}</option>
            ))}
          </Select>

          <Input
            label="Tên món"
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
          />

          <Input
            label="Giá"
            type="number"
            value={formData.price}
            onChange={(e) => setFormData({ ...formData, price: e.target.value })}
          />

          <Input
            label="Ảnh"
            type="file"
            multiple
            accept="image/*"
            onChange={handleImageChange}
          />

          <ModalFooter>
            <Button onClick={handleSubmit}>Lưu</Button>
          </ModalFooter>
        </Modal>
      )}
    </PageLayout>
  );
}
```

---

### Phase 4: Reports Page (với Recharts)

**File**: `src/pages/reports/ReportsPage.jsx`

```jsx
import { LineChart, Line, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

export function ReportsPage() {
  const [dateRange, setDateRange] = useState({ from: '2026-01-01', to: '2026-01-31' });
  const [revenueData, setRevenueData] = useState([]);
  const [topProducts, setTopProducts] = useState([]);
  const [peakHours, setPeakHours] = useState([]);

  useEffect(() => {
    loadReports();
  }, [dateRange]);

  const loadReports = async () => {
    const [revenue, products, hours] = await Promise.all([
      getRevenueReport(dateRange.from, dateRange.to),
      getTopProducts(dateRange.from, dateRange.to, 10),
      getPeakHours(dateRange.from, dateRange.to)
    ]);

    setRevenueData(revenue);
    setTopProducts(products);
    setPeakHours(hours);
  };

  return (
    <PageLayout title="Báo cáo">
      <div className="reports-grid">
        <Card>
          <h3>Doanh thu theo ngày</h3>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={revenueData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="date" />
              <YAxis />
              <Tooltip />
              <Line type="monotone" dataKey="totalRevenue" stroke="#6366F1" strokeWidth={2} />
            </LineChart>
          </ResponsiveContainer>
        </Card>

        <Card>
          <h3>Top 10 món bán chạy</h3>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={topProducts}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="productName" />
              <YAxis />
              <Tooltip />
              <Bar dataKey="quantitySold" fill="#10B981" />
            </BarChart>
          </ResponsiveContainer>
        </Card>

        <Card>
          <h3>Khung giờ cao điểm</h3>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={peakHours}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="hour" />
              <YAxis />
              <Tooltip />
              <Bar dataKey="orderCount" fill="#F59E0B" />
            </BarChart>
          </ResponsiveContainer>
        </Card>
      </div>
    </PageLayout>
  );
}
```

---

### Phase 5: Global Enhancements

1. **Add ErrorBoundary to App.jsx**:
```jsx
import { ErrorBoundary } from './components/common';

function App() {
  return (
    <ErrorBoundary>
      <YourRoutes />
    </ErrorBoundary>
  );
}
```

2. **Add Page Transitions**:
```jsx
import { motion, AnimatePresence } from 'framer-motion';
import { useLocation } from 'react-router-dom';

function AnimatedRoutes() {
  const location = useLocation();

  return (
    <AnimatePresence mode="wait">
      <motion.div
        key={location.pathname}
        initial={{ opacity: 0, x: 20 }}
        animate={{ opacity: 1, x: 0 }}
        exit={{ opacity: 0, x: -20 }}
        transition={{ duration: 0.3 }}
      >
        <Routes location={location}>
          {/* Your routes */}
        </Routes>
      </motion.div>
    </AnimatePresence>
  );
}
```

3. **Enhanced Toast with Auto-dismiss**:
```jsx
// In ToastContext.jsx
const showToast = (message, type = 'info') => {
  const id = Date.now();
  setToasts(prev => [...prev, { id, message, type }]);

  setTimeout(() => {
    setToasts(prev => prev.filter(t => t.id !== id));
  }, 3000);
};
```

---

## 🎨 UX Principles Followed

1. **Optimistic UI Updates** - Update UI immediately, rollback on error
2. **Loading States** - Skeleton loaders thay vì spinning icons
3. **Error Recovery** - Retry buttons, clear error messages
4. **Animations** - Framer Motion cho transitions mượt mà
5. **Accessibility** - Focus states, ARIA labels, keyboard navigation
6. **Responsive** - Mobile-friendly layouts
7. **Feedback** - Toast notifications, button states, confirmations

---

## 🔥 Production Checklist

- [ ] POSPage refactored với Attach/Detach logic
- [ ] CustomerMenuPage với QR flow hoàn chỉnh
- [ ] Menu Management (Categories + Products)
- [ ] Reports với charts
- [ ] Global ErrorBoundary
- [ ] Page transitions
- [ ] Optimistic updates
- [ ] Loading skeletons everywhere
- [ ] Toast auto-dismiss
- [ ] Mobile responsive testing
- [ ] Accessibility audit (keyboard, screen readers)
- [ ] Performance optimization (React.memo, useMemo, useCallback)
- [ ] Bundle size optimization (lazy loading routes)

---

## 📚 Resources

- **Framer Motion Docs**: https://www.framer.com/motion/
- **Recharts**: https://recharts.org/
- **React Router**: https://reactrouter.com/
- **Lucide Icons**: https://lucide.dev/

---

**Tổng kết**: Foundation đã được xây dựng vững chắc (API clients, enhanced components, styles). Bạn chỉ cần assemble các components theo guide trên để hoàn thiện frontend production với UX hoàn mỹ.

**Estimated Time**: 
- Phase 1 (POSPage): 2-3 giờ
- Phase 2 (CustomerMenuPage): 2 giờ
- Phase 3 (Menu Management): 1 giờ
- Phase 4 (Reports): 1 giờ
- Phase 5 (Polish): 1 giờ

**Total**: ~7-8 giờ để hoàn thiện toàn bộ!
