# Product Overview

Amigo is an AI-powered personal health coaching application that helps users track their health journey through intelligent meal logging, water tracking, fasting management, and personalized coaching powered by Claude AI via Amazon Bedrock.

## Core Features

- AI-powered meal logging (image, voice, text, barcode)
- Water intake tracking with reminders
- Intermittent fasting tracker with protocols
- Personalized AI health coaching conversations
- Health platform integrations (Fitbit, Garmin, Apple Health, Google Health Connect)
- Goal-based personalization (weight loss, muscle gain, maintenance, energy, sleep)
- Nutritional data from USDA FoodData Central
- Two-tier subscription model (Free, Pro)

## Architecture

- Mobile apps (iOS/Android) with shared business logic
- Supabase backend (Auth, Database, Storage, Realtime)
- AWS Bedrock for AI capabilities (Claude)
- AWS CDK for infrastructure as code

## Key User Flows

1. Onboarding: Conversational AI-driven profile setup and goal selection
2. Meal Logging: Multi-modal input (camera, voice, text, barcode) with AI parsing
3. AI Coaching: Context-aware conversations with Amigo agent
4. Goal Management: Smart goal planning with AI-calculated recommendations
5. Health Tracking: Water, fasting, and health metrics monitoring
