-- Add token_usage column to track per-turn input/output token counts
ALTER TABLE public.conversation_sessions
    ADD COLUMN IF NOT EXISTS token_usage JSONB NOT NULL DEFAULT '[]';
