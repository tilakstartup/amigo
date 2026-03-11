-- Add user_overridden column to health_goals table
ALTER TABLE public.health_goals
ADD COLUMN IF NOT EXISTS user_overridden BOOLEAN DEFAULT FALSE;

-- Add comment to explain the field
COMMENT ON COLUMN public.health_goals.user_overridden IS 'Whether user chose to override safety recommendations';
