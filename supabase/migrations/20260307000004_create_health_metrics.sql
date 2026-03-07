-- Create health_metrics table
CREATE TABLE IF NOT EXISTS public.health_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users_profiles(id) ON DELETE CASCADE,
    metric_type TEXT NOT NULL CHECK (metric_type IN ('steps', 'heart_rate', 'sleep', 'exercise', 'weight', 'blood_pressure')),
    value NUMERIC(10,2) NOT NULL,
    unit TEXT NOT NULL,
    source_platform TEXT NOT NULL CHECK (source_platform IN ('fitbit', 'garmin', 'apple_health', 'google_health_connect', 'manual')),
    timestamp TIMESTAMPTZ NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, metric_type, source_platform, timestamp)
);

-- Create indexes for efficient queries
CREATE INDEX idx_health_metrics_user_id ON public.health_metrics(user_id);
CREATE INDEX idx_health_metrics_type ON public.health_metrics(metric_type);
CREATE INDEX idx_health_metrics_timestamp ON public.health_metrics(timestamp DESC);
CREATE INDEX idx_health_metrics_user_type_time ON public.health_metrics(user_id, metric_type, timestamp DESC);

-- Enable Row Level Security
ALTER TABLE public.health_metrics ENABLE ROW LEVEL SECURITY;

-- RLS Policy: Users can only view their own health metrics
CREATE POLICY "Users can view own health metrics"
    ON public.health_metrics
    FOR SELECT
    USING (auth.uid() = user_id);

-- RLS Policy: Users can insert their own health metrics
CREATE POLICY "Users can insert own health metrics"
    ON public.health_metrics
    FOR INSERT
    WITH CHECK (auth.uid() = user_id);

-- RLS Policy: Users can update their own health metrics
CREATE POLICY "Users can update own health metrics"
    ON public.health_metrics
    FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- RLS Policy: Users can delete their own health metrics
CREATE POLICY "Users can delete own health metrics"
    ON public.health_metrics
    FOR DELETE
    USING (auth.uid() = user_id);
