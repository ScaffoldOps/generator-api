ALTER TABLE generation_requests ADD COLUMN IF NOT EXISTS recovery_reserved_until timestamptz;
