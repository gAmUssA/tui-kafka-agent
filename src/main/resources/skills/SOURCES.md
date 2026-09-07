# Vendored Agent Skills

Loaded at runtime by `langchain4j-skills` (`ClassPathSkillLoader.loadSkills("skills")`) and
exposed to the Flink specialists through `activate_skill` / `read_skill_resource`.
Refresh with `./scripts/vendor-skills.sh`.

| Skill | Source | Pinned at | License |
|---|---|---|---|
| confluent-cloud-flink-sql | https://github.com/confluentinc/agent-skills/tree/main/skills/confluent-cloud-flink-sql | `914d95eff7ff50513b34bfb9ac97eda7c7fce899` | Apache-2.0 (see LICENSE in the skill dir) |
| flink-sql | Tessl tile `gamussa/flink-sql` | tile version in tessl.json | tile license |

Other skills in confluentinc/agent-skills (kafka-schema-registry, kafka-streams-programming,
developing-kafka-*-client, flink-udf, confluent-cloud-cdc-tableflow, msk-migration) target
developer workflows (project scans, Terraform, client scaffolding), not runtime operations,
so they are not vendored.
