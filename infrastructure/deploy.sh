#!/bin/bash
set -e

ENVIRONMENT=${1:-dev}

echo "🚀 Deploying Amigo Infrastructure to $ENVIRONMENT"

if [ ! -f ".env" ]; then
    echo "❌ Error: .env file not found"
    echo "   Copy .env.example to .env and fill in your values"
    exit 1
fi

if [ ! -d "node_modules" ]; then
    echo "📦 Installing dependencies..."
    npm install
fi

echo "🔨 Building..."
npm run build

echo "☁️  Deploying..."
npx cdk deploy --all --context environment=$ENVIRONMENT --require-approval never

echo "✅ Deployment complete!"
