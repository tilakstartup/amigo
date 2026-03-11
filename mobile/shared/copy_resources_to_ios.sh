#!/bin/bash

# Script to copy resources from commonMain to iOS app bundle
# This should be run as a build phase in Xcode

set -e

# Source and destination paths
RESOURCES_SRC="${SRCROOT}/../shared/src/commonMain/resources"
RESOURCES_DEST="${BUILT_PRODUCTS_DIR}/${PRODUCT_NAME}.app"

echo "📦 Copying resources from ${RESOURCES_SRC} to ${RESOURCES_DEST}"

# Create session-configs directory in app bundle
mkdir -p "${RESOURCES_DEST}/session-configs"

# Copy all YAML files
cp -v "${RESOURCES_SRC}/session-configs/"*.yaml "${RESOURCES_DEST}/session-configs/" || true

echo "✅ Resources copied successfully"
