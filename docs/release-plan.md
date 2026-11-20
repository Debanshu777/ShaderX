---
title: Commit and publish ShaderX 0.2.0
description: Prepare the complete ShaderX 0.2.0 stable release commit, validate its artifacts and consumers, publish the exact version, and verify the documentation deployment.
---

# ShaderX 0.2.0 stable release checklist

Release **0.2.0 directly as stable**, with no beta stage. The release commit contains the
library, samples, visual showcase, documentation and publishing safeguards together.
[Publication status](index.md#releases-and-development-docs) is tracked centrally; a source
version bump or a Git tag does not prove Maven Central availability.

The local release validation passed: [build, consumer, artifact and documentation results](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/release-0.2.0-validation.json), plus the [published JVM compatibility audit](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/release-0.2.0-jvm-compatibility.json). Remote CI, publication and hosted deployment remain subsequent steps.

[CHANGELOG.md](https://github.com/Debanshu777/ShaderX/blob/main/CHANGELOG.md) owns feature
history; [migration](migration.md) owns upgrade actions. Dated validation records retain
the exact versions they tested. Analytics remains deferred.

## 1. Freeze and review the release contents

- Include the library upgrade, four-tab Sampler, focused ShaderLab navigation, production
  browser distributions and visual README recipes. Preserve the banner and all four original
  inline sample recordings, including their accepted recording refreshes.
- Review staged, unstaged and untracked files together. Include the intended source, test,
  build/workflow, guide and media additions; exclude credentials, local build output, temporary
  staging repositories and disposable capture harnesses.
- Audit public APIs and source migration against the latest published artifact. Keep the
  historical frozen JVM fixture and latest-stable audit separate; neither source compilation
  nor JVM linkage establishes Android/Klib binary compatibility.
- Review exhaustive input/enum handlers, Wave reflection/data-class behavior, playback ownership,
  generated Kotlin imports and caller-rebound images/live inputs.

Exit: a reviewed complete diff and 0.2.0 changelog, with no unintended files or unsupported claims.

## 2. Confirm the exact release version { #2-make-version-allocation-safe-before-publishing-a-candidate }

`shaderxVersion` in root `gradle.properties` is the source version. Ordinary Gradle builds,
release documentation and the stable workflow must agree on **0.2.0**. Use a unique explicit
`-PshaderxVersion=...` coordinate only for local validation staging.

The stable workflow runs from `main` and requires `confirmVersion` to match the source version.
It verifies Central metadata and the candidate coordinate, rejects existing or lower versions,
and reconciles the published baseline with repository tags before reserving the new immutable
`v0.2.0` tag at the selected commit. It does not infer a version bump or generate beta artifacts.

Exit: version-allocation tests and a read-only/dry-run check select exactly 0.2.0 without
creating a tag, signing artifacts or publishing.

## 3. Validate the release commit { #3-run-release-validation-from-the-frozen-candidate }

Use JDK 21 and the [contributor commands](contributing.md#final-integration-validation):

- Full library build/common metadata, JVM API checks, current/frozen consumers and Android
  AAR minSdk/minCompileSdk/classfile inspection. Include the latest-stable baseline audit.
- ShaderLab and Sampler JVM/browser tests, generated Kotlin consumers, Android release/R8
  consumers and Apple target builds/tests where the required simulator is available.
- Fresh unsigned local publication staging under a unique coordinate. Resolve it from
  independent Android/JVM/iOS/Wasm consumers and inspect module/POM metadata.
- Inspect sample tabs and controls, source failure/recovery, chain reorder, stale-export
  invalidation, playback and the static/animated noise recipes.
- Run documentation checks, package both production browser apps after the site build,
  and inspect desktop/mobile pages and the release-coordinate examples.

Capture source SHA, toolchains, commands, reports and artifact hashes. Prior evidence is
history, not validation of a new commit. Stop for unintended API regressions, missing artifacts,
invalid generated Kotlin, failing required checks or secrets in the diff. Record unavailable
hardware, independent minimum AGP, long-running memory and GPU budget checks explicitly; do
not turn evidence gaps into broad compatibility or performance guarantees.

Exit: required checks pass for the release source, generated examples and staged publications.

## 4. Commit the complete upgrade { #4-publish-and-test-a-beta-then-make-the-stable-decision }

Review and commit the complete validated upgrade on the feature branch. The commit should
contain the 0.2.0 build version, changelog, installation examples and migration/platform guidance
alongside all intended library and sample changes. Review its actual file list and source SHA,
then merge through the normal PR process. Stable publication runs from `main`; verify that the
selected merged commit contains the reviewed changes and passes required CI checks.

No beta publication is required. A local commit does not deploy documentation or publish artifacts.

## 5. Publish and verify stable { #5-publish-stable-and-switch-the-docs-together }

1. Run **Actions → Release ShaderX** from `main`, enter `confirmVersion: 0.2.0`, and check that
   the selected source and workflow agree on the exact intended release coordinate.
2. The workflow builds/tests, reserves the tag and signs/publishes the library. Verify Central
   metadata plus every expected target binary and source artifact; a tag alone is insufficient.
3. Run clean consumer smoke checks against the downloaded Central artifacts. Verify the GitHub
   release tag and source SHA match the reviewed merged release commit.
4. Mark publication complete in the central status note with the actual verification date.
   All install examples and feature guides already target the release; do not reintroduce
   historical per-feature version labels.
5. Verify the merged documentation deployment, playground and Sampler. The documentation
   workflow publishes only after merge; library publication does not deploy the site.

If Central succeeds and GitHub release creation fails, repair the release notes for the
reserved tag without republishing. If publication fails after tag reservation, inspect the
Central deployment before retrying; never delete/move the tag or reuse a published coordinate.
If a published library is defective, document it and issue a new version.
