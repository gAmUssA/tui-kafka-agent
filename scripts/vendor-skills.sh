#!/usr/bin/env bash
# Vendors Agent Skills (SKILL.md bundles) into src/main/resources/skills so the
# langchain4j-skills ClassPathSkillLoader can serve them to the runtime agents.
#
#   ./scripts/vendor-skills.sh            # refresh at the pinned commit
#   ./scripts/vendor-skills.sh <sha>      # pin to another commit of confluentinc/agent-skills
#
# Sources:
#   - confluentinc/agent-skills (Apache-2.0): https://github.com/confluentinc/agent-skills
#   - gamussa/flink-sql Tessl tile: copied from .tessl/plugins/gamussa/flink-sql/skills/flink-sql
#
# evals/ directories are deliberately not vendored (they are test fixtures, and the
# loader would expose them as readable resources).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/src/main/resources/skills"
REPO="confluentinc/agent-skills"
SHA="${1:-914d95eff7ff50513b34bfb9ac97eda7c7fce899}"   # 2026-09-02 "contributing update (#63)"
RAW="https://raw.githubusercontent.com/$REPO/$SHA"

# skill name -> space-separated reference files under references/
declare -A CONFLUENT_SKILLS=(
  ["confluent-cloud-flink-sql"]="cli-reference.md dialect-traps.md formats-and-serialization.md reserved-words.md sql-patterns-cc.md troubleshooting-cc.md"
)

fetch() {  # url dest
  curl -fsSL --connect-timeout 10 --retry 2 --max-time 30 -o "$2" "$1"
}

mkdir -p "$DEST"

for skill in "${!CONFLUENT_SKILLS[@]}"; do
  dir="$DEST/$skill"
  rm -rf "$dir"
  mkdir -p "$dir/references"
  echo "vendoring $skill @ ${SHA:0:7}"
  fetch "$RAW/skills/$skill/SKILL.md" "$dir/SKILL.md"
  for ref in ${CONFLUENT_SKILLS[$skill]}; do
    fetch "$RAW/skills/$skill/references/$ref" "$dir/references/$ref"
  done
  fetch "$RAW/LICENSE" "$DEST/LICENSE-confluent-agent-skills"   # outside the skill dir: the loader exposes every file in it as a resource
done

# Tessl tile skill (already on disk when the tile is installed)
TILE="$ROOT/.tessl/plugins/gamussa/flink-sql/skills/flink-sql"
if [ -d "$TILE" ]; then
  echo "vendoring flink-sql from Tessl tile"
  rm -rf "$DEST/flink-sql"
  cp -R "$TILE" "$DEST/flink-sql"
else
  echo "note: Tessl tile gamussa/flink-sql not installed; keeping existing $DEST/flink-sql" >&2
fi

cat > "$DEST/SOURCES.md" <<EOF
# Vendored Agent Skills

Loaded at runtime by \`langchain4j-skills\` (\`ClassPathSkillLoader.loadSkills("skills")\`) and
exposed to the Flink specialists through \`activate_skill\` / \`read_skill_resource\`.
Refresh with \`./scripts/vendor-skills.sh\`.

| Skill | Source | Pinned at | License |
|---|---|---|---|
| confluent-cloud-flink-sql | https://github.com/$REPO/tree/main/skills/confluent-cloud-flink-sql | \`$SHA\` | Apache-2.0 (LICENSE-confluent-agent-skills) |
| flink-sql | Tessl tile \`gamussa/flink-sql\` | tile version in tessl.json | tile license |

Other skills in confluentinc/agent-skills (kafka-schema-registry, kafka-streams-programming,
developing-kafka-*-client, flink-udf, confluent-cloud-cdc-tableflow, msk-migration) target
developer workflows (project scans, Terraform, client scaffolding), not runtime operations,
so they are not vendored.
EOF

echo "done: $(find "$DEST" -name SKILL.md | wc -l | tr -d ' ') skills in $DEST"
