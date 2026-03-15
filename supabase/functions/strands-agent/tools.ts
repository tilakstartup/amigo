/**
 * Strands tool registrations — thin wrappers over _shared/db.ts and _shared/health.ts.
 * No DB or calculation logic lives here.
 */

import { tool } from '@strands-agents/sdk'
import type { JSONValue } from '@strands-agents/sdk'
import { z } from 'zod'
import type { SupabaseClient } from '@supabase/supabase-js'
import * as db from '../_shared/db.ts'
import * as health from '../_shared/health.ts'

// ─── Tool context ─────────────────────────────────────────────────────────────

export interface ToolContext {
  supabaseClient: SupabaseClient
  serviceClient: SupabaseClient
  userId: string
}

// Serialize any value to a JSON-safe value
function toJson(v: unknown): JSONValue {
  return JSON.parse(JSON.stringify(v)) as JSONValue
}

// ─── Data tools ───────────────────────────────────────────────────────────────

export function makeGetProfileTool(ctx: ToolContext) {
  return tool({
    name: 'get_profile',
    description: 'Fetch the authenticated user profile from the database.',
    inputSchema: z.object({}),
    callback: async () => {
      console.log('[tool] get_profile called')
      try {
        const profile = await db.getProfile(ctx.supabaseClient, ctx.userId)
        const result = !profile ? { status: 'success', message: 'No profile found', id: ctx.userId } : { status: 'success', ...profile }
        console.log('[tool] get_profile result:', JSON.stringify(result))
        return toJson(result)
      } catch (e) {
        console.error('[tool] get_profile error:', (e as Error).message)
        return toJson({ status: 'error', message: (e as Error).message })
      }
    },
  })
}

export function makeSaveOnboardingDataTool(ctx: ToolContext) {
  return tool({
    name: 'save_onboarding_data',
    description: 'Save or update user onboarding fields in the database.',
    inputSchema: z.object({
      payload_json: z.string().describe('JSON string of profile fields to save'),
    }),
    callback: async ({ payload_json }) => {
      console.log('[tool] save_onboarding_data called, payload:', payload_json)
      try {
        const fields = JSON.parse(payload_json)
        await db.saveOnboardingData(ctx.supabaseClient, ctx.userId, fields)
        console.log('[tool] save_onboarding_data success')
        return toJson({ status: 'success', message: 'Onboarding data saved' })
      } catch (e) {
        console.error('[tool] save_onboarding_data error:', (e as Error).message)
        return toJson({ status: 'error', message: (e as Error).message })
      }
    },
  })
}

export function makeGetOnboardingStatusTool(ctx: ToolContext) {
  return tool({
    name: 'get_onboarding_status',
    description: 'Check the user onboarding completion status.',
    inputSchema: z.object({}),
    callback: async () => {
      console.log('[tool] get_onboarding_status called')
      try {
        const result = await db.getOnboardingStatus(ctx.supabaseClient, ctx.userId)
        console.log('[tool] get_onboarding_status result:', JSON.stringify(result))
        return toJson(result)
      } catch (e) {
        console.error('[tool] get_onboarding_status error:', (e as Error).message)
        return toJson({ status: 'error', message: (e as Error).message })
      }
    },
  })
}

// ─── Health calculation tools ─────────────────────────────────────────────────

export function makeCalculateBmrTool() {
  return tool({
    name: 'calculate_bmr',
    description: 'Calculate Basal Metabolic Rate using the Mifflin-St Jeor formula.',
    inputSchema: z.object({
      weight_kg: z.number().describe('Body weight in kilograms'),
      height_cm: z.number().describe('Height in centimetres'),
      age: z.number().describe('Age in years'),
      gender: z.enum(['male', 'female']).describe('Biological sex'),
    }),
    callback: ({ weight_kg, height_cm, age, gender }) => {
      console.log('[tool] calculate_bmr called:', { weight_kg, height_cm, age, gender })
      try {
        const bmr = health.calculateBmr({ weight_kg, height_cm, age, gender })
        console.log('[tool] calculate_bmr result:', bmr)
        return toJson({ status: 'success', bmr, unit: 'kcal/day' })
      } catch (e) {
        console.error('[tool] calculate_bmr error:', (e as Error).message)
        return toJson({ status: 'error', message: (e as Error).message })
      }
    },
  })
}

export function makeCalculateTdeeTool() {
  return tool({
    name: 'calculate_tdee',
    description: 'Calculate Total Daily Energy Expenditure (BMR × activity multiplier).',
    inputSchema: z.object({
      weight_kg: z.number(),
      height_cm: z.number(),
      age: z.number(),
      gender: z.enum(['male', 'female']),
      activity_level: z.enum(['sedentary', 'lightly_active', 'moderately_active', 'very_active', 'extra_active']),
    }),
    callback: (params) => {
      console.log('[tool] calculate_tdee called:', params)
      try {
        const tdee = health.calculateTdee(params)
        console.log('[tool] calculate_tdee result:', tdee)
        return toJson({ status: 'success', tdee, unit: 'kcal/day' })
      } catch (e) {
        console.error('[tool] calculate_tdee error:', (e as Error).message)
        return toJson({ status: 'error', message: (e as Error).message })
      }
    },
  })
}

export function makeCalculateDailyCaloriesTool() {
  return tool({
    name: 'calculate_daily_calories',
    description: 'Calculate daily calorie target for a weight goal.',
    inputSchema: z.object({
      goal_type: z.enum(['weight_loss', 'muscle_gain', 'maintenance']),
      tdee: z.number().describe('Total Daily Energy Expenditure in kcal/day'),
      current_weight_kg: z.number(),
      target_weight_kg: z.number(),
      target_date: z.string().describe('ISO 8601 date string (YYYY-MM-DD)'),
    }),
    callback: (params) => {
      console.log('[tool] calculate_daily_calories called:', params)
      try {
        const result = health.calculateDailyCalories(params)
        console.log('[tool] calculate_daily_calories result:', result)
        return toJson({ status: 'success', ...result })
      } catch (e) {
        console.error('[tool] calculate_daily_calories error:', (e as Error).message)
        return toJson({ status: 'error', message: (e as Error).message })
      }
    },
  })
}

export function makeValidateGoalTool() {
  return tool({
    name: 'validate_goal',
    description: 'Validate a health goal against USDA minimum calorie guidelines.',
    inputSchema: z.object({
      goal_type: z.enum(['weight_loss', 'muscle_gain', 'maintenance']),
      daily_calories: z.number(),
      gender: z.enum(['male', 'female']),
      current_weight_kg: z.number(),
      target_weight_kg: z.number(),
      target_date: z.string(),
      tdee: z.number(),
    }),
    callback: (params) => {
      console.log('[tool] validate_goal called:', params)
      try {
        const result = health.validateGoal(params)
        console.log('[tool] validate_goal result:', result)
        return toJson({ status: 'success', ...result })
      } catch (e) {
        console.error('[tool] validate_goal error:', (e as Error).message)
        return toJson({ status: 'error', message: (e as Error).message })
      }
    },
  })
}

// ─── Goal management tool ─────────────────────────────────────────────────────

export function makeSaveGoalTool(ctx: ToolContext) {
  return tool({
    name: 'save_goal',
    description: 'Save a health goal to the database.',
    inputSchema: z.object({
      goal_type: z.enum(['weight_loss', 'muscle_gain', 'maintenance']),
      current_weight: z.number(),
      target_weight: z.number(),
      target_date: z.string(),
      current_height: z.number().optional(),
      activity_level: z.string().optional(),
      calculated_bmr: z.number().optional(),
      calculated_tdee: z.number().optional(),
      calculated_daily_calories: z.number().optional(),
      user_daily_calories: z.number().optional(),
      is_realistic: z.boolean().optional(),
      validation_reason: z.string().optional(),
      user_overridden: z.boolean().optional(),
    }),
    callback: async (params) => {
      console.log('[tool] save_goal called:', JSON.stringify(params))
      try {
        if (!ctx.userId) return toJson({ status: 'error', message: 'Authentication required for goal management' })
        if (params.user_overridden && !params.user_daily_calories) {
          return toJson({ status: 'error', message: 'user_daily_calories is required when user_overridden=true' })
        }
        await db.saveGoal(ctx.serviceClient, ctx.userId, params)
        console.log('[tool] save_goal success')
        return toJson({ status: 'success', message: 'Goal saved successfully' })
      } catch (e) {
        console.error('[tool] save_goal error:', (e as Error).message)
        return toJson({ status: 'error', message: (e as Error).message })
      }
    },
  })
}

// ─── Build all tools for a request ───────────────────────────────────────────

export function buildTools(ctx: ToolContext) {
  return [
    makeGetProfileTool(ctx),
    makeSaveOnboardingDataTool(ctx),
    makeGetOnboardingStatusTool(ctx),
    makeCalculateBmrTool(),
    makeCalculateTdeeTool(),
    makeCalculateDailyCaloriesTool(),
    makeValidateGoalTool(),
    makeSaveGoalTool(ctx),
  ]
}
