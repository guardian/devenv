#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test counters
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

# Get the project root (parent of generation-tests directory)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Use DEVENV_BIN if set (for CI), otherwise use local staged binary
if [ -n "$DEVENV_BIN" ]; then
    BINARY="$DEVENV_BIN"
else
    BINARY="$PROJECT_ROOT/cli/target/universal/stage/bin/devenv"
fi

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  devenv generation test suite${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Function to print test status
pass() {
    echo -e "${GREEN}✓${NC} $1"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

fail() {
    echo -e "${RED}✗${NC} $1"
    echo -e "${RED}  $2${NC}"
    TESTS_FAILED=$((TESTS_FAILED + 1))
}

info() {
    echo -e "${BLUE}→${NC} $1"
}

# Function to run a test
run_test() {
    TESTS_RUN=$((TESTS_RUN + 1))
    local test_name="$1"
    echo ""
    echo -e "${YELLOW}Test: ${test_name}${NC}"
}

# Build the project if using local binary
if [ -z "$DEVENV_BIN" ]; then
    echo -e "${BLUE}Building project...${NC}"
    cd "$PROJECT_ROOT"
    if sbt "cli/stage" > /dev/null 2>&1; then
        pass "Project built successfully"
    else
        echo -e "${RED}Failed to build project${NC}"
        exit 1
    fi
else
    info "Using provided binary: $BINARY"
fi

# Verify binary exists
if [ ! -f "$BINARY" ]; then
    echo -e "${RED}Binary not found at: $BINARY${NC}"
    exit 1
fi
pass "Binary found at: $BINARY"
echo ""

# ============================================
# Test 1: Basic Init
# ============================================
run_test "Basic Init"

TEMP_DIR=$(mktemp -d)
cd "$TEMP_DIR"

info "Running: devenv init"
if "$BINARY" init > /dev/null 2>&1; then
    pass "Init command succeeded"
else
    fail "Init command failed" "Exit code: $?"
fi

# Check directory structure
if [ -d ".devcontainer" ]; then
    pass "Created .devcontainer directory"
else
    fail "Missing .devcontainer directory" ""
fi

if [ -d ".devcontainer/user" ]; then
    pass "Created .devcontainer/user directory"
else
    fail "Missing .devcontainer/user directory" ""
fi

if [ -d ".devcontainer/shared" ]; then
    pass "Created .devcontainer/shared directory"
else
    fail "Missing .devcontainer/shared directory" ""
fi

if [ -f ".devcontainer/devenv.yaml" ]; then
    pass "Created devenv.yaml"
    if grep -q "CHANGE_ME" ".devcontainer/devenv.yaml"; then
        pass "devenv.yaml contains CHANGE_ME placeholder"
    else
        fail "devenv.yaml missing CHANGE_ME placeholder" ""
    fi
else
    fail "Missing devenv.yaml" ""
fi

if [ -f ".devcontainer/.gitignore" ]; then
    pass "Created .gitignore"
    if grep -q "user/" ".devcontainer/.gitignore"; then
        pass ".gitignore contains user/ exclusion"
    else
        fail ".gitignore missing user/ exclusion" ""
    fi
else
    fail "Missing .gitignore" ""
fi

# Cleanup
cd /
rm -rf "$TEMP_DIR"

# ============================================
# Test 2: Generate with Modules
# ============================================
run_test "Generate with Modules"

TEMP_DIR=$(mktemp -d)
cp -r "$SCRIPT_DIR/generate-with-modules/.devcontainer" "$TEMP_DIR/"
cd "$TEMP_DIR"

info "Running: devenv generate"
if "$BINARY" generate > generate.log 2>&1; then
    pass "Generate command succeeded"
else
    cat generate.log
    fail "Generate command failed" "Exit code: $?"
fi

# Check generated files
if [ -f ".devcontainer/user/devcontainer.json" ]; then
    pass "Generated user/devcontainer.json"

    # Validate JSON
    if jq empty ".devcontainer/user/devcontainer.json" 2>/dev/null; then
        pass "user/devcontainer.json is valid JSON"
    else
        fail "user/devcontainer.json is invalid JSON" ""
    fi

    # Check for module contributions
    # The mise module embeds its setup script as a base64-encoded blob, so we check for the
    # VS Code extension it contributes (hverlin.mise-vscode) rather than the script contents.
    if grep -q "hverlin.mise-vscode" ".devcontainer/user/devcontainer.json"; then
        pass "mise module included in user config"
    else
        fail "mise module missing from user config" ""
    fi

    # Check for project content
    if grep -q "Generation Test Project" ".devcontainer/user/devcontainer.json"; then
        pass "Project name present in user config"
    else
        fail "Project name missing from user config" ""
    fi
else
    fail "Missing user/devcontainer.json" ""
fi

if [ -f ".devcontainer/shared/devcontainer.json" ]; then
    pass "Generated shared/devcontainer.json"

    # Validate JSON
    if jq empty ".devcontainer/shared/devcontainer.json" 2>/dev/null; then
        pass "shared/devcontainer.json is valid JSON"
    else
        fail "shared/devcontainer.json is invalid JSON" ""
    fi
else
    fail "Missing shared/devcontainer.json" ""
fi

# Cleanup
cd /
rm -rf "$TEMP_DIR"

# ============================================
# Test 3: Generate with User Config
# ============================================
run_test "Generate with User Config"

# NOTE: This test scenario is currently limited because Java's System.getProperty("user.home")
# doesn't respect the HOME environment variable. To fully test user config merging in E2E,
# we would need to either:
#   1. Modify Main.scala to use System.getenv("HOME") instead
#   2. Pass -Duser.home to the JVM
#   3. Actually write to the real user's ~/.config/devenv/devenv.yaml
# For now, we just verify that generate works with the project config alone.

TEMP_DIR=$(mktemp -d)
cp -r "$SCRIPT_DIR/generate-with-user-config/project/." "$TEMP_DIR/"
cd "$TEMP_DIR"

info "Running: devenv generate"
if "$BINARY" generate > /dev/null 2>&1; then
    pass "Generate command succeeded"
else
    fail "Generate command failed" "Exit code: $?"
fi

# Check that files were generated
if [ -f ".devcontainer/user/devcontainer.json" ]; then
    pass "Generated user/devcontainer.json"

    # Check for project plugins
    if grep -q "dbaeumer.vscode-eslint" ".devcontainer/user/devcontainer.json"; then
        pass "Project plugin present in user config"
    else
        fail "Project plugin missing from user config" ""
    fi
else
    fail "Missing user/devcontainer.json" ""
fi

if [ -f ".devcontainer/shared/devcontainer.json" ]; then
    pass "Generated shared/devcontainer.json"

    # Validate JSON
    if jq empty ".devcontainer/shared/devcontainer.json" 2>/dev/null; then
        pass "shared/devcontainer.json is valid JSON"
    else
        fail "shared/devcontainer.json is invalid JSON" ""
    fi

    # Check that shared has project content
    if grep -q "dbaeumer.vscode-eslint" ".devcontainer/shared/devcontainer.json"; then
        pass "Project plugin present in shared config"
    else
        fail "Project plugin missing from shared config" ""
    fi
else
    fail "Missing shared/devcontainer.json" ""
fi

# Cleanup
cd /
rm -rf "$TEMP_DIR"

# ============================================
# Test 4: Check Command
# ============================================
run_test "Check Command - Not Initialized"

TEMP_DIR=$(mktemp -d)
cd "$TEMP_DIR"

info "Running: devenv check (on uninitialized project)"
if "$BINARY" check > /dev/null 2>&1; then
    fail "Check should fail on uninitialized project" "Exit code: 0"
else
    pass "Check command failed as expected on uninitialized project"
fi

# Cleanup
cd /
rm -rf "$TEMP_DIR"

# ============================================
run_test "Check Command - Files Match"

TEMP_DIR=$(mktemp -d)
cp -r "$SCRIPT_DIR/generate-with-modules/.devcontainer" "$TEMP_DIR/"
cd "$TEMP_DIR"

info "Generating files first"
if "$BINARY" generate > /dev/null 2>&1; then
    pass "Generate command succeeded"
else
    fail "Generate command failed" "Exit code: $?"
fi

info "Running: devenv check (on freshly generated files)"
if "$BINARY" check > /dev/null 2>&1; then
    pass "Check command succeeded when files match"
else
    fail "Check command failed on matching files" "Exit code: $?"
fi

# Cleanup
cd /
rm -rf "$TEMP_DIR"

# ============================================
run_test "Check Command - Files Mismatch After Config Change"

TEMP_DIR=$(mktemp -d)
cp -r "$SCRIPT_DIR/generate-with-modules/.devcontainer" "$TEMP_DIR/"
cd "$TEMP_DIR"

info "Generating initial files"
if "$BINARY" generate > /dev/null 2>&1; then
    pass "Generate command succeeded"
else
    fail "Generate command failed" "Exit code: $?"
fi

info "Modifying devenv.yaml"
# Change the project name in the config
sed -i.bak 's/Generation Test Project/Modified Project Name/g' .devcontainer/devenv.yaml
if grep -q "Modified Project Name" .devcontainer/devenv.yaml; then
    pass "Config modified successfully"
else
    fail "Failed to modify config" ""
fi

info "Running: devenv check (after config change)"
if "$BINARY" check > /dev/null 2>&1; then
    fail "Check should fail when files don't match config" "Exit code: 0"
else
    pass "Check command failed as expected when files mismatch"
fi

# Verify regenerate fixes the mismatch
info "Regenerating files"
if "$BINARY" generate > /dev/null 2>&1; then
    pass "Regenerate succeeded"

    info "Running: devenv check (after regenerate)"
    if "$BINARY" check > /dev/null 2>&1; then
        pass "Check succeeded after regenerate"
    else
        fail "Check failed after regenerate" "Exit code: $?"
    fi
else
    fail "Regenerate failed" "Exit code: $?"
fi

# Cleanup
cd /
rm -rf "$TEMP_DIR"

# ============================================
# Test Summary
# ============================================
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Test Summary${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "Tests run:    ${TESTS_RUN}"
echo -e "Tests passed: ${GREEN}${TESTS_PASSED}${NC}"
echo -e "Tests failed: ${RED}${TESTS_FAILED}${NC}"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}Some tests failed.${NC}"
    exit 1
fi
