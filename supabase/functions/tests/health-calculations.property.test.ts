/**
 * Property-based tests for health calculation functions.
 *
 * Validates: Requirements 7.1, 7.2, 7.3, 7.6, 7.7, 7.8
 */

import * as fc from 'npm:fast-check'
import { calculateBmr, calculateTdee, calculateDailyCalories } from '../_shared/health.ts'
import type { Gender, ActivityLevel } from '../_shared/types.ts'

const ACTIVITY_MULTIPLIERS: Record<ActivityLevel, number> = {
  sedentary: 1.2,
  lightly_active: 1.375,
  moderately_active: 1.55,
  very_active: 1.725,
  extra_active: 1.9,
}

const genderArb = fc.constantFrom<Gender>('male', 'female')
const activityArb = fc.constantFrom<ActivityLevel>(
  'sedentary',
  'lightly_active',
  'moderately_active',
  'very_active',
  'extra_active',
)

/**
 * Property 1: calculateBmr matches Mifflin-St Jeor formula exactly for all valid inputs.
 * Validates: Requirements 7.1, 7.6
 */
Deno.test('Property 1: calculateBmr matches Mifflin-St Jeor formula exactly', () => {
  fc.assert(
    fc.property(
      fc.float({ min: 30, max: 200, noNaN: true }),
      fc.float({ min: 100, max: 250, noNaN: true }),
      fc.integer({ min: 10, max: 100 }),
      genderArb,
      (weight_kg, height_cm, age, gender) => {
        const result = calculateBmr({ weight_kg, height_cm, age, gender })
        const base = (10 * weight_kg) + (6.25 * height_cm) - (5 * age)
        const expected = gender === 'male' ? base + 5 : base - 161
        return result === expected
      },
    ),
    { numRuns: 100 },
  )
})

/**
 * Property 2: calculateTdee equals calculateBmr × activityMultiplier for all valid inputs.
 * Validates: Requirements 7.2, 7.7
 */
Deno.test('Property 2: calculateTdee equals calculateBmr * activityMultiplier', () => {
  fc.assert(
    fc.property(
      fc.float({ min: 30, max: 200, noNaN: true }),
      fc.float({ min: 100, max: 250, noNaN: true }),
      fc.integer({ min: 10, max: 100 }),
      genderArb,
      activityArb,
      (weight_kg, height_cm, age, gender, activity_level) => {
        const params = { weight_kg, height_cm, age, gender, activity_level }
        const tdee = calculateTdee(params)
        const bmr = calculateBmr(params)
        const multiplier = ACTIVITY_MULTIPLIERS[activity_level]
        const expected = bmr * multiplier
        return Math.abs(tdee - expected) < 1e-9
      },
    ),
    { numRuns: 100 },
  )
})

/**
 * Property 3: calculateDailyCalories deficit equals weightDiff * 7700 / days for weight_loss.
 * Validates: Requirements 7.3, 7.8
 */
Deno.test('Property 3: calculateDailyCalories deficit equals weightDiff * 7700 / days', () => {
  fc.assert(
    fc.property(
      fc.float({ min: 1200, max: 3000, noNaN: true }),
      fc.float({ min: 40, max: 150, noNaN: true }),
      fc.integer({ min: 30, max: 365 }),
      (tdee, current_weight_kg, daysFromNow) => {
        // target weight must be less than current for weight_loss
        const target_weight_kg = current_weight_kg - fc.sample(
          fc.float({ min: 0.5, max: Math.min(current_weight_kg - 1, 30), noNaN: true }),
          1,
        )[0]

        if (target_weight_kg <= 0) return true // skip degenerate case

        // Build a future date string
        const futureDate = new Date()
        futureDate.setUTCDate(futureDate.getUTCDate() + daysFromNow)
        const target_date = futureDate.toISOString().slice(0, 10)

        const result = calculateDailyCalories({
          goal_type: 'weight_loss',
          tdee,
          current_weight_kg,
          target_weight_kg,
          target_date,
        })

        const weightDiff = current_weight_kg - target_weight_kg
        const expectedDeficit = (weightDiff * 7700) / result.days_until_target
        const actualDeficit = tdee - result.daily_calories

        return Math.abs(actualDeficit - expectedDeficit) < 1e-9
      },
    ),
    { numRuns: 100 },
  )
})
