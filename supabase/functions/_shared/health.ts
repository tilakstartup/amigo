// Pure health calculation functions — no I/O.
// Matches the Kotlin HealthCalculationsActionGroup exactly.

import type { Gender, ActivityLevel, GoalType } from './types.ts'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface BmrParams {
  weight_kg: number
  height_cm: number
  age: number
  gender: Gender
}

export interface TdeeParams extends BmrParams {
  activity_level: ActivityLevel
}

export interface DailyCaloriesParams {
  goal_type: GoalType
  tdee: number
  current_weight_kg: number
  target_weight_kg: number
  target_date: string // yyyy-MM-dd
}

export interface DailyCaloriesResult {
  daily_calories: number
  tdee: number
  current_weight_kg: number
  target_weight_kg: number
  target_date: string
  days_until_target: number
  weekly_weight_change_kg: number
  goal_type: GoalType
}

export interface ValidateGoalParams {
  goal_type: GoalType
  daily_calories: number
  gender: Gender
  current_weight_kg: number
  target_weight_kg: number
  target_date: string // yyyy-MM-dd
  tdee: number
}

export interface Suggestion {
  type: 'extend_timeline' | 'adjust_target' | 'user_override'
  suggested_date?: string
  suggested_target_weight?: number
  daily_calories: number
  original_target?: number
  original_date?: string
  message: string
}

export interface ValidateGoalResult {
  is_valid: boolean
  message: string
  minimum_calories: number
  suggestions: Suggestion[]
}

// ─── Activity multipliers ─────────────────────────────────────────────────────

const ACTIVITY_MULTIPLIERS: Record<ActivityLevel, number> = {
  sedentary: 1.2,
  lightly_active: 1.375,
  moderately_active: 1.55,
  very_active: 1.725,
  extra_active: 1.9,
}

// ─── USDA minimums ────────────────────────────────────────────────────────────

const USDA_MINIMUMS: Record<Gender, number> = {
  male: 1500,
  female: 1200,
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function pad2(n: number): string {
  return n.toString().padStart(2, '0')
}

/** Returns epoch days (days since 1970-01-01) for a yyyy-MM-dd string. */
function dateStringToEpochDays(dateStr: string): number {
  const [year, month, day] = dateStr.split('-').map(Number)
  // Use UTC midnight to avoid timezone issues
  const ms = Date.UTC(year, month - 1, day)
  return Math.floor(ms / (24 * 60 * 60 * 1000))
}

/** Returns today's epoch days using UTC date. */
function todayEpochDays(): number {
  const now = new Date()
  const ms = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate())
  return Math.floor(ms / (24 * 60 * 60 * 1000))
}

/** Converts epoch days back to a yyyy-MM-dd string. */
function epochDaysToDateString(epochDays: number): string {
  const ms = epochDays * 24 * 60 * 60 * 1000
  const d = new Date(ms)
  return `${d.getUTCFullYear()}-${pad2(d.getUTCMonth() + 1)}-${pad2(d.getUTCDate())}`
}

// ─── Exported pure functions ──────────────────────────────────────────────────

/**
 * Calculate Basal Metabolic Rate using the Mifflin-St Jeor equation.
 * male:   (10*w) + (6.25*h) - (5*age) + 5
 * female: (10*w) + (6.25*h) - (5*age) - 161
 */
export function calculateBmr(params: BmrParams): number {
  const { weight_kg, height_cm, age, gender } = params
  const base = (10 * weight_kg) + (6.25 * height_cm) - (5 * age)
  return gender === 'male' ? base + 5 : base - 161
}

/**
 * Calculate Total Daily Energy Expenditure.
 * TDEE = BMR × activity multiplier
 */
export function calculateTdee(params: TdeeParams): number {
  const bmr = calculateBmr(params)
  const multiplier = ACTIVITY_MULTIPLIERS[params.activity_level]
  return bmr * multiplier
}

/**
 * Calculate the daily calorie target for a given goal.
 * Deficit/surplus = weightDiff * 7700 / daysUntilTarget
 * Throws if target_date is in the past.
 */
export function calculateDailyCalories(params: DailyCaloriesParams): DailyCaloriesResult {
  const { goal_type, tdee, current_weight_kg, target_weight_kg, target_date } = params

  const targetEpochDays = dateStringToEpochDays(target_date)
  const currentEpochDays = todayEpochDays()
  const daysUntilTarget = targetEpochDays - currentEpochDays

  if (daysUntilTarget <= 0) {
    throw new Error('Target date must be in the future')
  }

  const weightDifference = Math.abs(target_weight_kg - current_weight_kg)
  const caloriesPerKg = 7700.0
  const totalCalorieChange = weightDifference * caloriesPerKg
  const dailyCalorieChange = totalCalorieChange / daysUntilTarget

  let daily_calories: number
  if (goal_type === 'weight_loss') {
    daily_calories = tdee - dailyCalorieChange
  } else if (goal_type === 'muscle_gain') {
    daily_calories = tdee + dailyCalorieChange
  } else {
    // maintenance
    daily_calories = tdee
  }

  const weekly_weight_change_kg = (weightDifference / daysUntilTarget) * 7.0

  return {
    daily_calories,
    tdee,
    current_weight_kg,
    target_weight_kg,
    target_date,
    days_until_target: daysUntilTarget,
    weekly_weight_change_kg,
    goal_type,
  }
}

/**
 * Validate whether a goal is safe per USDA minimums.
 * Returns { is_valid, message, minimum_calories, suggestions }.
 */
export function validateGoal(params: ValidateGoalParams): ValidateGoalResult {
  const {
    goal_type,
    daily_calories,
    gender,
    current_weight_kg,
    target_weight_kg,
    target_date,
    tdee,
  } = params

  const minimumCalories = USDA_MINIMUMS[gender]

  const isValid = goal_type === 'weight_loss'
    ? daily_calories >= minimumCalories
    : goal_type === 'muscle_gain'
    ? daily_calories >= minimumCalories && daily_calories <= tdee + 500
    : daily_calories >= minimumCalories // maintenance

  const suggestions: Suggestion[] = []

  if (!isValid) {
    const weightDifference = Math.abs(target_weight_kg - current_weight_kg)
    const caloriesPerKg = 7700.0

    const targetEpochDays = dateStringToEpochDays(target_date)
    const currentEpochDays = todayEpochDays()
    const daysUntilTarget = targetEpochDays - currentEpochDays

    if (goal_type === 'weight_loss') {
      // Suggestion 1: extend timeline
      const safeDeficit = tdee - minimumCalories
      const totalCaloriesNeeded = weightDifference * caloriesPerKg
      const safeDays = Math.floor(totalCaloriesNeeded / safeDeficit)
      const safeDateStr = epochDaysToDateString(currentEpochDays + safeDays)

      suggestions.push({
        type: 'extend_timeline',
        suggested_date: safeDateStr,
        daily_calories: minimumCalories,
        message: `Extend your goal to ${safeDateStr} to safely reach ${target_weight_kg} kg at ${Math.floor(minimumCalories)} kcal/day`,
      })

      // Suggestion 2: adjust target weight
      const maxWeightLoss = (safeDeficit * daysUntilTarget) / caloriesPerKg
      const safeTargetWeight = current_weight_kg - maxWeightLoss

      suggestions.push({
        type: 'adjust_target',
        suggested_target_weight: safeTargetWeight,
        daily_calories: minimumCalories,
        message: `Adjust your target to ${safeTargetWeight.toFixed(1)} kg by ${target_date} at ${Math.floor(minimumCalories)} kcal/day`,
      })

      // Suggestion 3: user override
      suggestions.push({
        type: 'user_override',
        daily_calories: minimumCalories,
        original_target: target_weight_kg,
        original_date: target_date,
        message: `Set goal as requested with minimum safe calories (${Math.floor(minimumCalories)} kcal/day). Progress may be slower than planned.`,
      })
    }
  }

  let message: string
  if (isValid) {
    message = 'Goal is realistic and achievable'
  } else if (goal_type === 'weight_loss') {
    message = `This goal requires ${Math.floor(daily_calories)} kcal/day, which is below the USDA minimum of ${Math.floor(minimumCalories)} kcal/day for ${gender}s`
  } else if (goal_type === 'muscle_gain') {
    message = daily_calories < minimumCalories
      ? 'Daily calories are below minimum safe intake'
      : 'Calorie surplus is too high (max 500 kcal/day recommended)'
  } else {
    message = 'Daily calories are below minimum safe intake'
  }

  return {
    is_valid: isValid,
    message,
    minimum_calories: minimumCalories,
    suggestions,
  }
}
