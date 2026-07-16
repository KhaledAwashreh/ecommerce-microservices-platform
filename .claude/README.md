# `.claude/`

Claude Code configuration and knowledge base for this repository.

```
.claude/
├── README.md          this file
├── settings.json      shared project settings (permissions, env)
├── ai_docs/           durable per-module and cross-cutting reference docs
├── agents/            project-specific subagent definitions
└── skills/            project-specific skills (repeatable workflows)
```

## ai_docs

One document per Maven module, plus cross-cutting documents. These are the primary
reference for anyone (human or agent) working in a module: responsibilities, package
layout, data model, endpoints, dependencies, and gotchas.

Read the relevant doc before editing a module. Update it in the same change when you
alter a module's public surface — endpoints, DTOs, entities, or config keys.

## agents

Subagent definitions in Markdown with YAML frontmatter (`name`, `description`, `tools`,
`model`). Invoked through the Agent tool by name.

## skills

Repeatable workflows, one directory per skill containing a `SKILL.md` with frontmatter
(`name`, `description`). Invoked with the Skill tool or `/<name>`.

## settings.json

Project-scoped settings committed to the repo. Personal overrides belong in
`settings.local.json`, which is gitignored.
