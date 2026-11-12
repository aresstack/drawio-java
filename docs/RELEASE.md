# CI and release (#15)

## Continuous integration (`.github/workflows/ci.yml`)

| Job | Runs | What it proves |
|---|---|---|
| Java 8 build (Linux) | every PR, every push to `main` | `node js-bundle/build.mjs --verify` (pinned upstream bundles) and `gradlew clean build`. The build runs all tests including the corpus, `verifyJava8Bytecode` and `consumerSmokeTest`, and uploads the corpus report |
| Consumer JDK 11/17/21 (Linux) and 8/21 (Windows) | same | The Java 8 jar passes all tests and the offline consumer smoke test on newer JDKs and on Windows (`-PtestJavaVersion=N`) |
| Dependency vulnerability scan (OSV) | same, plus weekly on Monday | OSV-Scanner over the resolved classpath. Report-only, triage notes in [SECURITY.md](SECURITY.md) |

Library consumers never need Node.js. CI uses it only to re-verify the
checked-in JavaScript bundles against their pinned SHA-256 values.

Local equivalent:

```bash
node js-bundle/build.mjs --verify      # optional, needs Node 18+
./gradlew clean build                   # Java 8 toolchain required
./gradlew check -PtestJavaVersion=21    # run tests on another installed JDK
```

## Clean-checkout reproducibility

- The JavaScript bundles are committed, and `--verify` checks them against
  `js-bundle/upstream.json`.
- Dependency versions are pinned exactly in `build.gradle`; there are no
  dynamic versions.
- The Gradle wrapper (8.12) is committed.
- The corpus checks every fixture and reference image by SHA-256.

## Releasing

The project is MIT-licensed ([LICENSE](../LICENSE)); the POM declares it
and the jar carries it in `META-INF/LICENSE`.

Prerequisites, one-time decisions or settings by the owner:

1. **Stencil data:** done. It is published as the separate, optional
   artifact `drawio-java-stencils` ([LICENSE-REVIEW.md](LICENSE-REVIEW.md),
   section 2). Atlassian-related consumers depend on `drawio-java` only.
   To ship it inside the main jar again, see
   [STENCILS.md](STENCILS.md#folding-the-stencils-back-into-the-main-jar).
2. For Maven Central, add the repository secrets `CENTRAL_USERNAME` and
   `CENTRAL_PASSWORD` (a Central Portal user token), plus `GPG_PRIVATE_KEY`
   (ASCII-armored) and `GPG_PASSPHRASE`. These are the same names
   mermaid-java uses. The `com.aresstack` namespace must be verified in the
   Central Portal.

Then tag `main`:

```bash
git tag v0.1.0 && git push origin v0.1.0
```

`.github/workflows/release.yml` then:

1. verifies the bundles and runs the full build with `-PreleaseVersion=0.1.0`
   (tests, bytecode gate, stencil split check, both consumer smoke runs);
2. stages a Maven bundle (`gradlew publish` into `build/staging-deploy`),
   signed when `GPG_PRIVATE_KEY` is set. The bundle holds two artifacts:
   - `com.aresstack:drawio-java` (jar, sources, javadoc, POM, Gradle module
     metadata), MIT;
   - `com.aresstack:drawio-java-stencils` (jar, sources, documentation-only
     javadoc, POM), Apache-2.0 with the upstream Atlassian restriction in
     the license comments and the description;
3. uploads the bundle to the Central Portal as **USER_MANAGED** when the
   secrets exist, so a person still presses "Publish" after reviewing it;
4. creates a GitHub Release with the jars of both artifacts (main, sources,
   javadoc each), `LICENSE` and `THIRD-PARTY-NOTICES.md`.

`workflow_dispatch` with a version runs steps 1 and 2 only. Use it as a dry
run.

The published `drawio-java` POM lists GraalJS 21.3.20.0.3 and Batik svggen
1.20 as runtime dependencies. The jar contains the bundled draw.io
JavaScript, the draw.io Apache-2.0 license (`drawio/upstream/LICENSE`),
drawio-java's MIT license (`META-INF/LICENSE`) and
`META-INF/THIRD-PARTY-NOTICES.md`, but not the stencil data
`drawio/drawio-stencils.js`. The `drawio-java-stencils` POM has no
dependencies; its jar contains `drawio/drawio-stencils.js`,
`META-INF/LICENSE` (draw.io Apache-2.0) and its own
`META-INF/THIRD-PARTY-NOTICES.md` (from `src/stencils/`).
