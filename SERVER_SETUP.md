# Hướng dẫn chuẩn bị Server (lần đầu) — GCP

Tài liệu này hướng dẫn các lệnh thủ công cần chạy **1 lần duy nhất** trên từng máy GCP.

---

## Yêu cầu

- Đã chạy `setup_gcp_ssh.sh` thành công (đã có Service Account + SSH key đã được đẩy lên metadata)
- Đã cài `gcloud` CLI trên máy local

---

## 1. Thiết lập máy TEST (nhánh staging)

```bash
# Kết nối SSH vào máy Test (gcloud tự động xử lý xác thực)
gcloud compute ssh ci-cd@<TEN_INSTANCE_TEST> \
    --zone=<ZONE_INSTANCE_TEST> \
    --project=<PROJECT_ID> \
    --ssh-key-file=~/.ssh/id_rsa_cicd

# Ví dụ:
# gcloud compute ssh ci-cd@fnb-staging-vm --zone=us-central1-a --project=fnb-saas-project --ssh-key-file=~/.ssh/id_rsa_cicd
```

Sau khi SSH thành công **trên máy Test**, chạy:

```bash
# Clone project lần đầu (nhánh staging)
cd ~
git clone -b staging https://github.com/<USER>/<REPO>.git fnb-saas-platform
#                                    ↑ Thay bằng repo của bạn

cd ~/fnb-saas-platform

# Kiểm tra nhánh
git branch          # Phải hiển thị * staging

# Copy file .env cho staging
# ⚠️  Cần có file .env riêng cho staging
# Tạo file .env từ template hoặc copy từ máy local:
#   (Máy local) gcloud compute scp .env ci-cd@fnb-staging-vm:~/fnb-saas-platform/.env --zone=us-central1-a

# Khởi động ứng dụng lần đầu
docker compose up -d --build

# Kiểm tra
docker compose ps
```

> **Thoát SSH**: gõ `exit` hoặc `Ctrl+D`

---

## 2. Thiết lập máy PROD (nhánh production)

```bash
# Kết nối SSH vào máy Prod
gcloud compute ssh ci-cd@<TEN_INSTANCE_PROD> \
    --zone=<ZONE_INSTANCE_PROD> \
    --project=<PROJECT_ID> \
    --ssh-key-file=~/.ssh/id_rsa_cicd

# Ví dụ:
# gcloud compute ssh ci-cd@fnb-production-vm --zone=us-central1-a --project=fnb-saas-project --ssh-key-file=~/.ssh/id_rsa_cicd
```

Sau khi SSH thành công **trên máy Prod**, chạy:

```bash
# Clone project lần đầu (nhánh production)
cd ~
git clone -b production https://github.com/<USER>/<REPO>.git fnb-saas-platform

cd ~/fnb-saas-platform

# Kiểm tra nhánh
git branch          # Phải hiển thị * production

# Copy file .env cho production
# ⚠️  .env production phải khác staging (DB riêng, URL riêng, ...)

# Khởi động ứng dụng lần đầu
docker compose up -d --build

# Kiểm tra
docker compose ps
```

---

## 3. Kiểm tra thử GitHub Actions

1. Push 1 commit lên nhánh `staging`
2. Vào GitHub → Tab **Actions** → Xem workflow `Deploy to GCP (gcloud)` chạy
3. Nếu workflow xanh ✅ → thành công

---

## ⚠️ Lưu ý quan trọng

| Hạng mục | Ghi chú |
|----------|---------|
| **.env file** | Mỗi môi trường cần file `.env` riêng. KHÔNG commit `.env` lên Git! |
| **Firewall** | Mở port cần thiết (8081, 80, 443) trong GCP firewall |
| **Docker user** | Nếu user không có quyền Docker: `sudo usermod -aG docker $USER && newgrp docker` |
| **IAP (nếu dùng)** | Nếu instance không có public IP, cần bật IAP: `gcloud services enable iap.googleapis.com` + mở firewall `35.235.240.0/20` tcp:22 |
| **Service Account** | Key file `.json` là bí mật tuyệt đối, không commit lên Git! |
