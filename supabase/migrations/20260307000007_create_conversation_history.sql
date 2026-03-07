-- Create conversation_history table
CREATE TABLE IF NOT EXISTS public.conversation_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users_profiles(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('user', 'assistant')),
    message TEXT NOT NULL,
    feedback_rating INTEGER CHECK (feedback_rating >= 1 AND feedback_rating <= 5),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create indexes for efficient queries
CREATE INDEX idx_conversation_history_user_id ON public.conversation_history(user_id);
CREATE INDEX idx_conversation_history_created_at ON public.conversation_history(created_at DESC);
CREATE INDEX idx_conversation_history_user_time ON public.conversation_history(user_id, created_at DESC);

-- Enable Row Level Security
ALTER TABLE public.conversation_history ENABLE ROW LEVEL SECURITY;

-- RLS Policy: Users can only view their own conversation history
CREATE POLICY "Users can view own conversation history"
    ON public.conversation_history
    FOR SELECT
    USING (auth.uid() = user_id);

-- RLS Policy: Users can insert their own conversation history
CREATE POLICY "Users can insert own conversation history"
    ON public.conversation_history
    FOR INSERT
    WITH CHECK (auth.uid() = user_id);

-- RLS Policy: Users can update their own conversation history
CREATE POLICY "Users can update own conversation history"
    ON public.conversation_history
    FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- RLS Policy: Users can delete their own conversation history
CREATE POLICY "Users can delete own conversation history"
    ON public.conversation_history
    FOR DELETE
    USING (auth.uid() = user_id);
