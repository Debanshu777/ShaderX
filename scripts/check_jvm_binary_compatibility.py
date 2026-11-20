#!/usr/bin/env python3
"""Freeze real published consumers, then run identical class bytes with only current ShaderX.

JVM proof only: this does not check Android dex/ART, Klibs, or new sealed/enum inputs
passed into old exhaustive handlers. Supply resolved JVM runtime dependencies, not
an arbitrary cache directory. Requires JDK 21 and a Kotlin compiler CLI classpath.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / "validation/binary-compatibility"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(command, log):
    result = subprocess.run([str(v) for v in command], capture_output=True, text=True)
    log.append({"command": [str(v) for v in command], "exit_code": result.returncode,
                "stdout": result.stdout, "stderr": result.stderr})
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout


def inventory(javap, jar, log):
    with zipfile.ZipFile(jar) as archive:
        names = sorted(n[:-6].replace("/", ".") for n in archive.namelist()
                       if n.endswith(".class") and not n.startswith("META-INF/"))
    output = run([javap, "-protected", "-s", "-classpath", jar, *names], log)
    classes = {}
    current = None
    declaration = None
    for line in output.splitlines():
        if line.startswith(("public ", "protected ")) and line.endswith("{"):
            match = re.search(r"\b(?:class|interface|enum) ([^ <]+)", line)
            if match:
                current = match.group(1)
                classes[current] = {"declaration": line, "members": []}
        elif line == "}":
            current = None
        elif current and re.match(r"  (public|protected) ", line):
            declaration = line.strip()
        elif current and declaration and "descriptor:" in line:
            descriptor = line.split("descriptor:", 1)[1].strip()
            # Name + raw JVM descriptor also covers constructors, mangled methods,
            # synthetic default bridges and public/protected fields.
            prefix = declaration.split("(", 1)[0] if "(" in declaration else declaration.rstrip(";")
            name = prefix.rsplit(" ", 1)[-1]
            if name == current:
                name = "<init>"
            classes[current]["members"].append({"name": name, "descriptor": descriptor,
                                                "declaration": declaration})
            declaration = None
    assert classes, f"No public/protected classes found in {jar}"
    return classes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["freeze", "freeze-compose", "run"])
    parser.add_argument("--old-jar", required=True, type=Path)
    parser.add_argument("--baseline-version", choices=["0.1.3", "0.1.4"], default="0.1.3",
                        help="Published fixture baseline; use a separate work directory for each version")
    parser.add_argument("--current-jar", required=True, type=Path)
    parser.add_argument("--dependencies", required=True, type=Path, help="File containing resolved dependency classpath")
    parser.add_argument("--compiler-classpath", required=True, type=Path)
    parser.add_argument("--java-home", required=True, type=Path)
    parser.add_argument("--compose-compiler-plugin", type=Path)
    parser.add_argument("--work", required=True, type=Path)
    args = parser.parse_args()
    args.work.mkdir(parents=True, exist_ok=True)
    log = []
    status = {"mode": args.mode, "scope": "JVM only; source and Android/Klib ABI are separate gates",
              "baseline_version": args.baseline_version,
              "old_jar": str(args.old_jar.resolve()), "old_jar_sha256": sha(args.old_jar)}
    java, javac, javap = (args.java_home / "bin" / name for name in ("java", "javac", "javap"))
    dependencies = [Path(x).resolve() for x in args.dependencies.read_text().strip().split(os.pathsep) if x]
    # Reject ANY dependency jar carrying ShaderX, even if hidden behind another name.
    for dependency in dependencies:
        if dependency.is_file() and zipfile.is_zipfile(dependency):
            with zipfile.ZipFile(dependency) as archive:
                if "com/debanshu/shaderlab/shaderx/factory/ShaderFactory.class" in archive.namelist():
                    raise ValueError(f"Dependency classpath must exclude library jars: {dependency}")
    dep_cp = os.pathsep.join(map(str, dependencies))
    frozen, runner = args.work / "frozen", args.work / "runner"
    compose = args.work / "frozen-compose"
    compose_manifest = args.work / "frozen-compose-manifest.json"
    manifest = args.work / "frozen-manifest.json"
    compile_prefix = [java, "-cp", args.compiler_classpath.read_text().strip(),
                      "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "21"]
    try:
        if args.mode == "freeze":
            if manifest.exists():
                raise ValueError("Frozen manifest exists; use a new directory rather than recompiling frozen bytecode")
            frozen.mkdir()
            compile_cp = os.pathsep.join([str(args.old_jar.resolve()), dep_cp])
            old_sources = [SOURCES / "old/FrozenConsumer.kt"]
            if args.baseline_version == "0.1.4":
                old_sources += sorted((SOURCES / "old-0.1.4").glob("*.kt"))
            run([*compile_prefix, "-classpath", compile_cp, "-d", frozen, *old_sources], log)
            run([javac, "--release", "21", "-cp", os.pathsep.join([str(frozen), compile_cp]),
                 "-d", frozen, SOURCES / "old/FrozenJavaCaller.java"], log)
            status["frozen_hashes"] = {str(p.relative_to(frozen)): sha(p) for p in sorted(frozen.rglob("*")) if p.is_file()}
            status["source_hashes"] = {str(p.relative_to(ROOT)): sha(p) for p in sorted(set(old_sources + list((SOURCES / "old").glob("*"))))}
            manifest.write_text(json.dumps(status, indent=2) + "\n")
        elif args.mode == "freeze-compose":
            assert args.compose_compiler_plugin, "Compose plugin path required"
            assert not compose_manifest.exists(), "Compose fixture already frozen"
            before = json.loads(manifest.read_text())
            assert before.get("baseline_version", "0.1.3") == args.baseline_version, "Frozen baseline version changed"
            assert before["old_jar_sha256"] == sha(args.old_jar), "Old artifact changed"
            compose.mkdir()
            compile_cp = os.pathsep.join([str(frozen), str(args.old_jar.resolve()), dep_cp])
            run([*compile_prefix, "-Xplugin=" + str(args.compose_compiler_plugin), "-classpath", compile_cp, "-d", compose,
                 SOURCES / "old/FrozenComposeConsumer.kt"], log)
            status["frozen_hashes"] = {str(p.relative_to(compose)): sha(p) for p in sorted(compose.rglob("*")) if p.is_file()}
            status["source_sha256"] = sha(SOURCES / "old/FrozenComposeConsumer.kt")
            compose_manifest.write_text(json.dumps(status, indent=2) + "\n")
        else:
            assert args.compose_compiler_plugin and compose_manifest.exists(), "Run freeze-compose first and supply its Compose compiler plugin"
            before = json.loads(manifest.read_text())
            assert before.get("baseline_version", "0.1.3") == args.baseline_version, "Frozen baseline version changed"
            actual = {str(p.relative_to(frozen)): sha(p) for p in sorted(frozen.rglob("*")) if p.is_file()}
            assert actual == before["frozen_hashes"], "Frozen consumer bytecode changed"
            assert before["old_jar_sha256"] == sha(args.old_jar), "Old artifact changed"
            status["frozen_hashes"] = actual
            status["current_jar_sha256"] = sha(args.current_jar)
            status["runner_source_hashes"] = {str(p.relative_to(ROOT)): sha(p) for p in sorted((SOURCES / "current").glob("*.kt"))}
            status["compiler_classpath_hashes"] = {p: sha(Path(p)) for p in args.compiler_classpath.read_text().strip().split(os.pathsep)}
            status["compose_compiler_sha256"] = sha(args.compose_compiler_plugin)
            if compose_manifest.exists():
                compose_before = json.loads(compose_manifest.read_text())
                assert compose_before.get("baseline_version", "0.1.3") == args.baseline_version, "Compose baseline version changed"
                assert compose_before["old_jar_sha256"] == sha(args.old_jar), "Compose old artifact changed"
                status["frozen_compose_hashes"] = {str(p.relative_to(compose)): sha(p) for p in sorted(compose.rglob("*")) if p.is_file()}
                assert status["frozen_compose_hashes"] == json.loads(compose_manifest.read_text())["frozen_hashes"]
            old = inventory(javap, args.old_jar, log)
            current = inventory(javap, args.current_jar, log)
            missing = []
            for class_name, value in old.items():
                present = {(m["name"], m["descriptor"]) for m in current.get(class_name, {}).get("members", [])}
                if class_name not in current:
                    missing.append({"class": class_name, "missing_class": True})
                for member in value["members"]:
                    if (member["name"], member["descriptor"]) not in present:
                        missing.append({"class": class_name, **member})
            changes = []
            for class_name, value in old.items():
                present = {(m["name"], m["descriptor"]): m for m in current.get(class_name, {}).get("members", [])}
                for member in value["members"]:
                    counterpart = present.get((member["name"], member["descriptor"]))
                    if counterpart and counterpart["declaration"] != member["declaration"]:
                        changes.append({"class": class_name, "name": member["name"], "descriptor": member["descriptor"],
                                        "old": member["declaration"], "current": counterpart["declaration"]})
            unresolved, inherited, noncontract = [], [], []
            def inherited_member(class_name, member, visited=None):
                visited = set() if visited is None else visited
                if class_name in visited or class_name not in current:
                    return None
                visited.add(class_name)
                declaration = current[class_name]["declaration"]
                parents = re.findall(r"(?:extends|implements) ([\w.$]+)", declaration)
                for parent in parents:
                    if any((m["name"], m["descriptor"]) == (member["name"], member["descriptor"])
                           for m in current.get(parent, {}).get("members", [])):
                        return parent
                    owner = inherited_member(parent, member, visited)
                    if owner:
                        return owner
                return None
            for member in missing:
                owner = inherited_member(member["class"], member) if member.get("name") != "<init>" else None
                if owner:
                    inherited.append({**member, "resolved_in": owner})
                elif member.get("name", "").startswith("access$"):
                    noncontract.append({**member, "reason": "Compiler-generated accessor for a private implementation lambda; not consumer API"})
                elif member["class"] == "com.debanshu.shaderlab.shaderx.factory.LruCache":
                    noncontract.append({**member, "reason": "Kotlin internal cache implementation; JVM-public visibility is not a supported consumer contract"})
                else:
                    unresolved.append(member)
            status["descriptor_inventory"] = {"old_public_protected_classes": len(old),
                "old_public_protected_members": sum(len(c["members"]) for c in old.values()),
                "changed_class_declarations": [{"class": n, "old": c["declaration"], "current": current.get(n, {}).get("declaration")} for n, c in old.items() if c["declaration"] != current.get(n, {}).get("declaration")],
                "changed_member_declarations": changes, "missing_declared_descriptors": missing, "inherited_resolutions": inherited,
                "reviewed_noncontract_changes": noncontract, "unresolved_consumer_descriptors": unresolved}
            (args.work / "old-descriptors.json").write_text(json.dumps(old, indent=2) + "\n")
            (args.work / "current-descriptors.json").write_text(json.dumps(current, indent=2) + "\n")
            runner.mkdir(exist_ok=True)
            runtime_cp = os.pathsep.join([str(runner), str(frozen), str(compose), str(args.current_jar.resolve()), dep_cp])
            status["runtime_classpath"] = runtime_cp.split(os.pathsep)
            status["dependency_hashes"] = {str(p): sha(p) for p in dependencies if p.is_file()}
            run([*compile_prefix, *(["-Xplugin=" + str(args.compose_compiler_plugin)] if args.compose_compiler_plugin else []), "-classpath", runtime_cp, "-d", runner, *sorted((SOURCES / "current").glob("*.kt"))], log)
            fixture_bytecode = run([javap, "-c", "-p", "-classpath", runner, "shaderx.binary.CurrentSourceFixtureKt"], log)
            calls = re.findall(r'invokestatic.*ShaderModifierKt\."([^"]+)"', fixture_bytecode)
            counts = {name: calls.count(name) for name in sorted(set(calls))}
            status["current_source_overload_calls"] = counts
            assert counts.get("shaderEffect-OTbkHQo") == 4, counts
            assert counts.get("shaderEffectWithResult-OTbkHQo") == 4, counts
            assert len(counts) == 4 and sorted(counts.values()) == [1, 1, 4, 4], counts
            run([java, "-cp", runtime_cp, "shaderx.binary.CurrentRunnerKt", args.current_jar.resolve(), args.baseline_version], log)
            assert not unresolved, "Consumer public/protected descriptors removed"
            assert status["frozen_compose_hashes"] == {str(p.relative_to(compose)): sha(p) for p in sorted(compose.rglob("*")) if p.is_file()}
            assert actual == {str(p.relative_to(frozen)): sha(p) for p in sorted(frozen.rglob("*")) if p.is_file()}
        status["status"] = "PASS"
    except Exception as error:
        status["status"] = "FAIL"
        status["error"] = str(error)
    status["commands"] = log
    report = args.work / (args.mode + "-report.json")
    report.write_text(json.dumps(status, indent=2) + "\n")
    print(f"{status['status']}: {report}")
    if status["status"] != "PASS":
        print(status["error"])
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
