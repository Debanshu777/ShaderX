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
3. After the PR merges, the push to `main` rebuilds the site and automatically deploys the validated artifact to **GitHub Pages**. A failed build cannot deploy. Deployments are serialized so an in-progress publication is not cancelled by another merge.
4. Open the workflow's `github-pages` environment URL to verify the published site. For a retry, run **Documentation → Run workflow** on `main`; no separate deploy toggle is needed. Running it on another branch only builds a preview.

The repository's **Settings → Pages → Source** must be **GitHub Actions**. This is a one-time hosting setting. The deployment job uses GitHub's short-lived `GITHUB_TOKEN` and OIDC permissions (`pages: write`, `id-token: write`); no personal access token or deployment secret is needed. The workflow never merges feature branches.

If using a custom domain, update `site_url` and the URLs in `docs/llms.txt`, then rebuild. A project site's `/ShaderX/robots.txt` would not control the host's crawlers: robots rules belong at the host root. The site does not add misleading subdirectory robot rules.

The build does not require analytics, remote fonts, or a paid documentation service. Search engines decide whether and when to index published pages; metadata is not a ranking guarantee.

## Keep documentation current with coding agents

[AGENTS.md](https://github.com/Debanshu777/ShaderX/blob/main/AGENTS.md) gives coding agents a change-to-guide map and requires documentation changes in the same PR as implementation changes. `CLAUDE.md` points to those shared rules, and `.github/copilot-instructions.md` applies them to Copilot work. These are repository instructions for agents working on a task, not a background AI service; they need no model API key.

The PR check detects library implementation, sample, dependency, and toolchain changes. Such PRs must update a Markdown guide or the README/contributor guide. An image-only or changelog-only change does not satisfy the check. For an internal refactor with no user-facing impact, explain the exception in the PR body:

```text
Docs-Impact: none
Docs-Reason: Internal cache refactor preserves every documented API and behavior.
```

Comments in the PR template do not count as an explanation. Editing the PR body reruns the check. Reviewers still need to assess correctness: a changed Markdown file is evidence of review, not proof that an explanation is accurate. Branch protection can require **Documentation / build** if maintainers want to enforce this as a merge requirement.

`scripts/check_docs.py` also requires the sunflower banner and all four demo recordings to remain embedded in the README. Site checks verify metadata, social-preview images, and links before any deployment.

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

These settings are managed on GitHub, not by the README or Maven POM. After publishing the site, a maintainer can update them with GitHub's repository settings or `gh repo edit`. Add a social preview image through repository settings if a project-branded image is available.

## Release checklist

1. Review the diff and platform limitations. Run the relevant Gradle checks and sample validation.
2. Set the intended publication version in `shaderx/build.gradle.kts` and move completed changelog entries into a versioned section.
3. Create the matching GitHub release only when ready to publish: the existing **Publish** workflow triggers on released and prereleased events.
4. Verify the artifact is resolvable on Maven Central before updating install snippets. A Git tag alone is not proof of publication.
5. Update the dependency examples in `README.md`, `docs/getting-started.md`, and `docs/llms.txt`; update the development/release labels across the catalog, guides, and changelog.
6. Run the documentation checks and publish the refreshed site.

The Maven description and keywords in `shaderx/build.gradle.kts` should continue to reflect the same scope as the repository and guides. They take effect with the next artifact publication.

## Reference projects and editorial choices

[FileKit](https://github.com/vinceglb/FileKit) demonstrates a short installation path, a clear platform matrix, focused guides, and contribution entry points. ShaderX follows those patterns while documenting its own APIs and limitations.

[XCalendar](https://github.com/Debanshu777/XCalendar) demonstrates visual examples, explicit platform setup, and a browsable project structure. ShaderX keeps all four sample recordings inline in the README, with source links and run commands in the sample guide.

The original blue sunflower banner takes visual inspiration from FileKit's library presentation and [Paper's halftone CMYK shader](https://shaders.paper.design/halftone-cmyk). It is a generated illustration, not a screenshot of a built-in ShaderX halftone effect. The banner asset is shared by the README, documentation home page, and social-preview metadata; its generation prompt is kept in `assets/banner-prompt.txt`.

Follow [Google's SEO guidance](https://developers.google.com/search/docs/fundamentals/seo-starter-guide) with useful page content, descriptive links, and accurate titles. Use [GitHub topics](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/classifying-your-repository-with-topics) for repository discovery. Avoid keyword stuffing, invented performance claims, and claims that unreleased functionality is available from Maven Central.
