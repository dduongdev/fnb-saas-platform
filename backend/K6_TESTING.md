# POS k6 Testing Guide

## Script files

- `backend/k6_load_test.js`: Main k6 script for POS.
- `backend/k6_data.json`: External data loaded through `SharedArray`.

## Environment variables

- `BASE_URL`: Backend URL, default `http://localhost:8081`.
- `TEST_PROFILE`: `smoke` or `stress`.
- `DATA_FILE`: Path to data json loaded by k6.
- `STRESS_SCALE`: Scale factor for stress VU targets. Default `1`.
- `STRICT_MODE`: `true` => fail ngay khi một step trọng yếu lỗi. Mặc định `true` cho smoke, `false` cho stress.

## Profiles

- `smoke`: very small run to validate business flow correctness.
- `stress`: mixed load profile, with high read-heavy pressure and moderate write pressure.

## Run smoke on Windows (Docker k6)

```powershell
$env:BASE_URL = "http://host.docker.internal:8081"
$env:TEST_PROFILE = "smoke"
$env:DATA_FILE = "/work/backend/k6_data.json"
$env:STRICT_MODE = "true"
docker run --rm -i `
  -e BASE_URL=$env:BASE_URL `
  -e TEST_PROFILE=$env:TEST_PROFILE `
  -e DATA_FILE=$env:DATA_FILE `
  -e STRICT_MODE=$env:STRICT_MODE `
  -v ${PWD}:/work `
  grafana/k6 run /work/backend/k6_load_test.js
```

## Run stress on Windows (Docker k6)

```powershell
$env:BASE_URL = "http://host.docker.internal:8081"
$env:TEST_PROFILE = "stress"
$env:DATA_FILE = "/work/backend/k6_data.json"
$env:STRESS_SCALE = "1"
$env:STRICT_MODE = "false"
docker run --rm -i `
  -e BASE_URL=$env:BASE_URL `
  -e TEST_PROFILE=$env:TEST_PROFILE `
  -e DATA_FILE=$env:DATA_FILE `
  -e STRESS_SCALE=$env:STRESS_SCALE `
  -e STRICT_MODE=$env:STRICT_MODE `
  -v ${PWD}:/work `
  grafana/k6 run /work/backend/k6_load_test.js
```

Local sanity stress suggestion: set `STRESS_SCALE=0.1`.

## Run stress on GCloud VM

If you want to host a single load-generator machine on GCloud, use an E2 custom VM sized at 16 vCPU and 16 GB RAM. If you are thinking in CPU cores, that maps to 8 physical cores with SMT on many VM shapes. On GCP, this is typically created as `e2-custom-16-16384`.

```powershell
gcloud compute instances create k6-host-1 `
  --zone=asia-southeast1-b `
  --machine-type=e2-custom-16-16384 `
  --image-family=ubuntu-2204-lts `
  --image-project=ubuntu-os-cloud `
  --boot-disk-size=50GB
```

Then SSH into the VM, install Docker, copy this repo, and run:

```bash
docker run --rm -i \
  -e BASE_URL=http://<backend-ip>:8081 \
  -e TEST_PROFILE=stress \
  -e DATA_FILE=/work/backend/k6_data.json \
  -e STRESS_SCALE=1 \
  -e STRICT_MODE=false \
  -v /path/to/fnb-saas-platform:/work \
  grafana/k6 run /work/backend/k6_load_test.js
```

## Full-session hardware capture

If you want CPU and RAM usage for the whole test window, start the system metrics collector on the host before booting the backend and stop it after the test completes. The repo includes a reusable collector at `infra/scripts/collect_system_metrics.sh` plus systemd units for host and runner:

- `infra/systemd/host-metrics.service`
- `infra/systemd/runner-metrics.service`

The collector writes timestamped snapshots with `uptime`, `free -h`, and `docker stats --no-stream` into a log file so you can review the full session, not just a single snapshot.

If you truly need 16 vCPU on GCloud, the memory footprint will be higher than 16 GB on E2; in that case, pick a larger machine family or a custom machine that matches the target RAM.

## Notes

- The script creates dedicated test user, tenant, tables, and products in `setup()`.
- If backend returns business errors (`code != 200`) those are counted in `business_error_rate`.
- Thresholds are strict (`<1%`) to detect regressions early.
