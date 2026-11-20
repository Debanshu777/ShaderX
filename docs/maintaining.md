---
title: Maintain releases, documentation, and project discovery
description: Publish ShaderX documentation to GitHub Pages, update release-specific examples, and keep repository topics, Maven metadata, and search-facing content accurate.
---

# Maintainer guide

## Documentation publishing

The repository includes a static [Zensical](https://zensical.org/docs/) site using the compatible `mkdocs.yml` configuration format. Each guide produces HTML with a descriptive title, a unique description, a canonical URL, and Open Graph/Twitter metadata. The builder generates `sitemap.xml` and a local search index; the home page also describes the project using `SoftwareSourceCode` structured data.

The documentation URL is [debanshu777.github.io/ShaderX](https://debanshu777.github.io/ShaderX/).

1. Work on a feature branch and open a pull request against `main`.
2. The **Documentation** workflow checks source links and documentation impact, builds the production ShaderLab browser app with JDK 21, then builds the strict documentation site and bundles the app under `/playground/` plus the four-tab sampler under `/sampler/`. It validates generated metadata, anchors, sitemap, search, and all packaged app assets. The `documentation-site` preview artifact includes both guides and playground without publishing the feature branch.
3. Only when a PR is **merged into `main`**, its `pull_request_target: closed` event rebuilds the merged commit and deploys the validated artifact to **GitHub Pages**. Closing an unmerged PR skips the build and deployment. Direct pushes and manual dispatches do not trigger publication. A failed build cannot deploy.
4. Open the workflow's `github-pages` environment URL to verify the published site. To retry a failed publication, use **Re-run failed jobs** on that merged PR's workflow run. Deployment jobs are serialized so an in-progress publication is not cancelled by another merge.

The repository's **Settings → Pages → Source** must be **GitHub Actions**. This is a one-time hosting setting. The deployment job uses GitHub's short-lived `GITHUB_TOKEN` and OIDC permissions (`pages: write`, `id-token: write`); no personal access token or deployment secret is needed. The workflow never merges feature branches.

PR validation runs with read-only repository permissions and produces only a preview. The merge event checks `merged == true` and the `main` base branch before publishing, including for contributions from forks. It checks out the exact merged commit, never an unmerged PR head. Only the deployment job receives Pages/OIDC write permissions. See GitHub's [merged pull request event documentation](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#running-your-pull_request_target-workflow-when-a-pull-request-merges).

If using a custom domain, update `site_url` and the URLs in `docs/llms.txt`, then rebuild. A project site's `/ShaderX/robots.txt` would not control the host's crawlers: robots rules belong at the host root. The site does not add misleading subdirectory robot rules.

The build does not require analytics, remote fonts, or a paid documentation service. Search engines decide whether and when to index published pages; metadata is not a ranking guarantee.

Keep API behavior in the focused guides, validation commands and coverage in the [contributor guide](contributing.md), and publication requirements in the [stable release checklist](release-plan.md). Historical JSON records under `validation/evidence/` preserve the versions and environments they measured. Verify behavior before documenting it and verify Central artifacts before claiming availability.

### Build and preview the live playground

The [playground](playground/) is the ShaderLab Wasm production distribution, served under `/ShaderX/playground/`. It follows the local development library, not the published Maven release. The public route becomes available when the PR adding it is merged and the combined documentation deployment succeeds; branch preview artifacts already include the route.

With the [contributor toolchain](contributing.md#local-setup) installed, build the combined artifact:

```shell
./gradlew :samples:ShaderLab:composeApp:wasmJsBrowserDistribution :samples:Sampler:composeApp:wasmJsBrowserDistribution --no-daemon --no-parallel
zensical build --strict
python scripts/package_playground.py
python scripts/check_site.py
python -m http.server --directory site 8000
```

Open `http://localhost:8000/` and `http://localhost:8000/playground/`. A browser must serve the files over HTTP, not `file://`. Repackage after each documentation rebuild because Zensical can replace the `site` directory. When the sample source is unchanged, reuse its existing production distribution; the packaging step does not invoke Gradle.

The packager requires both production distributions and copies ShaderLab to `/playground/` and the sampler to `/sampler/`. Sampler tabs share one app/runtime download; opening the sampler does not load ShaderLab. The packager copies the complete distribution, including both Kotlin and Skia Wasm binaries, JavaScript chunks, license notices, and Compose resources. A manifest records every packaged file's size and SHA-256 digest. Validation fails for absent or modified files, invalid Wasm headers, missing sample resources, and an entry script that would escape the playground directory. Regression tests exercise truncated binaries and deleted deferred chunks, in addition to a successful bundle. CI never uploads or publishes a partially assembled site.

CI installs the Android SDK platform from the version catalog because Gradle configures Android modules even for the browser build. API 37's base package is `platforms;android-37.0`; the workflow maps integer API 37 and newer values to their `.0` package names and uses current SDK command-line tools. Keep the catalog value and package naming in sync when updating Android.

The development reuse panel stores only bounded typed configurations for registered definitions.
Keep preset format/schema versions explicit when changing controls, and preserve strict decoding
for unknown IDs, duplicate fields and invalid values. Browser-local storage and downloaded files
are local user data, separate from documentation publishing. Images and provider callbacks need
explicit rebinding and must never be made portable by serializing borrowed handles. Regenerate
and compile integration output after changes to binding descriptors or generator escaping;
preview clipboard fallback and keyboard/mobile editing in the production bundle.

The browser shell provides status text, retry on startup failure, and links to the guides and recordings. The app mounts in its own element and hides the loading screen after its first composition. The app uses no path-based router: tabs are UI state within `/playground/`, so refreshing that URL serves its own `index.html` without a catch-all 404 redirect. Keep relative JavaScript and resource paths so the GitHub Pages project prefix works.

Each canvas app has its own canonical URL and social previews, with `noindex,follow` and no sitemap entry. This is intentional: indexable setup instructions and examples live in the HTML guides, while the playground is an interactive companion. Its guide links remain usable when the app cannot run. ShaderLab keeps Gallery, Source and Pipeline within `/playground/`; the four teaching tabs share `/sampler/`, so refreshing either URL needs only that route's index file. The documentation home page links to the playground without loading its JavaScript or Wasm payload. Browser access does not change the supported-platform or published-version claims in the guides.

## Search visibility and the companion article

The site already provides crawlable guide links, unique titles and descriptions, canonical URLs, a sitemap, and source-code structured data. Keep the page text useful and accurate as the library evolves. `SoftwareSourceCode` describes the project; it does not promise a Google rich result. Social-preview metadata controls shared-link previews and is not a substitute for useful documentation.

After a documentation PR is merged and the site is deployed:

1. Add the URL-prefix property `https://debanshu777.github.io/ShaderX/` in [Google Search Console](https://search.google.com/search-console/). For HTML-tag verification, add the issued token as `extra.google_site_verification` in `mkdocs.yml`, merge and deploy that change, then verify ownership. The template emits the verification tag only when this setting is present. Keep it in place to retain verification.
2. Submit `https://debanshu777.github.io/ShaderX/sitemap.xml` in the Sitemaps report. Inspect the home page and key guides with URL Inspection to check fetch status, indexing eligibility, and Google's selected canonical. A submitted sitemap helps discovery; it does not guarantee indexing.
3. Record a baseline in the Search results report: impressions, clicks, queries, and landing pages. Compare equivalent periods after deployment, allowing time for crawling and indexing. A `site:` search is not a complete indexation report.
4. Check the home page and samples on mobile and desktop with [PageSpeed Insights](https://pagespeed.web.dev/). Review available field data separately from lab results; a small project may not have enough visits for URL-level field data. [Google's Core Web Vitals guidance](https://developers.google.com/search/docs/appearance/core-web-vitals) targets LCP within 2.5 seconds, INP under 200 milliseconds, and CLS under 0.1. A good lab score alone does not establish real-user performance or a ranking improvement.

The repository includes the public HTML-verification token issued for the documentation property. Once that change is deployed, complete verification from the account that created the property. The site checker verifies the configured token is present in the generated home page. Keep it after verification; it is public site metadata, not a credential.

For a new property or ownership change, the configuration has this shape; replace the placeholder with the `content` value supplied by Search Console:

```yaml
extra:
  google_site_verification: YOUR_SEARCH_CONSOLE_TOKEN
```

The companion Medium article is [Building GPU Shader Effects for Compose Multiplatform: From Scratch to ShaderX](https://medium.com/gitconnected/building-gpu-shader-effects-for-compose-multiplatform-from-scratch-to-shaderx-326659c4f47b). The README, home page, and custom-effects guide link to it for background. When updating the article, add a short current-documentation note linking to [installation](getting-started.md), the [custom shader contract](custom-effects.md), and [platform support](platforms.md) where each helps the reader. Use those guides' public URLs in Medium and keep version claims aligned with verified releases.

Keep the article's narrative and the documentation's reference material distinct. Cross-links should help readers continue a task; avoid repeating the article across many thin pages or adding keyword-heavy link lists. Keep each distinct page's own canonical URL. Only consider cross-domain canonicalization for a genuinely duplicate republication, not simply because two pages discuss ShaderX. [Google's link guidance](https://developers.google.com/search/docs/crawling-indexing/links-crawlable) explains descriptive links; a backlink is not a guaranteed ranking boost.

### Documentation media performance

The README retains the original flower artwork and all four established inline GIF paths, including the refreshed ShaderLab recordings. The home page and sample guide use the current ShaderLab/Sampler GIF walkthroughs; historical Android/browser recordings and camera/carousel videos remain MP4s with playback controls and `preload="none"`. Explicit media dimensions reserve space before loading; responsive CSS preserves their aspect ratio. Keep the home-page banner eager and below-the-fold walkthroughs lazy-loaded where supported.

When replacing a recording, compare the file size and playback against its source, check the first frame and motion, and preview desktop and mobile layouts. Keep a descriptive label and a fallback link to the recording. The site validator checks media dimensions, video loading policy, and local asset links alongside page metadata. [Google's web performance guidance](https://web.dev/articles/replace-gifs-with-videos) explains the bandwidth cost of animated GIFs; measure the generated assets rather than claiming an untested speedup.

To regenerate MP4 recordings and posters from the four established GIF paths, use an FFmpeg build that includes H.264 encoding. Match each page's dimensions and labels to the resulting footage; refreshed browser GIFs do not represent historical Android captures:

```shell
python scripts/encode_demo_videos.py --ffmpeg /path/to/ffmpeg
```

The documentation home page uses a lossless WebP copy of the original flower PNG. Regenerate it with:

```shell
python scripts/encode_banner.py --ffmpeg /path/to/ffmpeg
```

The encoder verifies that decoded pixels match the original. Keep the original PNG for the README and social previews.

Zensical rewrites raw HTML `src` and `href` paths relative to the Markdown source, but leaves video `poster` paths unchanged. In `samples.md`, use `assets/demos/` for a video source or fallback link, and `../assets/demos/` for its poster because the built page lives under `/samples/`. On `index.md`, all three use `assets/demos/`. The source validator accounts for that difference; keep ordinary Markdown guide links relative to their source files.

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

Follow the [stable release checklist](release-plan.md). Prepare and validate the full release
commit first, merge it through the normal PR process, then use **Actions → Release ShaderX →
Run workflow** from `main`. Enter the exact `confirmVersion` matching `shaderxVersion` in root
`gradle.properties`. This upgrade releases **0.2.0 directly as stable**; there is no beta stage.

The workflow reads the version from source, checks the explicit confirmation and `main` branch,
verifies Central metadata and the candidate coordinate, and reconciles the published baseline
with Git tags. Existing or lower versions fail before publication. New tags use `v`, while
Maven versions omit it. A source version bump does not claim that the artifact is published.

The workflow builds/tests `:shaderx` on macOS with JDK 21, covering the library's configured
Android, JVM, iOS and browser targets. It then reserves the immutable version tag at the exact
selected source commit, signs and publishes all `:shaderx` publications through
`:shaderx:publishAndReleaseToMavenCentral`, and creates a stable GitHub release with notes and
the dependency snippet. Runs are serialized across branches; publication is never cancelled
by a newer dispatch. Review sample, generated-consumer, migration and documentation validation
as part of the release checklist rather than treating a library build as their replacement.

Creating a GitHub release by hand does not publish artifacts. Library publication also does
not deploy the documentation site; that follows the merged documentation workflow.

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

- **Before tag reservation:** fix the failure and start a new run or rerun the failed job.
  Test reports are attached even when the build fails.
- **After tag reservation:** keep the tag. A failed command may already have uploaded or
  published immutable artifacts. Inspect the Central Portal deployment before taking any
  recovery action; do not delete or move a reserved tag to force version reuse.
- **Central succeeded but GitHub release creation failed:** verify the artifacts, then repair
  the GitHub release for the existing reserved tag and source SHA. Do not rerun publication
  merely to repair release notes.

### Release documentation

The release commit includes its versioned changelog, installation examples, migration guide,
platform limitations, relevant sample output and publishing safeguards together. Their version
matches root `gradle.properties`; `scripts/check_docs.py` checks the install coordinates,
version-catalog entry, `llms.txt` coordinate and changelog section against that source offline.
Routine guides explain one release API surface. Keep historical
feature/version details in the changelog and dated evidence, not labels on every example.

Until Central publication is verified, the [central publication status](index.md#releases-and-development-docs)
records that fact. After publishing, verify all target artifacts and clean consumer resolution,
then mark that status complete with the actual verification date. No second feature-doc rewrite
is required. Run documentation checks and verify the merged site's deployment; a tag alone is
not proof of publication or deployment.

The Maven description and keywords in `shaderx/build.gradle.kts` should continue to reflect the same scope as the repository and guides. They take effect with the next artifact publication.

## Reference projects and editorial choices

[FileKit](https://github.com/vinceglb/FileKit) demonstrates a short installation path, a clear platform matrix, focused guides, and contribution entry points. ShaderX follows those patterns while documenting its own APIs and limitations.

[XCalendar](https://github.com/Debanshu777/XCalendar) demonstrates visual examples, explicit platform setup, and a browsable project structure. ShaderX keeps all four sample recordings inline in the README, with source links and run commands in the sample guide.

The blue flower banner takes visual inspiration from FileKit's library presentation and uses [Paper's actual halftone CMYK shader](https://shaders.paper.design/halftone-cmyk), with settings tuned in its live editor. It is brand artwork, not a screenshot of a built-in ShaderX halftone effect. The README and social previews use the original PNG; the documentation home page uses its lossless WebP copy. The [banner renderer](https://github.com/Debanshu777/ShaderX/tree/main/tools/banner) includes the source image, pinned shader dependency, exact settings, and PNG export instructions; it adds clean typography after rendering the WebGL effect.

Follow [Google's SEO guidance](https://developers.google.com/search/docs/fundamentals/seo-starter-guide) with useful page content, descriptive links, and accurate titles. Use [GitHub topics](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/classifying-your-repository-with-topics) for repository discovery. Avoid keyword stuffing, invented performance claims, and claims that unreleased functionality is available from Maven Central.


## Public contract validation in CI

The library CI build checks the reviewed JVM ABI dump through `:shaderx:check`. A separate
step compiles Android-only and KMP public consumers and runs their JVM contract tests;
the consumer reports are uploaded alongside the library reports even after failure.
Updating the ABI dump requires reviewing the contract change, not just regenerating a green
build. Android connected pixel tests are an explicit device command described in the
[contributor guide](contributing.md#rendered-and-device-validation); ordinary hosted CI
builds do not establish physical AGSL or GPU performance evidence.

## Workbench preview packaging

After workbench changes rebuild the production Wasm distribution before packaging the site;
existing output is not evidence for new source. Inspect the guide and actual app at desktop and
mobile widths. Keep local draft storage distinct from the gallery preset slot, preserve the
README recordings, and record validation in `validation/evidence/sx07-custom-shader-workbench.json`.
Do not publish a branch merely to preview. Draft files contain unvalidated source and scalar
configuration; restoring a file must never promote a working SDK export or execute Kotlin.

### Android artifact compatibility gate

CI checks the release AAR's packaged minSdk 31, minCompileSdk 35 and Java 17 classfiles with
`scripts/check_android_artifact.py`, and builds a separate optimized release consumer. This is
an artifact/build gate; API 31 native-blur/fallback startup and API 33+ AGSL pixels require device
runs. Keep contributor JDK 21 separate from emitted Java 17 bytecode; historical artifact requirements belong in migration and dated evidence.
See [contributor commands](contributing.md#android-adoption-validation) and the [Android adoption validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx11-android-adoption.json).


### Final backend and generated-example gates

The workflow runs the sample Wasm browser pixel suite once on Linux with explicit Chrome setup,
not merely the library's common suite or the JVM generated-source producer. Current consumers
compile actual generated exports; optional ecosystem render checks remain consumer-only. macOS
also compiles Apple sample/consumer sources. The workflow retains generated Kotlin, failed-test
reports, AAR/publication metadata and R8 mappings. Local unsigned staging uses the init script
in [contributor validation](contributing.md#stage-current-consumer-artifacts-without-publishing)
and invokes no publication task. Device Compose window results remain separate from hosted
compilation/artifact checks, and legacy precompiled linkage remains an SX15 release gate.


## Stable release readiness

The [migration guide](migration.md) and the [frozen JVM compatibility record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx15-release-and-migration.json)
retain the original compatibility evidence. The [stable release checklist](release-plan.md)
covers the latest-stable audit, complete commit review and direct 0.2.0 publication. Local
workflow/YAML checks do not establish a remote Actions run, and a local Wasm distribution does
not deploy the hosted playground or guides. Keep physical hardware, independent minimum-AGP,
Android/Klib binary, long-running memory and GPU-budget evidence distinct from source/raster
checks. Update publication status only after the actual artifacts resolve.
