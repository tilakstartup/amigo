-- Create health_goals table first (referenced by user_ai_context)
CREATE TABLE IF NOT EXISTS public.health_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users_profiles(id) ON DELETE CASCADE,
    goal_type TEXT NOT NULL CHECK (goal_type IN ('weight_loss', 'muscle_gain', 'maintenance', 'improved_energy', 'better_sleep')),
    start_date TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    end_date TIMESTAMPTZ,
    is_active BOOLEAN DEFAULT TRUE,
    goal_context JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create indexes for efficient queries
CREATE INDEX idx_health_goals_user_id ON public.health_goals(user_id);
CREATE INDEX idx_health_goals_is_active ON public.health_goals(is_active);
CREATE INDEX idx_health_goals_user_active ON public.health_goals(user_id, is_active);

-- Enable Row Level Security
ALTER TABLE public.health_goals ENABLE ROW LEVEL SECURITY;

-- RLS Policy: Users can only view their own health goals
CREATE POLICY "Users can view own health goals"
    ON public.health_goals
    FOR SELECT
    USING (auth.uid() = user_id);

-- RLS Policy: Users can insert their own health goals
CREATE POLICY "Users can insert own health goals"
    ON public.health_goals
    FOR INSERT
    WITH CHECK (auth.uid() = user_id);

-- RLS Policy: Users can update their own health goals
CREATE POLICY "Users can update own health goals"
    ON public.health_goals
    FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- RLS Policy: Users can delete their own health goals
CREATE POLICY "Users can delete own health goals"
    ON public.health_goals
    FOR DELETE
    USING (auth.uid() = user_id);

-- Create trigger to update updated_at on health_goals
CREATE TRIGGER set_health_goals_updated_at
    BEFORE UPDATE ON public.health_goals
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_updated_at();

-- Create user_ai_context table
CREATE TABLE IF NOT EXISTS public.user_ai_context (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES public.users_profiles(id) ON DELETE CASCADE,
    pattern_profile JSONB,
    preferences JSONB,
    coaching_style JSONB,
    context_summary TEXT,
    active_goal_id UUID REFERENCES public.health_goals(id) ON DELETE SET NULL,
    last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create index for efficient queries
CREATE INDEX idx_user_ai_context_user_id ON public.user_ai_context(user_id);
CREATE INDEX idx_user_ai_context_active_goal ON public.user_ai_context(active_goal_id);

-- Enable Row Level Security
ALTER TABLE public.user_ai_context ENABLE ROW LEVEL SECURITY;

-- RLS Policy: Users can only view their own AI context
CREATE POLICY "Users can view own AI context"
    ON public.user_ai_context
    FOR SELECT
    USING (auth.uid() = user_id);

-- RLS Policy: Users can insert their own AI context
CREATE POLICY "Users can insert own AI context"
    ON public.user_ai_context
    FOR INSERT
    WITH CHECK (auth.uid() = user_id);

-- RLS Policy: Users can update their own AI context
CREATE POLICY "Users can update own AI context"
    ON public.user_ai_context
    FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- RLS Policy: Users can delete their own AI context
CREATE POLICY "Users can delete own AI context"
    ON public.user_ai_context
    FOR DELETE
    USING (auth.uid() = user_id);

-- Create goal_history table
CREATE TABLE IF NOT EXISTS public.goal_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users_profiles(id) ON DELETE CASCADE,
    goal_id UUID NOT NULL REFERENCES public.health_goals(id) ON DELETE CASCADE,
    goal_type TEXT NOT NULL CHECK (goal_type IN ('weight_loss', 'muscle_gain', 'maintenance', 'improved_energy', 'better_sleep')),
    start_date TIMESTAMPTZ NOT NULL,
    end_date TIMESTAMPTZ NOT NULL,
    outcome_summary TEXT,
    transition_reason TEXT,
    goal_context JSONB,
    achievements JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create indexes for efficient queries
CREATE INDEX idx_goal_history_user_id ON public.goal_history(user_id);
CREATE INDEX idx_goal_history_goal_id ON public.goal_history(goal_id);
CREATE INDEX idx_goal_history_goal_type ON public.goal_history(goal_type);

-- Enable Row Level Security
ALTER TABLE public.goal_history ENABLE ROW LEVEL SECURITY;

-- RLS Policy: Users can only view their own goal history
CREATE POLICY "Users can view own goal history"
    ON public.goal_history
    FOR SELECT
    USING (auth.uid() = user_id);

-- RLS Policy: Users can insert their own goal history
CREATE POLICY "Users can insert own goal history"
    ON public.goal_history
    FOR INSERT
    WITH CHECK (auth.uid() = user_id);
