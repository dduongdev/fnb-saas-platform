import { useState, useEffect } from 'react';
import { BarChart3, TrendingUp, Clock } from 'lucide-react';
import { format, subDays } from 'date-fns';
import { PageLayout } from '../../components/layout';
import { Card, CardHeader, CardTitle, Loading, Input } from '../../components/common';
import { getRevenueReport, getTopProducts, getPeakHours } from '../../api/reports';
import './ReportsPage.css';

export function ReportsPage() {
    const [activeTab, setActiveTab] = useState('revenue');
    const [dateRange, setDateRange] = useState({
        from: format(subDays(new Date(), 30), 'yyyy-MM-dd'),
        to: format(new Date(), 'yyyy-MM-dd'),
    });
    const [loading, setLoading] = useState(true);
    const [revenueData, setRevenueData] = useState([]);
    const [topProducts, setTopProducts] = useState([]);
    const [peakHours, setPeakHours] = useState([]);

    useEffect(() => {
        loadData();
    }, [dateRange]);

    const loadData = async () => {
        try {
            setLoading(true);
            const [revenue, products, hours] = await Promise.all([
                getRevenueReport(dateRange.from, dateRange.to),
                getTopProducts(dateRange.from, dateRange.to, 10),
                getPeakHours(dateRange.from, dateRange.to),
            ]);
            setRevenueData(revenue || []);
            setTopProducts(products || []);
            setPeakHours(hours || []);
        } catch (error) {
            console.error('Failed to load reports:', error);
        } finally {
            setLoading(false);
        }
    };

    const formatPrice = (price) => {
        return new Intl.NumberFormat('vi-VN').format(price) + 'đ';
    };

    const totalRevenue = revenueData.reduce((sum, d) => sum + d.totalRevenue, 0);
    const totalOrders = revenueData.reduce((sum, d) => sum + d.orderCount, 0);
    const averageOrder = totalOrders > 0 ? Math.round(totalRevenue / totalOrders) : 0;

    const tabs = [
        { id: 'revenue', label: 'Doanh thu', icon: BarChart3 },
        { id: 'products', label: 'Top món', icon: TrendingUp },
        { id: 'hours', label: 'Khung giờ', icon: Clock },
    ];


    return (
        <PageLayout title="Báo cáo" subtitle="Tổng quan kinh doanh và hiệu suất bán hàng">
            {/* Header hiển thị range */}
            <div className="reports-header">
                <div>
                    <h2 className="reports-title">Báo cáo kinh doanh</h2>
                    <p className="reports-subtitle">Số liệu cập nhật theo khoảng thời gian bạn chọn.</p>
                </div>
                <div className="report-date-range-fields">
                    <Input
                        label="Từ ngày"
                        type="date"
                        value={dateRange.from}
                        onChange={(e) => setDateRange({ ...dateRange, from: e.target.value })}
                    />
                    <Input
                        label="Đến ngày"
                        type="date"
                        value={dateRange.to}
                        onChange={(e) => setDateRange({ ...dateRange, to: e.target.value })}
                    />
                </div>
            </div>

            {/* KPI Summary */}
            <div className="report-summary-grid">
                <Card className="summary-card big">
                    <span className="summary-label">Tổng doanh thu </span>
                    <span className="summary-value">{formatPrice(totalRevenue)}</span>
                    <span className="summary-meta">Trong khoảng {dateRange.from} - {dateRange.to}</span>
                </Card>
                <Card className="summary-card big">
                    <span className="summary-label">Tổng đơn hàng </span>
                    <span className="summary-value">{totalOrders}</span>
                    <span className="summary-meta">Khách mua trung bình {averageOrder === 0 ? 0 : formatPrice(averageOrder)} / đơn</span>
                </Card>
                <Card className="summary-card small">
                    <span className="summary-label">Đơn trung bình </span>
                    <span className="summary-value">{formatPrice(averageOrder)}</span>
                </Card>
            </div>

            {/* Tabs */}
            <div className="report-tabs">
                {tabs.map(tab => (
                    <button
                        key={tab.id}
                        className={`report-tab ${activeTab === tab.id ? 'active' : ''}`}
                        onClick={() => setActiveTab(tab.id)}
                    >
                        <tab.icon size={16} />
                        {tab.label}
                    </button>
                ))}
            </div>

            <Card className="report-content">
                {loading ? (
                    <div className="reports-loading"><Loading /></div>
                ) : (
                    <>
                        {activeTab === 'revenue' && (
                            <div className="report-section">
                                <CardHeader>
                                    <CardTitle>Biểu đồ doanh thu theo ngày</CardTitle>
                                </CardHeader>
                                <div className="chart-card">
                                    <div className="revenue-trend-chart">
                                        {revenueData.map(row => {
                                            const maxRevenue = Math.max(...revenueData.map(d => d.totalRevenue), 0);
                                            const width = maxRevenue > 0 ? (row.totalRevenue / maxRevenue) * 100 : 0;
                                            return (
                                                <div key={row.date} className="revenue-trend-bar">
                                                    <span className="revenue-date">{row.date}</span>
                                                    <div className="revenue-track">
                                                        <div className="revenue-fill" style={{ width: `${width}%` }} />
                                                    </div>
                                                    <span className="revenue-value-small">{formatPrice(row.totalRevenue)}</span>
                                                </div>
                                            );
                                        })}
                                    </div>
                                </div>
                                <div className="table-card">
                                    <table className="report-table">
                                        <thead>
                                            <tr>
                                                <th>Ngày</th>
                                                <th>Số đơn</th>
                                                <th>Doanh thu</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {revenueData.map(row => (
                                                <tr key={row.date}>
                                                    <td>{row.date}</td>
                                                    <td>{row.orderCount}</td>
                                                    <td className="revenue-value">{formatPrice(row.totalRevenue)}</td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        )}

                        {activeTab === 'products' && (
                            <div className="report-section">
                                <CardHeader>
                                    <CardTitle>Top sản phẩm bán chạy</CardTitle>
                                </CardHeader>
                                <div className="chart-card">
                                    <div className="top-products-chart">
                                        {topProducts.map((product, index) => {
                                            const maxSold = Math.max(...topProducts.map(p => p.quantitySold), 0);
                                            const width = maxSold > 0 ? (product.quantitySold / maxSold) * 100 : 0;
                                            return (
                                                <div key={product.productId} className="top-product-bar">
                                                    <span className="product-name">{index + 1}. {product.productName}</span>
                                                    <div className="top-product-track">
                                                        <div className="top-product-fill" style={{ width: `${width}%` }} />
                                                    </div>
                                                    <span className="top-product-value">{product.quantitySold} cái</span>
                                                </div>
                                            );
                                        })}
                                    </div>
                                </div>

                                <div className="table-card">
                                    <table className="report-table">
                                        <thead>
                                            <tr>
                                                <th>#</th>
                                                <th>Món</th>
                                                <th>Bán</th>
                                                <th>Hủy</th>
                                                <th>Doanh thu</th>
                                                <th>Doanh thu hủy</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {topProducts.map((product, index) => (
                                                <tr key={product.productId}>
                                                    <td>{index + 1}</td>
                                                    <td className="product-name">{product.productName}</td>
                                                    <td>{product.quantitySold}</td>
                                                    <td>{product.quantityCancelled || 0}</td>
                                                    <td className="revenue-value">{formatPrice(product.totalRevenue)}</td>
                                                    <td className="revenue-value">{formatPrice(product.cancelledRevenue || 0)}</td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        )}

                        {activeTab === 'hours' && (
                            <div className="report-section">
                                <CardHeader>
                                    <CardTitle>Khung giờ đắt khách</CardTitle>
                                </CardHeader>
                                <div className="chart-card">
                                    <div className="peak-hours-chart">
                                        {peakHours.map(hour => {
                                            const maxOrders = Math.max(...peakHours.map(h => h.orderCount), 1);
                                            const percentage = maxOrders > 0 ? (hour.orderCount / maxOrders) * 100 : 0;
                                            return (
                                                <div key={hour.hour} className="peak-hour-bar">
                                                    <span className="peak-hour-label">{hour.hour}:00</span>
                                                    <div className="peak-hour-track">
                                                        <div className="peak-hour-fill" style={{ width: `${percentage}%` }} />
                                                    </div>
                                                    <span className="peak-hour-value">{hour.orderCount} đơn</span>
                                                </div>
                                            );
                                        })}
                                    </div>
                                </div>
                            </div>
                        )}
                    </>
                )}
            </Card>
        </PageLayout>
    );
}
