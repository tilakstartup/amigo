# Amigo - AI-First Health Coaching Application

Amigo is an AI-powered personal health coaching application that helps users track their health journey through intelligent meal logging, water tracking, fasting management, and personalized coaching powered by Claude AI.

## Project Structure

```
amigo/
├── mobile/
│   ├── ios/                    # iOS app (Swift/SwiftUI)
│   ├── android/                # Android app (Kotlin/Compose)
│   └── shared/                 # Kotlin Multiplatform shared module
├── supabase/                   # Supabase configuration
│   ├── migrations/            # Database migrations
│   ├── functions/             # Edge functions
│   └── config.toml            # Supabase config
├── infrastructure/             # AWS CloudFormation templates
│   └── bedrock.yaml           # AWS Bedrock configuration
├── docs/                       # Documentation
│   ├── infrastructure/        # Infrastructure setup guides
│   ├── api/                   # API documentation
│   ├── deployment/            # Deployment guides
│   └── user/                  # User documentation
└── task_summaries/            # Task completion summaries
```

## Tech Stack

- **Mobile**: Kotlin Multiplatform (shared logic), Swift (iOS UI), Kotlin/Compose (Android UI)
- **Backend**: Supabase (Auth, Database, Storage, Realtime)
- **AI**: Amazon Bedrock with Claude AI
- **Infrastructure**: AWS CloudFormation

## Features

- 🍽️ AI-powered meal logging (image, voice, text, barcode)
- 💧 Water intake tracking with reminders
- ⏱️ Intermittent fasting tracker with protocols
- 🤖 Personalized AI health coaching with Amigo
- 📊 Health platform integrations (Fitbit, Garmin, Apple Health, Google Health Connect)
- 🎯 Goal-based personalization (weight loss, muscle gain, maintenance, energy, sleep)
- 📈 Nutritional data from USDA FoodData Central
- 💎 Two-tier subscription model (Free, Pro)

## Getting Started

### Quick Setup

1. **Clone the repository**
2. **Set up Supabase** - See [Supabase Setup](docs/infrastructure/supabase-setup.md)
3. **Configure SendGrid SMTP** - See [SENDGRID-SETUP.md](SENDGRID-SETUP.md) for email authentication
4. **Set up AWS Bedrock** - See [AWS Bedrock Setup](docs/infrastructure/aws-bedrock.md)
5. **Build and run** - See [implementation plan](.kiro/specs/health-app/tasks.md)

### Email Configuration

For authentication emails, we use SendGrid (free tier: 100 emails/day):
- Quick setup: [SENDGRID-SETUP.md](SENDGRID-SETUP.md)
- Full guide: [docs/infrastructure/sendgrid-smtp-setup.md](docs/infrastructure/sendgrid-smtp-setup.md)

## Documentation

- [Requirements](.kiro/specs/health-app/requirements.md)
- [Design Document](.kiro/specs/health-app/design.md)
- [Implementation Tasks](.kiro/specs/health-app/tasks.md)

## License

Proprietary - All rights reserved


## get device id

```
xcrun simctl list devices | grep "iPhone 16 Pro"
```

## run app and see logs


```
APP_PATH=$(find ~/Library/Developer/Xcode/DerivedData -path "*/Debug-iphonesimulator/Amigo.app" -type d 2>/dev/null | head -1) && xcrun simctl install B82E40CD-DF97-4C0C-A2B6-AEFB0B3C4DEF "$APP_PATH" && xcrun simctl launch --console B82E40CD-DF97-4C0C-A2B6-AEFB0B3C4DEF com.amigoai.ios 2>&1



 xcrun simctl spawn booted log stream --predicate 'process == "Amigo"' --level debug --st
yle compact 2>&1 | grep -E "(ProfileManager|GoalManagement|Logger)" --line-buffered
```