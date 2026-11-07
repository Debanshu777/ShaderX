# Working on ShaderX

ShaderX is a Kotlin Multiplatform shader library. Read `CLAUDE.md` for the
architecture and `docs/contributing.md` for setup and commands. Use JDK 21.
Keep unrelated user edits intact and work on a feature branch.

## Documentation is part of the change

When you change behavior, update the relevant guides **in the same pull request**.
Do this before considering a code task complete; do not leave it as a follow-up.

| Change | Documentation to review and update |
| --- | --- |
| Add/remove an effect, parameters, defaults or ranges | `docs/effects.md`, public KDoc, README effect list, `CHANGELOG.md` |
| Compose modifiers, animation or resource lifetime | `docs/compose.md`, `docs/getting-started.md`, public KDoc |
| Shader interfaces, uniforms or custom effects | `docs/custom-effects.md`, `docs/effects.md`, public KDoc |
| Android/Skia behavior or target support | `docs/platforms.md`, `docs/troubleshooting.md` |
| Image processing | `docs/image-processing.md`, `docs/platforms.md` |
| Toolchain, dependencies or setup | `docs/getting-started.md`, `docs/contributing.md` |
| Sample behavior or run commands | `docs/samples.md`, README showcase |
| Release/version | README install snippet, `docs/getting-started.md`, `docs/llms.txt`, effect availability labels, `CHANGELOG.md` |
| Docs build, publishing or agent instructions | `docs/maintaining.md`, `docs/contributing.md` |

Read the implementation and tests before describing behavior. Check each code
example's imports, signatures and platform restrictions. Verify that a version
is published on Maven Central before recommending it in an install command.
The existing guides distinguish published 0.1.3 from development 0.1.4; update
those labels only when the release is verified.

Keep the sunflower banner and **all four inline sample recordings** in the
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

The Documentation workflow checks PRs and publishes successful `main` builds
to GitHub Pages. Feature branches never deploy through the normal workflow.
Do not merge a branch merely to preview the docs.

If a code change truly has no user-facing documentation impact (for example an
internal refactor), explain it in the PR body using `Docs-Impact: none` and a
specific `Docs-Reason: ...` line. Never use this to skip necessary documentation.
The CI check flags missing documentation updates; it cannot verify prose accuracy.
