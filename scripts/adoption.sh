ORG="guardian"

# 1. Fetch ALL results matching the filename 'devenv.yaml' within the org
RESPONSE=$(gh api "search/code?q=org:${ORG}+filename:devenv.yaml&per_page=100")

# 2. Use jq to filter for files specifically inside a '.devcontainer' folder
FILTERED_JSON=$(echo "$RESPONSE" | jq '[.items[] | select(.path == ".devcontainer/devenv.yaml")]')

# 3. Print the total matching count
COUNT=$(echo "$FILTERED_JSON" | jq '. | length')
echo "========================================"
echo "Total Repositories/Files Found: $COUNT"
echo "========================================"

# 4. Print the detailed list
echo "$FILTERED_JSON" | jq -r '.[] | 
  "Repo: \(.repository.full_name)\nPath: \(.path)\nURL:  \(.html_url)\n----------------------------------------"'

