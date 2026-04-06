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

## Notes

- The script creates dedicated test user, tenant, tables, and products in `setup()`.
- If backend returns business errors (`code != 200`) those are counted in `business_error_rate`.
- Thresholds are strict (`<1%`) to detect regressions early.
