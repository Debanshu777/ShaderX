---
title: Maintain releases, documentation, and project discovery
description: Publish ShaderX documentation to GitHub Pages, update release-specific examples, and keep repository topics, Maven metadata, and search-facing content accurate.
---

# Maintainer guide

## Documentation publishing

The repository includes a static [Zensical](https://zensical.org/docs/) site using the compatible `mkdocs.yml` configuration format. Each guide produces HTML with a descriptive title, a unique description, a canonical URL, and Open Graph/Twitter metadata. The builder generates `sitemap.xml` and a local search index; the home page also describes the project using `SoftwareSourceCode` structured data.

The documentation URL is [debanshu777.github.io/ShaderX](https://debanshu777.github.io/ShaderX/).

1. Work on a feature branch and open a pull request against `main`.
2. The **Documentation** workflow checks source links, documentation impact, the strict site build, generated metadata, anchors, sitemap, and search. It uploads a `documentation-site` preview artifact without publishing the feature branch.
3. Only when a PR is **merged into `main`**, its `pull_request_target: closed` event rebuilds the merged commit and deploys the validated artifact to **GitHub Pages**. Closing an unmerged PR skips the build and deployment. Direct pushes and manual dispatches do not trigger publication. A failed build cannot deploy.
4. Open the workflow's `github-pages` environment URL to verify the published site. To retry a failed publication, use **Re-run failed jobs** on that merged PR's workflow run. Deployment jobs are serialized so an in-progress publication is not cancelled by another merge.

The repository's **Settings → Pages → Source** must be **GitHub Actions**. This is a one-time hosting setting. The deployment job uses GitHub's short-lived `GITHUB_TOKEN` and OIDC permissions (`pages: write`, `id-token: write`); no personal access token or deployment secret is needed. The workflow never merges feature branches.

PR validation runs with read-only repository permissions and produces only a preview. The merge event checks `merged == true` and the `main` base branch before publishing, including for contributions from forks. It checks out the exact merged commit, never an unmerged PR head. Only the deployment job receives Pages/OIDC write permissions. See GitHub's [merged pull request event documentation](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#running-your-pull_request_target-workflow-when-a-pull-request-merges).

If using a custom domain, update `site_url` and the URLs in `docs/llms.txt`, then rebuild. A project site's `/ShaderX/robots.txt` would not control the host's crawlers: robots rules belong at the host root. The site does not add misleading subdirectory robot rules.

The build does not require analytics, remote fonts, or a paid documentation service. Search engines decide whether and when to index published pages; metadata is not a ranking guarantee.

## Keep documentation current with coding agents

[AGENTS.md](https://github.com/Debanshu777/ShaderX/blob/main/AGENTS.md) gives coding agents a change-to-guide map and requires **both `README.md` and the affected Markdown guides in `docs/` to be updated with every codebase change in the same PR**. `CLAUDE.md` points to those shared rules, and `.github/copilot-instructions.md` applies them to Copilot work. These are repository instructions for agents working on a task, not a background AI service; they need no model API key.

The PR check covers library and sample code, tests, internal refactors, dependencies, configuration, scripts, tooling, styles, and workflows. It treats new file types as code/configuration by default, exempting prose, media, license notices, and documentation text files. Prose-only and media-only changes do not require companion edits. For code changes, README-only or guide-only updates fail, as do deleting the README/guide or updating only a changelog, contributor file, or image. There is no PR-body opt-out.

Keep the README explanation concise and use the guides for details. For an internal change, describe the relevant architecture, contributor process, or test coverage without inventing a public behavior change. Reviewers still need to assess correctness: a changed Markdown file is evidence of an update, not proof of accuracy. Branch protection can require **Documentation / build** if maintainers want to enforce the check as a merge requirement.

`scripts/check_docs.py` also requires the flower banner and all four demo recordings to remain embedded in the README. Site checks verify metadata, social-preview images, and links before any deployment.

## Repository description and topics

Suggested GitHub About description:

> GPU shader effects for Jetpack Compose and Compose Multiplatform. Kotlin library for Android, iOS, desktop, and WebAssembly, with AGSL/SkSL shaders and custom effects.

Relevant topics (within GitHub's 20-topic limit):

```text
kotlin kotlin-multiplatform kotlin-multiplatform-library compose-multiplatform
compose-multiplatform-library jetpack-compose android ios compose-desktop
webassembly shader shaders agsl sksl gpu image-processing animation
```

Use topics that describe implemented capabilities. GLSL is related but is not the library's shader contract; AGSL and SkSL are more precise. Review existing topics before replacing them.

These settings are managed on GitHub, not by the README or Maven POM. Set the repository's **About → Website** to `https://debanshu777.github.io/ShaderX/` so visitors can find the documentation from the project page. The first successful merge deployment makes that URL live. Description and topics can be updated through repository settings or `gh repo edit`. Add a social preview image through repository settings if a project-branded image is available.

## Release workflow

Once `.github/workflows/release.yml` is merged into `main`, use **Actions → Release ShaderX → Run workflow**. Choose the source using GitHub's **Use workflow from** branch selector, then choose a release type. A selected feature branch must contain the release workflow and its scripts. Review its code before publishing a beta: publishing executes that branch's build with repository signing credentials.

| Release type | Source branch | Version when the highest stable tag is `0.1.3` |
| --- | --- | --- |
| `prerelease` (default) | `main` or a feature branch | `0.1.4-beta.<run number>.<attempt>` |
| `patch` | `main` | `0.1.4` |
| `minor` | `main` | `0.2.0` |
| `major` | `main` | `1.0.0` |

These are numbering examples, not claims that the artifacts are published. The workflow reads all repository tags, accepts the existing bare tags and new `v`-prefixed tags, and compares stable versions numerically. Beta tags do not advance the stable version. Run and attempt numbers distinguish beta builds and retries. New tags use `v`, while Maven versions omit it. No manual version bump or automated commit to a protected branch is needed: the workflow passes `-PshaderxVersion=...` to the library build. The default in `shaderx/build.gradle.kts` remains the local development version.

Both stable releases and betas run `:shaderx:build` on macOS with JDK 21, covering Android, JVM, iOS and browser targets before publication. The workflow then reserves the version's tag at the exact selected commit, signs and publishes all `:shaderx` publications using `:shaderx:publishAndReleaseToMavenCentral`, and creates a GitHub release with generated notes and the dependency snippet. Betas are marked as prereleases and never replace GitHub's latest stable release. The summary includes the same coordinates. Runs are serialized across branches; publication is never cancelled by a newer dispatch.

This replaces the old release-event **Publish** workflow. Creating a GitHub release by hand no longer publishes artifacts, and the workflow publishes directly rather than relying on a second workflow triggered by its bot-created release. Library releases do not deploy the documentation site.

### One-time credentials

Keep these repository secrets under **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Central Portal user-token username |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal user-token password |
| `GPG_KEY_CONTENTS` | ASCII-armored private signing key; its public key must be distributed |
| `SIGNING_PASSWORD` | Key passphrase, if the key has one |
| `SIGNING_KEY_ID` | Optional signing key ID |

These names match the previous publishing workflow. See the publishing plugin's [Maven Central setup](https://vanniktech.github.io/gradle-maven-publish-plugin/central/) for namespace registration and credentials. The workflow uses its built-in `GITHUB_TOKEN` to create tags and releases; it needs no PAT and does not push to `main`. Repository tag rules must allow this token to create `v*` tags. Missing required credentials fail before the build; credentials are exposed to Gradle only in the publication step.

### Failed runs and retries

- **Before tag reservation:** fix the failure and start a new run (or rerun the failed job). Test reports are attached even when the build fails.
- **After tag reservation:** keep the tag. Maven Central versions are immutable, and a failed command might already have uploaded or published artifacts. Inspect the Central Portal deployment before doing anything else. A new stable run skips past the reserved version; a new beta run or retry gets a different run/attempt suffix. Do not delete or move a reserved tag to force version reuse.
- **Maven Central succeeded but GitHub release creation failed:** verify the artifacts, then create the GitHub release for the existing reserved tag, using the version and source SHA in the run log. Mark a beta as a prerelease. Do not rerun publication just to repair release notes.

The publishing task waits for Central Portal publication, but verify that the version resolves from Maven Central before changing consumer installation guidance. A tag alone is not proof of publication.

### Release documentation

Before dispatching a stable release, review the changelog, platform limitations, and relevant sample output. After confirming publication, update the dependency examples in `README.md`, `docs/getting-started.md`, and `docs/llms.txt`; update development/release labels across the catalog and guides, move completed changelog entries into the versioned section, and advance the local development version when appropriate. Run the documentation checks and merge that documentation PR to publish the refreshed site. Until publication is verified, keep the current published `0.1.3` and development `0.1.4` labels.

The Maven description and keywords in `shaderx/build.gradle.kts` should continue to reflect the same scope as the repository and guides. They take effect with the next artifact publication.

## Reference projects and editorial choices

[FileKit](https://github.com/vinceglb/FileKit) demonstrates a short installation path, a clear platform matrix, focused guides, and contribution entry points. ShaderX follows those patterns while documenting its own APIs and limitations.

[XCalendar](https://github.com/Debanshu777/XCalendar) demonstrates visual examples, explicit platform setup, and a browsable project structure. ShaderX keeps all four sample recordings inline in the README, with source links and run commands in the sample guide.

The blue flower banner takes visual inspiration from FileKit's library presentation and uses [Paper's actual halftone CMYK shader](https://shaders.paper.design/halftone-cmyk), with settings tuned in its live editor. It is brand artwork, not a screenshot of a built-in ShaderX halftone effect. The asset is shared by the README, documentation home page, and social-preview metadata. The [banner renderer](https://github.com/Debanshu777/ShaderX/tree/main/tools/banner) includes the source image, pinned shader dependency, exact settings, and PNG export instructions; it adds clean typography after rendering the WebGL effect.

Follow [Google's SEO guidance](https://developers.google.com/search/docs/fundamentals/seo-starter-guide) with useful page content, descriptive links, and accurate titles. Use [GitHub topics](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/classifying-your-repository-with-topics) for repository discovery. Avoid keyword stuffing, invented performance claims, and claims that unreleased functionality is available from Maven Central.
