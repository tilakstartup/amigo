-- Create meal_logs table
CREATE TABLE IF NOT EXISTS public.meal_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users_profiles(id) ON DELETE CASCADE,
    meal_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    input_method TEXT NOT NULL CHECK (input_method IN ('image', 'voice', 'text', 'barcode', 'manual')),
    photo_url TEXT,
    food_items JSONB NOT NULL,
    total_calories NUMERIC(7,2),
    total_protein_g NUMERIC(6,2),
    total_carbs_g NUMERIC(6,2),
    total_fat_g NUMERIC(6,2),
    data_source TEXT CHECK (data_source IN ('ai_estimate', 'usda', 'barcode', 'custom', 'manual')),
    barcode TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create indexes for efficient queries
CREATE INDEX idx_meal_logs_user_id ON public.meal_logs(user_id);
CREATE INDEX idx_meal_logs_meal_time ON public.meal_logs(meal_time DESC);
CREATE INDEX idx_meal_logs_user_time ON public.meal_logs(user_id, meal_time DESC);

-- Enable Row Level Security
ALTER TABLE public.meal_logs ENABLE ROW LEVEL SECURITY;

-- RLS Policy: Users can only view their own meal logs
CREATE POLICY "Users can view own meal logs"
    ON public.meal_logs
    FOR SELECT
    USING (auth.uid() = user_id);

-- RLS Policy: Users can insert their own meal logs
CREATE POLICY "Users can insert own meal logs"
    ON public.meal_logs
    FOR INSERT
    WITH CHECK (auth.uid() = user_id);

-- RLS Policy: Users can update their own meal logs
CREATE POLICY "Users can update own meal logs"
    ON public.meal_logs
    FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- RLS Policy: Users can delete their own meal logs
CREATE POLICY "Users can delete own meal logs"
    ON public.meal_logs
    FOR DELETE
    USING (auth.uid() = user_id);
