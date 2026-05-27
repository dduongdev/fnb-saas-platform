# CI/CD Quick Setup

This file describes minimal steps to enable the `deploy-staging` workflow.

Required repository secrets (GitHub Settings → Secrets):
- `STAGING_HOST` - staging host IP or hostname (e.g. 34.21.214.96)
- `STAGING_SSH_USER` - SSH username on staging host (e.g. ASUS)
- `STAGING_SSH_KEY` - private SSH key (RSA/ED25519) for the `STAGING_SSH_USER`
- `STAGING_SSH_PORT` - optional (defaults to 22)

Workflow: `.github/workflows/deploy-staging.yml`
- Trigger: push to `develop`
- Steps: build backend/frontend, upload artifacts, SSH to staging host and run `infra/scripts/deploy_to_staging.sh`.

On the staging host, ensure:
- Repository cloned at `/home/<user>/fnb-saas-platform` and `infra/scripts/deploy_to_staging.sh` executable.
- Docker and `docker compose` plugin installed.
- Configure DB backup/migration scripts as needed (see `infra/scripts/deploy_to_staging.sh` placeholders).

Next steps:
- Implement `backend/run_migrations.sh` to run Flyway/Liquibase or appropriate migration tool.
- Add production workflow with manual approval and DB snapshot steps.
