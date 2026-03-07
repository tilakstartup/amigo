-- Create oauth_tokens table
CREATE TABLE IF NOT EXISTS public.oauth_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users_profiles(id) ON DELETE CASCADE,
    platform TEXT NOT NULL CHECK (platform IN ('fitbit', 'garmin', 'apple_health', 'google_health_connect')),
    access_token TEXT NOT NULL,
    refresh_token TEXT,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, platform)
);

-- Create indexes for efficient queries
CREATE INDEX idx_oauth_tokens_user_id ON public.oauth_tokens(user_id);
CREATE INDEX idx_oauth_tokens_platform ON public.oauth_tokens(platform);

-- Enable Row Level Security
ALTER TABLE public.oauth_tokens ENABLE ROW LEVEL SECURITY;

-- RLS Policy: Users can only view their own OAuth tokens
CREATE POLICY "Users can view own OAuth tokens"
    ON public.oauth_tokens
    FOR SELECT
    USING (auth.uid() = user_id);

-- RLS Policy: Users can insert their own OAuth tokens
CREATE POLICY "Users can insert own OAuth tokens"
    ON public.oauth_tokens
    FOR INSERT
    WITH CHECK (auth.uid() = user_id);

-- RLS Policy: Users can update their own OAuth tokens
CREATE POLICY "Users can update own OAuth tokens"
    ON public.oauth_tokens
    FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- RLS Policy: Users can delete their own OAuth tokens
CREATE POLICY "Users can delete own OAuth tokens"
    ON public.oauth_tokens
    FOR DELETE
    USING (auth.uid() = user_id);

-- Create trigger to update updated_at on oauth_tokens
CREATE TRIGGER set_oauth_tokens_updated_at
    BEFORE UPDATE ON public.oauth_tokens
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_updated_at();
