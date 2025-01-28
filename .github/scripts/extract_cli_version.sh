#!/bin/bash

echo "Starting CLI version extraction..."

# Find the correct JAR file in Gradle cache (excluding javadoc JARs)
JAR_PATH=$(find ~/.gradle -type f -name "ast-cli-java-wrapper-*.jar" ! -name "*-javadoc.jar" | head -n 1)

if [ -z "$JAR_PATH" ]; then
    echo "Error: ast-cli-java-wrapper JAR not found in Gradle dependencies."
    exit 1
fi

echo "Found JAR at: $JAR_PATH"

# Create a temporary directory to extract the CLI
TEMP_DIR=$(mktemp -d)
echo "Using temporary directory: $TEMP_DIR"

unzip -j "$JAR_PATH" "cx-linux" -d "$TEMP_DIR"
if [ $? -ne 0 ]; then
    echo "Error: Failed to unzip cx-linux from the JAR."
    exit 1
fi

if [ ! -f "$TEMP_DIR/cx-linux" ]; then
    echo "Error: cx-linux not found inside the JAR."
    ls -la "$TEMP_DIR"
    exit 1
fi

chmod +x "$TEMP_DIR/cx-linux"

# Extract the CLI version
CLI_VERSION=$("$TEMP_DIR/cx-linux" version | grep -Eo '^[0-9]+\.[0-9]+\.[0-9]+')

if [ -z "$CLI_VERSION" ]; then
    echo "Error: CLI_VERSION is not set or is empty."
    exit 1
fi

echo "CLI version being packed is $CLI_VERSION"

# Export CLI version as an environment variable
echo "CLI_VERSION=$CLI_VERSION" >> $GITHUB_ENV

echo "CLI version extraction completed successfully."
