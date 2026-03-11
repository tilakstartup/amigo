-- Add user_daily_calories field to health_goals table
ALTER TABLE public.health_goals
ADD COLUMN IF NOT EXISTS user_daily_calories NUMERIC;

-- Add comment to explain the new field
COMMENT ON COLUMN public.health_goals.user_daily_calories IS 'User-specified daily calories when they override the recommended amount';