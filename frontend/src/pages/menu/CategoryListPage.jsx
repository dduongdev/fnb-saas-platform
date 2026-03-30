import { useState, useEffect } from 'react';
import { Plus, Trash2, Layers } from 'lucide-react';
import { PageLayout } from '../../components/layout';
import { useToast } from '../../context/ToastContext';
import { Button, Card, Loading, Empty, Modal, ModalFooter, Input } from '../../components/common';
import { getCategories, createCategory, deleteCategory } from '../../api/menu';
import './CategoryListPage.css';

export function CategoryListPage() {
    const toast = useToast();
    const [categories, setCategories] = useState([]);
    const [loading, setLoading] = useState(true);
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [newName, setNewName] = useState('');
    const [createLoading, setCreateLoading] = useState(false);

    useEffect(() => {
        loadCategories();
    }, []);

    const loadCategories = async () => {
        try {
            setLoading(true);
            const data = await getCategories();
            setCategories(data || []);
        } catch (error) {
            console.error('Failed to load categories:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleCreate = async (e) => {
        e.preventDefault();
        if (!newName.trim()) return;

        try {
            setCreateLoading(true);
            await createCategory(newName);
            await loadCategories();
            setShowCreateModal(false);
            setNewName('');
        } catch (error) {
            toast.error(error.message);
        } finally {
            setCreateLoading(false);
        }
    };

    const handleDelete = async (category) => {
        if (!confirm(`Xóa danh mục "${category.name}"? Các sản phẩm trong danh mục này sẽ bị ảnh hưởng.`)) return;

        try {
            await deleteCategory(category.id);
            await loadCategories();
        } catch (error) {
            toast.error(error.message);
        }
    };

    if (loading) {
        return (
            <PageLayout title="Quản lý danh mục">
                <Loading />
            </PageLayout>
        );
    }

    return (
        <PageLayout
            title="Quản lý danh mục"
            actions={
                <Button onClick={() => setShowCreateModal(true)}>
                    Thêm danh mục
                </Button>
            }
        >
            <Card padding={false}>
                <table className="category-table">
                    <thead>
                        <tr>
                            <th>Tên danh mục</th>
                            <th>Hành động</th>
                        </tr>
                    </thead>
                    <tbody>
                        {categories.map(category => (
                            <tr key={category.id}>
                                <td className="category-name">{category.name}</td>
                                <td>
                                    {!category.isDefault && (
                                        <button
                                            className="action-btn action-btn-danger"
                                            onClick={() => handleDelete(category)}
                                            title="Xóa"
                                        >
                                            <Trash2 size={16} />
                                        </button>
                                    )}
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </Card>

            {/* Create Modal */}
            <Modal
                isOpen={showCreateModal}
                onClose={() => { setShowCreateModal(false); setNewName(''); }}
                title="Thêm danh mục"
                size="sm"
            >
                <form onSubmit={handleCreate}>
                    <Input
                        label="Tên danh mục"
                        placeholder="VD: Đồ uống, Món chính"
                        value={newName}
                        onChange={(e) => setNewName(e.target.value)}
                        required
                    />
                    <ModalFooter>
                        <Button variant="secondary" onClick={() => setShowCreateModal(false)}>
                            Hủy
                        </Button>
                        <Button type="submit" loading={createLoading}>
                            Thêm
                        </Button>
                    </ModalFooter>
                </form>
            </Modal>
        </PageLayout>
    );
}
