import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

type ActivityLevel = 'sedentary' | 'lightly_active' | 'moderately_active' | 'very_active' | 'extremely_active'
type GoalType = 'lose_weight' | 'maintain_weight' | 'gain_weight'

interface HealthCalculationRequest {
  operation?: string
  weight_kg?: number
  height_cm?: number
  age?: number
  gender?: string
  activity_level?: string
  goal_type?: string
  daily_calories?: number
  protein_percent?: number
  carbs_percent?: number
  fat_percent?: number
}

interface ErrorResult {
  error: string
}

interface BmrResult {
  bmr: number
  formula: string
  inputs: {
    weight_kg: number
    height_cm: number
    age: number
    gender: string | null
  }
}

interface TdeeResult {
  bmr: number
  activity_level: ActivityLevel
  activity_multiplier: number
  tdee: number
}

interface ValidateGoalResult {
  goal_type: GoalType
  tdee: number
  daily_calories: number
  calorie_delta_vs_tdee: number
  is_valid: boolean
  recommendation: string
}

interface MacroResult {
  daily_calories: number
  split_percent: {
    protein: number
    carbs: number
    fat: number
  }
  grams: {
    protein: number
    carbs: number
    fat: number
  }
}

const JSON_HEADERS = { 'Content-Type': 'application/json' }

function round2(value: number): number {
  return Math.round(value * 100) / 100
}

function normalizeActivityLevel(value?: string): ActivityLevel {
  const normalized = (value || '').trim().toLowerCase()
  const map: Record<string, ActivityLevel> = {
    sedentary: 'sedentary',
    light: 'lightly_active',
    lightly_active: 'lightly_active',
    moderate: 'moderately_active',
    moderately_active: 'moderately_active',
    active: 'very_active',
    very_active: 'very_active',
    extremely_active: 'extremely_active'
  }
  return map[normalized] || 'moderately_active'
}

function activityMultiplier(level: ActivityLevel): number {
  const multipliers: Record<ActivityLevel, number> = {
    sedentary: 1.2,
    lightly_active: 1.375,
    moderately_active: 1.55,
    very_active: 1.725,
    extremely_active: 1.9
  }
  return multipliers[level]
}

function normalizeGoalType(value?: string): GoalType {
  const normalized = (value || '').trim().toLowerCase()
  const map: Record<string, GoalType> = {
    lose: 'lose_weight',
    lose_weight: 'lose_weight',
    fat_loss: 'lose_weight',
    maintain: 'maintain_weight',
    maintain_weight: 'maintain_weight',
    gain: 'gain_weight',
    gain_weight: 'gain_weight',
    bulk: 'gain_weight'
  }
  return map[normalized] || 'maintain_weight'
}

function calculateBmr(input: HealthCalculationRequest): BmrResult | ErrorResult {
  const weight = Number(input.weight_kg)
  const height = Number(input.height_cm)
  const age = Number(input.age)

  if (!Number.isFinite(weight) || !Number.isFinite(height) || !Number.isFinite(age)) {
    return { error: 'weight_kg, height_cm, and age are required numeric values' }
  }

  const gender = (input.gender || '').trim().toLowerCase()
  const base = (10 * weight) + (6.25 * height) - (5 * age)

  let bmr = base
  if (gender === 'male' || gender === 'm') {
    bmr = base + 5
  } else if (gender === 'female' || gender === 'f') {
    bmr = base - 161
  }

  return {
    bmr: round2(bmr),
    formula: 'Mifflin-St Jeor',
    inputs: { weight_kg: weight, height_cm: height, age, gender: input.gender || null }
  }
}

function calculateTdee(input: HealthCalculationRequest): TdeeResult | ErrorResult {
  const bmrResult = calculateBmr(input)
  if ('error' in bmrResult) return bmrResult

  const level = normalizeActivityLevel(input.activity_level)
  const multiplier = activityMultiplier(level)
  const tdee = bmrResult.bmr * multiplier

  return {
    bmr: bmrResult.bmr,
    activity_level: level,
    activity_multiplier: multiplier,
    tdee: round2(tdee)
  }
}

function validateGoal(input: HealthCalculationRequest): ValidateGoalResult | ErrorResult {
  const tdeeResult = calculateTdee(input)
  if ('error' in tdeeResult) return tdeeResult

  const goalType = normalizeGoalType(input.goal_type)
  const targetCalories = Number(input.daily_calories)

  if (!Number.isFinite(targetCalories)) {
    return { error: 'daily_calories is required as numeric value for validate_goal' }
  }

  const delta = round2(targetCalories - tdeeResult.tdee)
  let isValid = true
  let recommendation = 'Target calories look reasonable for your selected goal.'

  if (goalType === 'lose_weight' && delta > -200) {
    isValid = false
    recommendation = 'For fat loss, target calories are usually 200-700 below TDEE.'
  } else if (goalType === 'gain_weight' && delta < 150) {
    isValid = false
    recommendation = 'For muscle gain, target calories are usually 150-500 above TDEE.'
  } else if (goalType === 'maintain_weight' && Math.abs(delta) > 200) {
    isValid = false
    recommendation = 'For maintenance, target calories are usually within ±200 of TDEE.'
  }

  return {
    goal_type: goalType,
    tdee: tdeeResult.tdee,
    daily_calories: round2(targetCalories),
    calorie_delta_vs_tdee: delta,
    is_valid: isValid,
    recommendation
  }
}

function calculateMacros(input: HealthCalculationRequest): MacroResult | ErrorResult {
  const calories = Number(input.daily_calories)
  if (!Number.isFinite(calories)) {
    return { error: 'daily_calories is required as numeric value for calculate_macros' }
  }

  const proteinPercent = Number.isFinite(Number(input.protein_percent)) ? Number(input.protein_percent) : 30
  const carbsPercent = Number.isFinite(Number(input.carbs_percent)) ? Number(input.carbs_percent) : 40
  const fatPercent = Number.isFinite(Number(input.fat_percent)) ? Number(input.fat_percent) : 30

  const total = proteinPercent + carbsPercent + fatPercent
  if (Math.abs(total - 100) > 0.01) {
    return { error: 'protein_percent + carbs_percent + fat_percent must equal 100' }
  }

  const proteinCalories = calories * (proteinPercent / 100)
  const carbsCalories = calories * (carbsPercent / 100)
  const fatCalories = calories * (fatPercent / 100)

  return {
    daily_calories: round2(calories),
    split_percent: {
      protein: proteinPercent,
      carbs: carbsPercent,
      fat: fatPercent
    },
    grams: {
      protein: round2(proteinCalories / 4),
      carbs: round2(carbsCalories / 4),
      fat: round2(fatCalories / 9)
    }
  }
}

serve(async (req: Request) => {
  const requestId = crypto.randomUUID()
  const startedAt = Date.now()
  try {
    console.log('[health-calculations] request.start', JSON.stringify({
      requestId,
      method: req.method
    }))

    if (req.method !== 'POST') {
      console.warn('[health-calculations] method.not_allowed', JSON.stringify({ requestId, method: req.method }))
      return new Response(JSON.stringify({ error: 'Method not allowed' }), { status: 405, headers: JSON_HEADERS })
    }

    const body: HealthCalculationRequest = await req.json()
    const operation = (body.operation || '').trim().toLowerCase()

    console.log('[health-calculations] payload.received', JSON.stringify({
      requestId,
      operation,
      hasWeight: Number.isFinite(Number(body.weight_kg)),
      hasHeight: Number.isFinite(Number(body.height_cm)),
      hasAge: Number.isFinite(Number(body.age)),
      hasCalories: Number.isFinite(Number(body.daily_calories))
    }))

    if (!operation) {
      console.warn('[health-calculations] operation.missing', JSON.stringify({ requestId }))
      return new Response(
        JSON.stringify({ error: 'operation is required. Use one of: calculate_bmr, calculate_tdee, validate_goal, calculate_macros' }),
        { status: 400, headers: JSON_HEADERS }
      )
    }

    let result: BmrResult | TdeeResult | ValidateGoalResult | MacroResult | ErrorResult
    if (operation === 'calculate_bmr') {
      result = calculateBmr(body)
    } else if (operation === 'calculate_tdee') {
      result = calculateTdee(body)
    } else if (operation === 'validate_goal') {
      result = validateGoal(body)
    } else if (operation === 'calculate_macros') {
      result = calculateMacros(body)
    } else {
      console.warn('[health-calculations] operation.unsupported', JSON.stringify({ requestId, operation }))
      return new Response(JSON.stringify({ error: `Unsupported operation: ${operation}` }), { status: 400, headers: JSON_HEADERS })
    }

    if ('error' in result) {
      console.warn('[health-calculations] operation.validation_failed', JSON.stringify({
        requestId,
        operation,
        message: result.error
      }))
      return new Response(JSON.stringify(result), { status: 400, headers: JSON_HEADERS })
    }

    const response = new Response(
      JSON.stringify({
        success: true,
        operation,
        result
      }),
      { status: 200, headers: JSON_HEADERS }
    )

    console.log('[health-calculations] request.end', JSON.stringify({
      requestId,
      operation,
      durationMs: Date.now() - startedAt
    }))

    return response
  } catch (error) {
    console.error('[health-calculations] request.error', JSON.stringify({
      requestId,
      durationMs: Date.now() - startedAt,
      message: error instanceof Error ? error.message : String(error)
    }))
    return new Response(
      JSON.stringify({ error: error instanceof Error ? error.message : 'Unknown error' }),
      { status: 500, headers: JSON_HEADERS }
    )
  }
})
