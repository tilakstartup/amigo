-- Add session_config column to store the full SessionConfig (hat, responsibilities, etc.)
ALTER TABLE public.conversation_sessions
    ADD COLUMN IF NOT EXISTS session_config JSONB NOT NULL DEFAULT '{}';
