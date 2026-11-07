---
title: Maintain releases, documentation, and project discovery
description: Publish ShaderX documentation to GitHub Pages, update release-specific examples, and keep repository topics, Maven metadata, and search-facing content accurate.
---

# Maintainer guide

## Documentation publishing

The repository includes a static [Zensical](https://zensical.org/docs/) site using the compatible `mkdocs.yml` configuration format. Each guide produces HTML with a descriptive title, a unique description, a canonical URL, and Open Graph/Twitter metadata. The builder generates `sitemap.xml` and a local search index; the home page also describes the project using `SoftwareSourceCode` structured data.

The configured URL is `https://debanshu777.github.io/ShaderX/`. This is the intended deployment destination, not a claim that the site is already live.

1. Merge the documentation changes and verify the **Documentation** workflow passes.
2. In repository **Settings → Pages**, select **GitHub Actions** as the source.
3. Run **Documentation** from **Actions → Run workflow** on `main`, setting **Deploy to GitHub Pages** to true. The default only validates and uploads a preview artifact.
4. Open the deployment URL and check navigation, search, installation, and the sitemap.
5. Set the repository website to that URL only after the site is available. The README's Markdown links continue to work independently.

If using a custom domain, update `site_url` and the URLs in `docs/llms.txt`, then rebuild. A project site's `/ShaderX/robots.txt` would not control the host's crawlers: robots rules belong at the host root. The site does not add misleading subdirectory robot rules.

The build does not require analytics, remote fonts, or a paid documentation service. Search engines decide whether and when to index published pages; metadata is not a ranking guarantee.

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

[XCalendar](https://github.com/Debanshu777/XCalendar) demonstrates visual examples, explicit platform setup, and a browsable project structure. ShaderX retains its sample recordings and adds run commands; large recordings are linked instead of all loading in the README.

Follow [Google's SEO guidance](https://developers.google.com/search/docs/fundamentals/seo-starter-guide) with useful page content, descriptive links, and accurate titles. Use [GitHub topics](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/classifying-your-repository-with-topics) for repository discovery. Avoid keyword stuffing, invented performance claims, and claims that unreleased functionality is available from Maven Central.
