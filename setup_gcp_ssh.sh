#!/usr/bin/env bash
# =============================================================
#  setup_gcp_ssh.sh
#  Script tự động hoá GCP + SSH cho CI/CD
#
#  Chức năng:
#    1. Tạo Service Account + JSON key (xác thực GitHub → GCP)
#    2. Cấp quyền IAM
#    3. Tạo cặp SSH Key id_rsa_cicd
#    4. Đẩy Public Key lên metadata của instance
#    5. Lấy thông tin instance (zone, IP)
#
#  Yêu cầu:
#    - gcloud CLI, đã đăng nhập (gcloud auth login)
#    - Quyền Owner hoặc các quyền tương đương
# =============================================================

set -euo pipefail

# =============================================================
#  ██████   CONFIG  —  Điền thông tin của bạn vào đây
# =============================================================

GCP_PROJECT_ID="fnb-saas-platform"            # ID project GCP (đã được tạo)

TEST_INSTANCE_NAME="fnb-staging-vm"          # Tên máy Test
TEST_INSTANCE_ZONE="us-central1-a"           # Zone của máy Test

PROD_INSTANCE_NAME="fnb-production-vm"       # Tên máy Prod
PROD_INSTANCE_ZONE="us-central1-a"           # Zone của máy Prod

SSH_USER="ci-cd"                            # Username SSH (dùng cho metadata & secrets)
SA_NAME="github-actions-cicd"               # Tên Service Account

# =============================================================
#  Kiểm tra gcloud
# =============================================================
echo "🔍 Kiểm tra gcloud..."
if ! command -v gcloud &>/dev/null; then
    echo "❌ Chưa cài gcloud CLI. Xem: https://cloud.google.com/sdk/docs/install"
    exit 1
fi

ACTIVE_ACCOUNT=$(gcloud auth list --filter=status:ACTIVE --format="value(account)" 2>/dev/null || true)
if [ -z "$ACTIVE_ACCOUNT" ]; then
    echo "❌ Chưa đăng nhập gcloud. Chạy: gcloud auth login"
    exit 1
fi
echo "   ✅ Đã login: $ACTIVE_ACCOUNT"

echo ""
echo "🔍 Kiểm tra project $GCP_PROJECT_ID..."
gcloud projects describe "$GCP_PROJECT_ID" > /dev/null 2>&1 || {
    echo "❌ Không tìm thấy project $GCP_PROJECT_ID"
    exit 1
}
echo "   ✅ Project OK"

# =============================================================
#  Bước 1: Tạo Service Account + JSON Key
# =============================================================
echo ""
echo "══════════════════════════════════════════════"
echo "  Bước 1: Tạo Service Account"
echo "══════════════════════════════════════════════"

SA_EMAIL="$SA_NAME@$GCP_PROJECT_ID.iam.gserviceaccount.com"

if gcloud iam service-accounts describe "$SA_EMAIL" --project="$GCP_PROJECT_ID" > /dev/null 2>&1; then
    echo "   ✅ SA đã tồn tại: $SA_EMAIL"
else
    gcloud iam service-accounts create "$SA_NAME" \
        --project="$GCP_PROJECT_ID" \
        --display-name="GitHub Actions CI/CD SA"
    echo "   ✅ Đã tạo: $SA_EMAIL"
fi

# Cấp quyền — bao gồm cả IAP Tunnel User nếu dùng IAP
echo ""
echo "   Đang cấp quyền IAM..."
ROLES=(
    "roles/compute.instanceAdmin.v1"
    "roles/iam.serviceAccountUser"
    "roles/iap.tunnelResourceAccessor"     # Cần nếu dùng IAP (use_iap: true)
)
for ROLE in "${ROLES[@]}"; do
    gcloud projects add-iam-policy-binding "$GCP_PROJECT_ID" \
        --member="serviceAccount:$SA_EMAIL" \
        --role="$ROLE" \
        --condition=None \
        --quiet > /dev/null 2>&1 || true
    echo "      ✅ $ROLE"
done

# Tải JSON key
KEY_FILE="$HOME/gcp-sa-$SA_NAME.json"
if [ -f "$KEY_FILE" ]; then
    rm -f "$KEY_FILE"
fi
gcloud iam service-accounts keys create "$KEY_FILE" \
    --project="$GCP_PROJECT_ID" \
    --iam-account="$SA_EMAIL"
echo "   ✅ JSON key: $KEY_FILE"

# =============================================================
#  Bước 2: Tạo SSH Key
# =============================================================
echo ""
echo "══════════════════════════════════════════════"
echo "  Bước 2: Tạo SSH Key (id_rsa_cicd)"
echo "══════════════════════════════════════════════"

SSH_KEY_PATH="$HOME/.ssh/id_rsa_cicd"
SSH_PUB_PATH="${SSH_KEY_PATH}.pub"

if [ -f "$SSH_KEY_PATH" ]; then
    echo "   ⚠️  SSH Key đã tồn tại. Bỏ qua (xoá bằng rm -f $SSH_KEY_PATH $SSH_PUB_PATH nếu muốn tạo lại)"
else
    mkdir -p "$HOME/.ssh"
    ssh-keygen -t rsa -b 4096 -f "$SSH_KEY_PATH" -N "" -C "$SSH_USER"
    chmod 600 "$SSH_KEY_PATH"
    echo "   ✅ Đã tạo: $SSH_KEY_PATH"
fi

# =============================================================
#  Bước 3: Đẩy Public Key lên metadata của instance
# =============================================================
echo ""
echo "══════════════════════════════════════════════"
echo "  Bước 3: Đẩy Public Key lên metadata"
echo "══════════════════════════════════════════════"

PUB_KEY_LINE=$(echo "$SSH_USER:$(cat $SSH_PUB_PATH)")

echo ""
echo "--- Máy TEST: $TEST_INSTANCE_NAME ---"
gcloud compute instances add-metadata "$TEST_INSTANCE_NAME" \
    --zone="$TEST_INSTANCE_ZONE" \
    --project="$GCP_PROJECT_ID" \
    --metadata "ssh-keys=$PUB_KEY_LINE"
echo "   ✅ OK"

echo ""
echo "--- Máy PROD: $PROD_INSTANCE_NAME ---"
gcloud compute instances add-metadata "$PROD_INSTANCE_NAME" \
    --zone="$PROD_INSTANCE_ZONE" \
    --project="$GCP_PROJECT_ID" \
    --metadata "ssh-keys=$PUB_KEY_LINE"
echo "   ✅ OK"

# =============================================================
#  Bước 4: Lấy thông tin instance
# =============================================================
echo ""
echo "══════════════════════════════════════════════"
echo "  Bước 4: Thông tin instance"
echo "══════════════════════════════════════════════"

echo ""
echo "--- Máy TEST ---"
gcloud compute instances describe "$TEST_INSTANCE_NAME" \
    --zone="$TEST_INSTANCE_ZONE" \
    --project="$GCP_PROJECT_ID" \
    --format="value(name,zone.basename(),networkInterfaces[0].accessConfigs[0].natIP)" \
    | tee /tmp/gcp_test_info.txt

echo ""
echo "--- Máy PROD ---"
gcloud compute instances describe "$PROD_INSTANCE_NAME" \
    --zone="$PROD_INSTANCE_ZONE" \
    --project="$GCP_PROJECT_ID" \
    --format="value(name,zone.basename(),networkInterfaces[0].accessConfigs[0].natIP)" \
    | tee /tmp/gcp_prod_info.txt

# =============================================================
#  TỔNG KẾT
# =============================================================
echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║              ✅  HOÀN TẤT — THÊM GITHUB SECRETS              ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

cat << SECRETS_EOF
   Mở GitHub → Repo Settings → Secrets and variables → Actions
   Thêm các Repository secrets SAU ĐÂY:

┌──────────────────────┬──────────────────────────────────────────────────────┐
│ Tên Secret           │ Giá trị                                              │
├──────────────────────┼──────────────────────────────────────────────────────┤
│ GCP_SA_KEY           │ Nội dung file → cat "$KEY_FILE"                      │
│ GCP_PROJECT_ID       │ $GCP_PROJECT_ID                                      │
│ GCP_SSH_PRIVATE_KEY  │ Nội dung file → cat "$SSH_KEY_PATH"                  │
│ GCP_SSH_USER         │ $SSH_USER                                            │
│ GCP_TEST_INSTANCE    │ $TEST_INSTANCE_NAME                                  │
│ GCP_TEST_ZONE        │ $TEST_INSTANCE_ZONE                                  │
│ GCP_PROD_INSTANCE    │ $PROD_INSTANCE_NAME                                  │
│ GCP_PROD_ZONE        │ $PROD_INSTANCE_ZONE                                  │
└──────────────────────┴──────────────────────────────────────────────────────┘

   💡 Copy nhanh:
     Windows:  cat "$KEY_FILE" | clip   &&   cat "$SSH_KEY_PATH" | clip
     macOS:    cat "$KEY_FILE" | pbcopy &&   cat "$SSH_KEY_PATH" | pbcopy

   📄 Xem SERVER_SETUP.md để biết cách SSH vào server và clone code lần đầu.
SECRETS_EOF

echo ""
echo "   ⚠️  QUAN TRỌNG: Chạy lệnh sau trên mỗi máy GCP (nếu cần):"
echo "       sudo usermod -aG docker $SSH_USER"
echo "       (để user có quyền chạy Docker mà không cần sudo)"
echo ""
