import { useState, useEffect } from 'react';
import { Plus, Pencil, Trash2, Image, Search, ChevronLeft, ChevronRight } from 'lucide-react';
import { PageLayout } from '../../components/layout';
import {
    Button, Card, Loading, Empty, StatusBadge, Modal, ModalFooter,
    Input, Textarea, Select, ConfirmModal
} from '../../components/common';
import { useToast } from '../../context/ToastContext';
import { getProducts, createProduct, updateProduct, deleteProduct, updateProductImages, deleteProductImage } from '../../api/menu';
import { getCategories } from '../../api/menu';
import { formatPrice } from '../../utils/format';
import './ProductListPage.css';

export function ProductListPage() {
    const toast = useToast();
    const [products, setProducts] = useState([]);
    const [categories, setCategories] = useState([]);
    const [loading, setLoading] = useState(true);

    // Server-side Filtering & Pagination
    const [searchTerm, setSearchTerm] = useState('');
    const [debouncedSearch, setDebouncedSearch] = useState('');
    const [selectedCategory, setSelectedCategory] = useState('all');
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const pageSize = 20;

    // Modal states
    const [showModal, setShowModal] = useState(false);
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [editingProduct, setEditingProduct] = useState(null);
    const [productToDelete, setProductToDelete] = useState(null);

    // Form states
    const [form, setForm] = useState({
        name: '',
        price: '',
        description: '',
        categoryId: '',
        status: 'AVAILABLE'
    });
    const [selectedFiles, setSelectedFiles] = useState([]);
    const [formLoading, setFormLoading] = useState(false);

    // Debounce search
    useEffect(() => {
        const timer = setTimeout(() => {
            setDebouncedSearch(searchTerm);
            setPage(0); // Reset to page 0 on search change
        }, 500);
        return () => clearTimeout(timer);
    }, [searchTerm]);

    // Reset page on category change
    useEffect(() => {
        setPage(0);
    }, [selectedCategory]);

    // Load categories once on mount
    useEffect(() => {
        loadCategories();
    }, []);

    useEffect(() => {
        loadProducts();
    }, [page, debouncedSearch, selectedCategory]);

    const loadCategories = async () => {
        try {
            const categoriesData = await getCategories();
            setCategories(categoriesData || []);
        } catch (error) {
            console.error('Failed to load categories:', error);
        }
    };

    const loadProducts = async () => {
        try {
            setLoading(true);

            const params = {
                page,
                size: pageSize,
                keyword: debouncedSearch || undefined,
                categoryId: selectedCategory !== 'all' ? selectedCategory : undefined
            };

            const productsData = await getProducts(params);
            setProducts(productsData?.content || []);
            setTotalPages(productsData?.totalPages || 0);
        } catch (error) {
            console.error('Failed to load products:', error);
            toast.error('Không thể tải dữ liệu: ' + error.message);
        } finally {
            setLoading(false);
        }
    };

    const loadData = async () => {
        await Promise.all([loadCategories(), loadProducts()]);
    };

    const resetForm = () => {
        setForm({
            name: '',
            price: '',
            description: '',
            categoryId: categories[0]?.id || '',
            status: 'AVAILABLE'
        });
        setSelectedFiles([]);
        setEditingProduct(null);
    };

    const handleOpenCreate = () => {
        resetForm();
        setShowModal(true);
    };

    const handleOpenEdit = (product) => {
        setEditingProduct(product);
        setForm({
            name: product.name,
            price: product.price,
            description: product.description || '',
            categoryId: product.categoryId,
            status: product.status || 'AVAILABLE'
        });
        setShowModal(true);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        try {
            setFormLoading(true);

            let productId;

            if (editingProduct) {
                // Update basic info
                await updateProduct(editingProduct.id, {
                    ...form,
                    price: Number(form.price)
                });
                productId = editingProduct.id;
                toast.success(`Đã cập nhật món "${form.name}"`);
            } else {
                // Create new
                const newProduct = await createProduct({
                    ...form,
                    price: Number(form.price)
                });
                productId = newProduct.id;
                toast.success(`Đã thêm món "${form.name}"`);
            }

            // Upload images if any
            if (selectedFiles.length > 0) {
                await updateProductImages(productId, selectedFiles);
                toast.success('Đã tải lên hình ảnh');
            }

            await loadData();
            setShowModal(false);
            resetForm();
        } catch (error) {
            toast.error(error.message);
        } finally {
            setFormLoading(false);
        }
    };

    const handleDeleteClick = (product) => {
        setProductToDelete(product);
        setShowDeleteModal(true);
    };

    const handleConfirmDelete = async () => {
        if (!productToDelete) return;

        try {
            setFormLoading(true);
            await deleteProduct(productToDelete.id);
            toast.success(`Đã xóa món "${productToDelete.name}"`);
            await loadData();
            setShowDeleteModal(false);
            setProductToDelete(null);
        } catch (error) {
            toast.error(error.message);
        } finally {
            setFormLoading(false);
        }
    };

    const handleDeleteImage = async (imageId) => {
        try {
            await deleteProductImage(imageId);
            toast.success('Đã xóa ảnh');
            await loadData();
            // Need to refresh editing product images if modal is open
            if (editingProduct) {
                const updatedProduct = products.find(p => p.id === editingProduct.id);
                if (updatedProduct) setEditingProduct(updatedProduct);
            }
        } catch (error) {
            toast.error(error.message);
        }
    };

    return (
        <PageLayout
            title="Quản lý thực đơn"
            actions={
                <Button onClick={handleOpenCreate}>
                    Thêm món
                </Button>
            }
        >
            <div className="filters-bar">
                <div className="search-box">
                    <Search size={18} />
                    <input
                        type="text"
                        placeholder="Tìm theo tên..."
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                    />
                </div>
                <select
                    className="category-filter"
                    value={selectedCategory}
                    onChange={(e) => setSelectedCategory(e.target.value)}
                >
                    <option value="all">Tất cả danh mục</option>
                    {categories.map(c => (
                        <option key={c.id} value={c.id}>{c.name}</option>
                    ))}
                </select>
            </div>

            {loading ? (
                <Loading />
            ) : products.length === 0 ? (
                <Empty
                    message="Không tìm thấy món ăn"
                    description="Thử thay đổi bộ lọc hoặc thêm món mới"
                />
            ) : (
                <>
                    <div className="product-grid-admin">
                        {products.map(product => (
                            <Card key={product.id} padding={false}>
                                <div className="product-item">
                                    <div className="product-img-wrapper">
                                        {product.images?.[0] ? (
                                            <img src={product.images[0].url} alt={product.name} />
                                        ) : (
                                            <div className="no-image-placeholder"><Image size={24} /></div>
                                        )}
                                    </div>
                                    <div className="product-content">
                                        <div className="product-header">
                                            <h4>{product.name}</h4>
                                            <StatusBadge status={product.status} />
                                        </div>
                                        <p className="product-price">{formatPrice(product.price)}</p>
                                        <p className="product-cat">
                                            {categories.find(c => c.id === product.categoryId)?.name || '-'}
                                        </p>

                                        <div className="product-actions">
                                            <button onClick={() => handleOpenEdit(product)} title="Sửa">
                                                <Pencil size={16} />
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            </Card>
                        ))}
                    </div>

                    {/* Pagination Controls */}
                    {totalPages > 1 && (
                        <div className="pagination-controls">
                            <Button
                                variant="secondary"
                                size="sm"
                                disabled={page === 0}
                                onClick={() => setPage(p => p - 1)}
                            >
                                <ChevronLeft size={16} />
                            </Button>
                            <span className="pagination-info">
                                Trang {page + 1} / {totalPages}
                            </span>
                            <Button
                                variant="secondary"
                                size="sm"
                                disabled={page >= totalPages - 1}
                                onClick={() => setPage(p => p + 1)}
                            >
                                <ChevronRight size={16} />
                            </Button>
                        </div>
                    )}
                </>
            )}

            {/* Create/Edit Modal */}
            <Modal
                isOpen={showModal}
                onClose={() => setShowModal(false)}
                title={editingProduct ? 'Sửa món ăn' : 'Thêm món mới'}
            >
                <form onSubmit={handleSubmit}>
                    <div className="form-group">
                        <Input
                            label="Tên món"
                            value={form.name}
                            onChange={(e) => setForm({ ...form, name: e.target.value })}
                            required
                        />
                    </div>
                    <div className="form-row">
                        <div className="form-group">
                            <Input
                                label="Giá bán"
                                type="number"
                                value={form.price}
                                onChange={(e) => setForm({ ...form, price: e.target.value })}
                                required
                            />
                        </div>
                        <div className="form-group">
                            <Select
                                label="Danh mục"
                                value={form.categoryId}
                                onChange={(e) => setForm({ ...form, categoryId: e.target.value })}
                                required
                            >
                                {categories.map(c => (
                                    <option key={c.id} value={c.id}>{c.name}</option>
                                ))}
                            </Select>
                        </div>
                    </div>

                    <div className="form-group">
                        <Textarea
                            label="Mô tả"
                            value={form.description}
                            onChange={(e) => setForm({ ...form, description: e.target.value })}
                        />
                    </div>

                    <div className="form-group">
                        <Select
                            label="Trạng thái"
                            value={form.status}
                            onChange={(e) => setForm({ ...form, status: e.target.value })}
                        >
                            <option value="AVAILABLE">Đang bán</option>
                            <option value="OUT_OF_STOCK">Hết hàng</option>
                            <option value="HIDDEN">Ẩn món</option>
                        </Select>
                    </div>

                    <div className="form-group">
                        <label className="input-label">Hình ảnh</label>
                        <input
                            type="file"
                            multiple
                            onChange={(e) => setSelectedFiles(Array.from(e.target.files))}
                            accept="image/*"
                        />
                        {editingProduct?.images?.length > 0 && (
                            <div className="current-images">
                                <p>Ảnh hiện tại:</p>
                                <div className="image-list">
                                    {editingProduct.images.map(img => (
                                        <div key={img.id} className="img-preview">
                                            <img src={img.url} alt="Preview" />
                                            <button type="button" onClick={() => handleDeleteImage(img.id)}>
                                                <Trash2 size={12} />
                                            </button>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>

                    <ModalFooter>
                        <Button variant="secondary" onClick={() => setShowModal(false)}>Hủy</Button>
                        <Button type="submit" loading={formLoading}>
                            {editingProduct ? 'Lưu thay đổi' : 'Thêm mới'}
                        </Button>
                    </ModalFooter>
                </form>
            </Modal>

            {/* Confirm Delete Modal */}
            <ConfirmModal
                isOpen={showDeleteModal}
                onClose={() => setShowDeleteModal(false)}
                onConfirm={handleConfirmDelete}
                title="Xóa món ăn"
                message={`Bạn có chắc muốn xóa món "${productToDelete?.name}"?`}
                description="Hành động này không thể hoàn tác."
                variant="danger"
                loading={formLoading}
            />
        </PageLayout>
    );
}
