# Frozen JVM compatibility fixtures

The fixture compiles Kotlin/Java consumers and third-party factories against an **actual
Maven Central JVM jar**, hashes their class files, and runs those unchanged files with only
the current ShaderX jar. Select the published fixture baseline using `--baseline-version`:

- `0.1.3` (default) retains the original historical fixture and its original source files.
- `0.1.4` compiles those same common calls against the newer Central jar and adds
  `old-0.1.4/FrozenPublishedFeatures.kt`: published `TextureUniform` constructors/copy/tile modes
  and a real animated session creation/refresh/close through the current Skia backend.

The comments in the original `old/` sources describe their original historical use; the
selected baseline, artifact hash and actual compiler classpath in each manifest identify
which published jar a new freeze used. Use a **separate work directory for each baseline**.
Do not replace an old library jar or regenerate frozen outputs merely to make a diagnostic pass.

A separate Compose-plugin fixture calls both old modifier symbols in a real runtime
`Composition`, using null effects; it proves composition/linkage, not UI pixels. Ordinary old
calls also create a current Skia filter, update legacy parameters and exercise result/uniform
APIs. The current runner invokes the session operation on both old factory shapes, which must
return `UnsupportedEffect` rather than throw `AbstractMethodError`.

Run `scripts/check_jvm_binary_compatibility.py --help`. Supply a resolved JVM dependency
classpath file excluding ShaderX itself, a Kotlin compiler CLI classpath file, JDK 21,
the matching Compose compiler plugin and immutable old/current jars. Run modes in order:
`freeze`, `freeze-compose`, `run`, with the same arguments, baseline and work directory.
Only `run` may be repeated; it checks the frozen manifests before and after execution,
including artifact hashes and baseline identity. Existing historical manifests without an
explicit version field retain their original `0.1.3` meaning.

Reports contain exact commands, jar/source/class hashes, complete old/new public/protected
JVM descriptor inventories, changed class declarations, removed declared descriptors,
inheritance resolutions and explicitly classified compiler accessors/internal cache changes.
The runtime rejects dependencies containing a second ShaderX class and verifies CodeSource
for representative factory/effect/result/uniform classes plus the unique factory resource.

This checks the old library against the supplied current dependencies and JDK, not every
historical dependency combination. It does not establish Android dex/ART or Kotlin/Native
and Wasm Klib binary compatibility, old exhaustive handlers receiving newly introduced
subclasses/enum constants, physical GPU performance or universal behavioral compatibility.
The release evidence is recorded in `validation/evidence/release-0.2.0-jvm-compatibility.json`.
