#!/bin/bash
# Build script: compiles native image with Gradle and optionally compresses with UPX
# Usage: ./build.sh [--no-upx]

set -euo pipefail

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default: use UPX
USE_UPX=true

# Help function
show_help() {
  cat <<EOF
Usage: $0 [OPTIONS]

Options:
  --no-upx    Skip UPX compression (compression is enabled by default)
  --help      Show this help message

Description:
  Builds a native executable using Gradle (nativeCompile) and optionally
  compresses it with UPX to reduce file size.
EOF
  exit 0
}

# Parse arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
  --no-upx)
    USE_UPX=false
    shift
    ;;
  --help)
    show_help
    ;;
  *)
    echo -e "${RED}Unknown option: $1${NC}"
    show_help
    ;;
  esac
done

echo -e "${GREEN}🔨 Building native image via Gradle...${NC}"

GRADLEW="./gradlew"

# Verify gradlew exists (script must be run from project root)
if [ ! -f "$GRADLEW" ]; then
  echo -e "${RED}❌ gradlew not found in current directory. Run this script from the project root.${NC}"
  exit 1
fi

# Run Gradle build
"$GRADLEW" clean nativeCompile

# (set -e already handles errors, but explicit check improves clarity)
if [ $? -ne 0 ]; then
  echo -e "${RED}❌ Build failed. Aborting.${NC}"
  exit 1
fi

INPUT_FILE="build/native/nativeCompile/jlogfmt"
OUTPUT_FILE="jlogfmt"

# Verify the executable was created
if [ ! -f "$INPUT_FILE" ]; then
  echo -e "${RED}❌ Executable file $INPUT_FILE not found. The build may have failed to produce it.${NC}"
  exit 1
fi

# Handle compression
if [ "$USE_UPX" = true ]; then
  echo -e "${YELLOW}ℹ️  UPX compression is enabled. Note: This may take a while and is optional.${NC}"
  echo -e "${YELLOW}   You can skip it by running the script with --no-upx.${NC}"

  if command -v upx &>/dev/null; then
    echo -e "${GREEN}📦 Compressing with UPX (best)...${NC}"
    upx --best "$INPUT_FILE" -o "$OUTPUT_FILE"

    if [ $? -eq 0 ]; then
      echo -e "${GREEN}✅ Done: $OUTPUT_FILE (compressed)${NC}"
    else
      echo -e "${RED}⚠️  Compression failed. Original file preserved as $INPUT_FILE.${NC}"
      exit 1
    fi
  else
    echo -e "${YELLOW}⚠️  UPX is not installed. Skipping compression.${NC}"
    cp "$INPUT_FILE" "$OUTPUT_FILE"
    echo -e "${GREEN}✅ Executable copied to $OUTPUT_FILE (uncompressed).${NC}"
  fi
else
  echo -e "${YELLOW}ℹ️  UPX compression skipped by user request.${NC}"
  cp "$INPUT_FILE" "$OUTPUT_FILE"
  echo -e "${GREEN}✅ Executable copied to $OUTPUT_FILE (uncompressed).${NC}"
fi
