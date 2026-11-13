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
and the jar carries it in `META-INF/LICENSE`. GitHub Releases and Maven
Central share one tag-triggered workflow, `.github/workflows/release.yml`.
Maven Central publishing works like mermaid-java's: same Central Portal
account, same secrets, `actions/setup-java` imports the GPG key, artifacts
are signed with gpg and the deployment is published automatically once
Central has validated it.

### Prerequisites

1. **Stencil data:** done. It is published as the separate, optional
   artifact `drawio-java-stencils` ([LICENSE-REVIEW.md](LICENSE-REVIEW.md),
   section 2). Atlassian-related consumers depend on `drawio-java` only.
   To ship it inside the main jar again, see
   [STENCILS.md](STENCILS.md#folding-the-stencils-back-into-the-main-jar).
2. **Secrets:** done. `CENTRAL_USERNAME` and `CENTRAL_PASSWORD` (Central
   Portal user token), `GPG_PRIVATE_KEY` (ASCII-armored) and
   `GPG_PASSPHRASE` are organization secrets of aresstack, the same ones
   mermaid-java uses, so drawio-java sees them without repository settings.
   The `com.aresstack` namespace is verified in the Central Portal
   (mermaid-java is published under it).

### The Maven Central switch

`gradle.properties` holds `mavenCentral.publish`:

| Value | A `v*` tag does |
|---|---|
| `false` (current) | build, tests, signed staged bundle, GitHub Release. Nothing goes to Maven Central. |
| `true` | the same, then uploads the bundle to the Central Portal and publishes it (`publishingType=AUTOMATIC`, the equivalent of mermaid-java's `autoPublish=true`). |

The switch stays `false` until the library is ready for Central. A
release on Maven Central can never be deleted or replaced, so a version
number is used up once it is there.

### Cutting a release

```bash
git tag v0.1.0 && git push origin v0.1.0
```

The version comes from the tag (`-PreleaseVersion=0.1.0`); `build.gradle`
holds only the `0.1.0-SNAPSHOT` default for local builds, so there is no
version bump commit. `.github/workflows/release.yml` then:

1. **job `release`:** verifies the bundles and runs the full build with
   `-PreleaseVersion=0.1.0` (tests, bytecode gate, stencil split check,
   both consumer smoke runs);
2. stages a Maven bundle (`gradlew publish -PsignWithGpg` into
   `build/staging-deploy`, zipped to `build/central-bundle.zip`), every
   file signed with the organization GPG key. The bundle holds two
   artifacts:
   - `com.aresstack:drawio-java` (jar, sources, javadoc, POM, Gradle module
     metadata), MIT;
   - `com.aresstack:drawio-java-stencils` (jar, sources, documentation-only
     javadoc, POM), Apache-2.0 with the upstream Atlassian restriction in
     the license comments and the description;
3. creates a GitHub Release with the jars of both artifacts (main, sources,
   javadoc each), `LICENSE` and `THIRD-PARTY-NOTICES.md`, and keeps the jars
   and the bundle as a workflow artifact;
4. **job `maven-central`**, only with the switch on: uploads the bundle to
   the Central Portal, publishes it and waits until Central reports
   `PUBLISHED` (or fails the job with Central's validation errors).

### Publishing an existing tag to Maven Central later

A tag cut while the switch was `false` (for example the first GitHub-only
release) can go to Central later without a new tag: Actions, "Release",
"Run workflow", pick the **tag** under "Use workflow from", tick
`publish_to_central`. The run rebuilds that tag, signs, uploads and
publishes it, and does not touch its GitHub Release. Only tags whose
commit already contains this workflow can be published this way.

To make every future tag go to Central, set `mavenCentral.publish=true`
in `gradle.properties` on `main` instead.

### Dry run

"Run workflow" on a branch with a `version` (e.g. `0.1.0-rc1`) runs the
build and stages the signed bundle only. Download it from the run's
artifacts to inspect what Central would receive. Locally:

```bash
./gradlew publish -PreleaseVersion=0.1.0-rc1        # unsigned, build/staging-deploy
./gradlew publish -PreleaseVersion=0.1.0-rc1 \
    -PsigningKey="$(cat key.asc)" -PsigningPassword=...   # signed in memory
MAVEN_GPG_PASSPHRASE=... ./gradlew publish -PreleaseVersion=0.1.0-rc1 -PsignWithGpg  # signed with gpg, as in CI
```

### What the POMs contain

The published `drawio-java` POM lists GraalJS 21.3.20.0.3 and Batik svggen
1.20 as runtime dependencies, the MIT license, the developer entry
`miguel0888` (organization aresstack, as in mermaid-java), SCM and issue
tracker. The jar contains the bundled draw.io
JavaScript, the draw.io Apache-2.0 license (`drawio/upstream/LICENSE`),
drawio-java's MIT license (`META-INF/LICENSE`) and
`META-INF/THIRD-PARTY-NOTICES.md`, but not the stencil data
`drawio/drawio-stencils.js`. The `drawio-java-stencils` POM has no
dependencies; its jar contains `drawio/drawio-stencils.js`,
`META-INF/LICENSE` (draw.io Apache-2.0) and its own
`META-INF/THIRD-PARTY-NOTICES.md` (from `src/stencils/`).
