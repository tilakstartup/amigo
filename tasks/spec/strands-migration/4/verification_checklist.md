# Task 4 Verification Checklist — Health Calculations (`health.ts`)

## Implementation Checks

- [x] `supabase/functions/_shared/health.ts` exists ✅
- [x] `calculateBmr` exported — Mifflin-St Jeor (male: base+5, female: base−161) ✅
- [x] `calculateTdee` exported — BMR × activity multiplier ✅
- [x] `calculateDailyCalories` exported — weightDiff * 7700 / daysUntilTarget ✅
- [x] `validateGoal` exported — USDA minimums (male 1500, female 1200) ✅
- [x] Activity multipliers: sedentary=1.2, lightly_active=1.375, moderately_active=1.55, very_active=1.725, extra_active=1.9 ✅
- [x] `calculateDailyCalories` throws when target date is in the past ✅
- [x] `validateGoal` returns 3 suggestions for invalid weight_loss goal ✅
- [x] No I/O — pure functions only ✅

## Type Correctness

- [x] `deno check` exits 0 with no errors ✅ auto-verified

## Sanity Check

- [x] BMR(70kg, 175cm, 30yo, male) = 1648.75 ✅ auto-verified
- [x] TDEE(same, sedentary) = 1978.5 ✅ auto-verified

## Property Tests (Task 4.1)

- [x] Property 1: `calculateBmr` matches Mifflin-St Jeor formula exactly (100 runs) ✅ auto-verified
- [x] Property 2: `calculateTdee` = BMR × multiplier within 1e-9 (100 runs) ✅ auto-verified
- [x] Property 3: `calculateDailyCalories` deficit = weightDiff × 7700 / days (100 runs) ✅ auto-verified

## Note on tasks.md sanity check

The tasks.md had a typo: expected BMR 1698.75 / TDEE 2038.5. The correct values per Mifflin-St Jeor are BMR 1648.75 / TDEE 1978.5. The implementation is correct.
