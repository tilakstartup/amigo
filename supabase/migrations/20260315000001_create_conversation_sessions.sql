-- Create conversation_sessions table for Strands agent session management
CREATE TABLE IF NOT EXISTS public.conversation_sessions (
    session_id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES public.users_profiles(id) ON DELETE CASCADE,
    hat TEXT NOT NULL,
    messages JSONB NOT NULL DEFAULT '[]',
    data_collected JSONB NOT NULL DEFAULT '{}',
    aim_status TEXT NOT NULL DEFAULT 'not_set'
        CHECK (aim_status IN ('not_set', 'in_progress', 'completed')),
    is_expired BOOLEAN NOT NULL DEFAULT false,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Index for the primary session lookup: find active sessions by user + hat
CREATE INDEX idx_conversation_sessions_user_hat
    ON public.conversation_sessions (user_id, hat);

-- Partial index for resumable session queries (only non-expired rows)
CREATE INDEX idx_conversation_sessions_resumable
    ON public.conversation_sessions (user_id, hat)
    WHERE is_expired = false;

-- Index for time-based expiry sweeps
CREATE INDEX idx_conversation_sessions_updated_at
    ON public.conversation_sessions (updated_at);

-- Reuse the existing handle_updated_at() trigger function
CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON public.conversation_sessions
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_updated_at();

-- Enable Row Level Security
ALTER TABLE public.conversation_sessions ENABLE ROW LEVEL SECURITY;

-- RLS: users can read their own sessions (used by get-session endpoint via user JWT)
-- All writes (INSERT / UPDATE) are performed by the edge function using the service-role
-- key which bypasses RLS entirely — no client-side write policies needed.
CREATE POLICY "Users can view own sessions"
    ON public.conversation_sessions
    FOR SELECT
    USING (auth.uid() = user_id);
