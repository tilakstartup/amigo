-- Create water_logs table
CREATE TABLE IF NOT EXISTS public.water_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users_profiles(id) ON DELETE CASCADE,
    volume_ml NUMERIC(6,2) NOT NULL CHECK (volume_ml > 0),
    logged_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_water_logs_user_id ON public.water_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_water_logs_logged_at ON public.water_logs(logged_at DESC);
CREATE INDEX IF NOT EXISTS idx_water_logs_user_logged ON public.water_logs(user_id, logged_at);

-- Enable Row Level Security
ALTER TABLE public.water_logs ENABLE ROW LEVEL SECURITY;

-- RLS Policy: Users can only view their own water logs
CREATE POLICY "Users can view own water logs"
    ON public.water_logs
    FOR SELECT
    USING (auth.uid() = user_id);

-- RLS Policy: Users can insert their own water logs
CREATE POLICY "Users can insert own water logs"
    ON public.water_logs
    FOR INSERT
    WITH CHECK (auth.uid() = user_id);

-- RLS Policy: Users can update their own water logs
CREATE POLICY "Users can update own water logs"
    ON public.water_logs
    FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- RLS Policy: Users can delete their own water logs
CREATE POLICY "Users can delete own water logs"
    ON public.water_logs
    FOR DELETE
    USING (auth.uid() = user_id);

-- Create fasting_sessions table
CREATE TABLE IF NOT EXISTS public.fasting_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users_profiles(id) ON DELETE CASCADE,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ,
    target_duration_hours NUMERIC(4,2) NOT NULL CHECK (target_duration_hours > 0),
    protocol TEXT CHECK (protocol IN ('16:8', '18:6', '20:4', 'custom')),
    status TEXT NOT NULL CHECK (status IN ('active', 'completed', 'cancelled')) DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_fasting_sessions_user_id ON public.fasting_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_fasting_sessions_start_time ON public.fasting_sessions(start_time DESC);
CREATE INDEX IF NOT EXISTS idx_fasting_sessions_status ON public.fasting_sessions(status);

-- Enable Row Level Security
ALTER TABLE public.fasting_sessions ENABLE ROW LEVEL SECURITY;

-- RLS Policy: Users can only view their own fasting sessions
CREATE POLICY "Users can view own fasting sessions"
    ON public.fasting_sessions
    FOR SELECT
    USING (auth.uid() = user_id);

-- RLS Policy: Users can insert their own fasting sessions
CREATE POLICY "Users can insert own fasting sessions"
    ON public.fasting_sessions
    FOR INSERT
    WITH CHECK (auth.uid() = user_id);

-- RLS Policy: Users can update their own fasting sessions
CREATE POLICY "Users can update own fasting sessions"
    ON public.fasting_sessions
    FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- RLS Policy: Users can delete their own fasting sessions
CREATE POLICY "Users can delete own fasting sessions"
    ON public.fasting_sessions
    FOR DELETE
    USING (auth.uid() = user_id);
