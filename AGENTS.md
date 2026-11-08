# Working on ShaderX

ShaderX is a Kotlin Multiplatform shader library. Read `CLAUDE.md` for the
architecture and `docs/contributing.md` for setup and commands. Use JDK 21.
Keep unrelated user edits intact and work on a feature branch.

## Documentation is part of the change

Every codebase change must update **both `README.md` and the affected Markdown
guide(s) in `docs/` in the same pull request**. This includes library and sample
code, tests, build configuration, dependencies, scripts, tooling, and workflows.
Do this before considering a code task complete; do not leave it as a follow-up.
Keep README changes concise and put detailed explanations in the guides. For
internal changes, document the relevant architecture, contributor workflow, or
validation coverage without implying a public API change. Do not add filler.

| Change | Documentation to review and update |
| --- | --- |
| Add/remove an effect, parameters, defaults or ranges | `docs/effects.md`, public KDoc, README effect list, `CHANGELOG.md` |
| Compose modifiers, animation or resource lifetime | `docs/compose.md`, `docs/getting-started.md`, public KDoc |
| Shader interfaces, uniforms or custom effects | `docs/custom-effects.md`, `docs/effects.md`, public KDoc |
| Android/Skia behavior or target support | `docs/platforms.md`, `docs/troubleshooting.md` |
| Image processing | `docs/image-processing.md`, `docs/platforms.md` |
| Toolchain, dependencies or setup | `docs/getting-started.md`, `docs/contributing.md` |
| Sample behavior or run commands | `docs/samples.md`, README showcase |
| Internal refactors, tests, scripts or developer tooling | `docs/contributing.md` or the affected architecture/maintenance guide, README contribution section |
| Release/version | README install snippet, `docs/getting-started.md`, `docs/llms.txt`, effect availability labels, `CHANGELOG.md` |
| Docs build, publishing or agent instructions | `docs/maintaining.md`, `docs/contributing.md` |

Read the implementation and tests before describing behavior. Check each code
example's imports, signatures and platform restrictions. Verify that a version
is published on Maven Central before recommending it in an install command.
The guides use published 0.1.4 as their baseline. Label later unreleased APIs
explicitly, and update version examples only after the release is verified.

Keep the flower banner and **all four inline sample recordings** in the
README. Preserve the visual showcase when shortening or reorganizing prose.
Use the hosted docs for deep dives and relative links within `docs/`. New guides
need a unique front-matter title and description and an entry in `mkdocs.yml`.

## Required documentation checks

Install `requirements-docs.txt` in a virtual environment, then run:

```shell
python scripts/check_docs.py
python -m unittest discover -s scripts/tests -v
zensical build --strict
python scripts/check_site.py
```

Run the relevant Gradle checks for code changes (usually
`./gradlew :shaderx:jvmTest`), and inspect visual changes in a sample app.
For documentation or styles, preview the site on desktop and mobile.

The Documentation workflow validates PRs and publishes to GitHub Pages **only
after a pull request is merged into `main`** and the merged site passes checks.
Feature-branch builds, direct pushes, and closing an unmerged PR do not publish.
Do not merge a branch merely to preview the docs.

The CI check requires both README and guide updates for codebase changes; there
is no PR-body opt-out. Prose-only and media-only changes do not require artificial
companion edits. Review the diff for accuracy: CI can detect missing updates,
but cannot prove the prose describes the implementation correctly.
