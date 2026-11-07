# ShaderX agent instructions

Follow the repository's `AGENTS.md` for every task. Read `CLAUDE.md` for the
architecture and `docs/contributing.md` for commands.

Every codebase change requires updates to both `README.md` and the affected
Markdown guides in `docs/` in the same PR, including tests, internal refactors,
dependencies, scripts, tooling, and workflows. Also update public KDoc when APIs
change. Use the change-to-guide table in `AGENTS.md`; there is no no-impact opt-out.
Preserve the README banner and all four inline sample recordings. Run the
documentation checks listed in `AGENTS.md` before finishing. Documentation is
deployed only after a PR merges into `main` and the merged site passes validation.
