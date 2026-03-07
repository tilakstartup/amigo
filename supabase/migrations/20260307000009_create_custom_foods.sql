-- Create custom_foods table
CREATE TABLE IF NOT EXISTS public.custom_foods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users_profiles(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    brand TEXT,
    serving_size TEXT NOT NULL,
    calories NUMERIC(7,2) NOT NULL,
    protein_g NUMERIC(6,2),
    carbs_g NUMERIC(6,2),
    fat_g NUMERIC(6,2),
    fiber_g NUMERIC(6,2),
    sugar_g NUMERIC(6,2),
    sodium_mg NUMERIC(7,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create indexes for efficient queries
CREATE INDEX idx_custom_foods_user_id ON public.custom_foods(user_id);
CREATE INDEX idx_custom_foods_name ON public.custom_foods(name);

-- Enable Row Level Security
ALTER TABLE public.custom_foods ENABLE ROW LEVEL SECURITY;

-- RLS Policy: Users can only view their own custom foods
CREATE POLICY "Users can view own custom foods"
    ON public.custom_foods
    FOR SELECT
    USING (auth.uid() = user_id);

-- RLS Policy: Users can insert their own custom foods
CREATE POLICY "Users can insert own custom foods"
    ON public.custom_foods
    FOR INSERT
    WITH CHECK (auth.uid() = user_id);

-- RLS Policy: Users can update their own custom foods
CREATE POLICY "Users can update own custom foods"
    ON public.custom_foods
    FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- RLS Policy: Users can delete their own custom foods
CREATE POLICY "Users can delete own custom foods"
    ON public.custom_foods
    FOR DELETE
    USING (auth.uid() = user_id);

-- Create trigger to update updated_at on custom_foods
CREATE TRIGGER set_custom_foods_updated_at
    BEFORE UPDATE ON public.custom_foods
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_updated_at();
