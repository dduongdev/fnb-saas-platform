-- =====================================================
-- MIGRATION: Order-Table to Session-based Architecture
-- =====================================================
-- This script migrates the database schema from the old Order-Table
-- direct relationship to the new Session-based architecture.
--
-- Run this script AFTER deploying the new code with Hibernate auto-update.
-- This will handle existing data migration.
-- =====================================================

-- Step 1: Create serving_sessions table if not exists (Hibernate auto-creates)
-- CREATE TABLE IF NOT EXISTS serving_sessions (
--     id BIGINT AUTO_INCREMENT PRIMARY KEY,
--     status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
--     started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
--     ended_at TIMESTAMP NULL,
--     guest_count INT NULL,
--     note VARCHAR(500) NULL,
--     tenant_id BIGINT NOT NULL,
--     created_by VARCHAR(255) NULL,
--     updated_by VARCHAR(255) NULL,
--     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
--     updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
--     CONSTRAINT fk_session_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
-- );

-- Step 2: Add new columns to existing tables (Hibernate auto-creates)
-- ALTER TABLE dining_tables ADD COLUMN current_session_id BIGINT NULL;
-- ALTER TABLE orders ADD COLUMN session_id BIGINT NULL;

-- =====================================================
-- DATA MIGRATION: Migrate existing orders to sessions
-- =====================================================

-- Step 3: Create sessions for existing ACTIVE orders that have a table
INSERT INTO serving_sessions (status, started_at, tenant_id, created_at, updated_at)
SELECT DISTINCT 
    'ACTIVE',
    o.created_at,
    o.tenant_id,
    NOW(),
    NOW()
FROM orders o
WHERE o.table_id IS NOT NULL 
  AND o.status NOT IN ('COMPLETED', 'CANCELLED')
  AND NOT EXISTS (
      SELECT 1 FROM serving_sessions s 
      WHERE s.id = o.session_id
  );

-- Step 4: Link orders to their new sessions
-- This update links each order to a session based on table_id
UPDATE orders o
SET o.session_id = (
    SELECT s.id 
    FROM serving_sessions s 
    INNER JOIN dining_tables dt ON dt.current_session_id = s.id
    WHERE dt.id = o.table_id
    LIMIT 1
)
WHERE o.table_id IS NOT NULL 
  AND o.session_id IS NULL
  AND o.status NOT IN ('COMPLETED', 'CANCELLED');

-- Step 5: Link tables to sessions
UPDATE dining_tables dt
SET dt.current_session_id = (
    SELECT o.session_id 
    FROM orders o 
    WHERE o.table_id = dt.id 
      AND o.status NOT IN ('COMPLETED', 'CANCELLED')
      AND o.session_id IS NOT NULL
    ORDER BY o.created_at DESC
    LIMIT 1
)
WHERE dt.status = 'OCCUPIED'
  AND dt.current_session_id IS NULL;

-- =====================================================
-- MIGRATE MERGED TABLES (master_table_id → session)
-- =====================================================

-- Step 6: For tables that were merged (have master_table_id),
-- link them to the same session as their master table
UPDATE dining_tables slave_table
SET slave_table.current_session_id = (
    SELECT master_table.current_session_id
    FROM dining_tables master_table
    WHERE master_table.id = slave_table.master_table_id
      AND master_table.current_session_id IS NOT NULL
)
WHERE slave_table.master_table_id IS NOT NULL
  AND slave_table.current_session_id IS NULL;

-- =====================================================
-- CLEANUP: Optional - Remove deprecated columns later
-- =====================================================
-- After verifying the migration, these columns can be dropped:
-- 
-- ALTER TABLE orders DROP COLUMN table_id;
-- ALTER TABLE dining_tables DROP COLUMN master_table_id;
--
-- Note: Keep these columns during transition period for backward compatibility.

-- =====================================================
-- REQUIRED: Make table_id nullable (run this first!)
-- =====================================================
-- The new Session-based architecture no longer uses table_id in orders.
-- This column must be nullable to allow new orders without table_id.

ALTER TABLE orders MODIFY COLUMN table_id INT NULL;

-- =====================================================
-- VERIFICATION QUERIES
-- =====================================================

-- Check all active orders have sessions
-- SELECT COUNT(*) as orders_without_session
-- FROM orders 
-- WHERE status NOT IN ('COMPLETED', 'CANCELLED') 
--   AND table_id IS NOT NULL 
--   AND session_id IS NULL;

-- Check all occupied tables have sessions
-- SELECT COUNT(*) as tables_without_session
-- FROM dining_tables 
-- WHERE status = 'OCCUPIED' 
--   AND current_session_id IS NULL;

-- List sessions with their tables
-- SELECT 
--     s.id as session_id,
--     s.status as session_status,
--     GROUP_CONCAT(dt.name) as tables,
--     COUNT(o.id) as order_count
-- FROM serving_sessions s
-- LEFT JOIN dining_tables dt ON dt.current_session_id = s.id
-- LEFT JOIN orders o ON o.session_id = s.id
-- WHERE s.status = 'ACTIVE'
-- GROUP BY s.id;
