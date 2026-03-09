-- Add smart goal planning fields to health_goals table
ALTER TABLE public.health_goals
ADD COLUMN IF NOT EXISTS target_weight NUMERIC,
ADD COLUMN IF NOT EXISTS target_date DATE,
ADD COLUMN IF NOT EXISTS current_weight NUMERIC,
ADD COLUMN IF NOT EXISTS current_height NUMERIC,
ADD COLUMN IF NOT EXISTS gender TEXT CHECK (gender IN ('male', 'female')),
ADD COLUMN IF NOT EXISTS activity_level TEXT CHECK (activity_level IN ('sedentary', 'light', 'moderate', 'active', 'very_active')),
ADD COLUMN IF NOT EXISTS calculated_bmr NUMERIC,
ADD COLUMN IF NOT EXISTS calculated_tdee NUMERIC,
ADD COLUMN IF NOT EXISTS calculated_daily_calories NUMERIC,
ADD COLUMN IF NOT EXISTS calculated_bmi_start NUMERIC,
ADD COLUMN IF NOT EXISTS calculated_bmi_target NUMERIC,
ADD COLUMN IF NOT EXISTS weekly_milestones JSONB,
ADD COLUMN IF NOT EXISTS is_realistic BOOLEAN DEFAULT TRUE,
ADD COLUMN IF NOT EXISTS recommended_target_date DATE,
ADD COLUMN IF NOT EXISTS validation_reason TEXT;

-- Add comment to explain the new fields
COMMENT ON COLUMN public.health_goals.target_weight IS 'Target weight for weight loss/gain goals (in kg)';
COMMENT ON COLUMN public.health_goals.target_date IS 'Target completion date for the goal';
COMMENT ON COLUMN public.health_goals.current_weight IS 'Weight at goal creation (in kg)';
COMMENT ON COLUMN public.health_goals.current_height IS 'Height at goal creation (in cm)';
COMMENT ON COLUMN public.health_goals.gender IS 'Gender for BMR calculation (male/female)';
COMMENT ON COLUMN public.health_goals.activity_level IS 'Activity level for TDEE calculation';
COMMENT ON COLUMN public.health_goals.calculated_bmr IS 'Basal Metabolic Rate (calories/day)';
COMMENT ON COLUMN public.health_goals.calculated_tdee IS 'Total Daily Energy Expenditure (calories/day)';
COMMENT ON COLUMN public.health_goals.calculated_daily_calories IS 'Target daily calories to achieve goal';
COMMENT ON COLUMN public.health_goals.calculated_bmi_start IS 'BMI at goal start';
COMMENT ON COLUMN public.health_goals.calculated_bmi_target IS 'BMI at target weight';
COMMENT ON COLUMN public.health_goals.weekly_milestones IS 'Projected weekly progress milestones (JSON array)';
COMMENT ON COLUMN public.health_goals.is_realistic IS 'Whether the target date is realistic and safe';
COMMENT ON COLUMN public.health_goals.recommended_target_date IS 'Alternative target date if original is unrealistic';
COMMENT ON COLUMN public.health_goals.validation_reason IS 'Explanation of why goal is/isn''t realistic';

-- Create index for target_date queries
CREATE INDEX IF NOT EXISTS idx_health_goals_target_date ON public.health_goals(target_date);
