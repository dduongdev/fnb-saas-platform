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

    const tabs = [
        { id: 'revenue', label: 'Doanh thu', icon: BarChart3 },
        { id: 'products', label: 'Top món', icon: TrendingUp },
        { id: 'hours', label: 'Khung giờ', icon: Clock },
    ];

    return (
        <PageLayout title="Báo cáo">
            {/* Date Range Filter */}
            <Card className="report-filters">
                <div className="report-date-range">
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
            </Card>

            {/* Summary Cards */}
            <div className="report-summary">
                <Card className="summary-card">
                    <span className="summary-label">Tổng doanh thu</span>
                    <span className="summary-value">{formatPrice(totalRevenue)}</span>
                </Card>
                <Card className="summary-card">
                    <span className="summary-label">Tổng đơn hàng</span>
                    <span className="summary-value">{totalOrders}</span>
                </Card>
                <Card className="summary-card">
                    <span className="summary-label">Đơn trung bình</span>
                    <span className="summary-value">
                        {totalOrders > 0 ? formatPrice(Math.round(totalRevenue / totalOrders)) : '0đ'}
                    </span>
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
                        <tab.icon size={18} />
                        {tab.label}
                    </button>
                ))}
            </div>

            {/* Tab Content */}
            {loading ? (
                <Loading />
            ) : (
                <Card className="report-content">
                    {activeTab === 'revenue' && (
                        <>
                            <CardHeader>
                                <CardTitle>Doanh thu theo ngày</CardTitle>
                            </CardHeader>
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
                        </>
                    )}

                    {activeTab === 'products' && (
                        <>
                            <CardHeader>
                                <CardTitle>Top sản phẩm bán chạy</CardTitle>
                            </CardHeader>
                            <table className="report-table">
                                <thead>
                                    <tr>
                                        <th>#</th>
                                        <th>Sản phẩm</th>
                                        <th>Số lượng bán</th>
                                        <th>Doanh thu</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {topProducts.map((product, index) => (
                                        <tr key={product.productId}>
                                            <td>{index + 1}</td>
                                            <td className="product-name">{product.productName}</td>
                                            <td>{product.quantitySold}</td>
                                            <td className="revenue-value">{formatPrice(product.totalRevenue)}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </>
                    )}

                    {activeTab === 'hours' && (
                        <>
                            <CardHeader>
                                <CardTitle>Khung giờ đắt khách</CardTitle>
                            </CardHeader>
                            <div className="peak-hours-chart">
                                {peakHours.map(hour => {
                                    const maxOrders = Math.max(...peakHours.map(h => h.orderCount));
                                    const percentage = maxOrders > 0 ? (hour.orderCount / maxOrders) * 100 : 0;

                                    return (
                                        <div key={hour.hour} className="peak-hour-bar">
                                            <span className="peak-hour-label">{hour.hour}:00</span>
                                            <div className="peak-hour-track">
                                                <div
                                                    className="peak-hour-fill"
                                                    style={{ width: `${percentage}%` }}
                                                />
                                            </div>
                                            <span className="peak-hour-value">{hour.orderCount} đơn</span>
                                        </div>
                                    );
                                })}
                            </div>
                        </>
                    )}
                </Card>
            )}
        </PageLayout>
    );
}
