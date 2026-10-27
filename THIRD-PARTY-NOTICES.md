# Third-party notices

This app is licensed under the MIT Licence (see `LICENSE`). It bundles the libraries below.
Apache-2.0 libraries require this notice and a copy of the Apache License, Version 2.0
(<https://www.apache.org/licenses/LICENSE-2.0>). Licences were verified at the source on
**2026-10-26**; regenerate this file whenever a dependency changes.

## Runtime dependencies (shipped in the APK)

| Library | Version | Licence (SPDX) | Verified at |
|---|---|---|---|
| Kotlin standard library | 2.3.21 | Apache-2.0 | <https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt> |
| androidx.core:core-ktx | 1.19.1 | Apache-2.0 | <https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt> |
| androidx.activity:activity-compose | 1.13.0 | Apache-2.0 | <https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt> |
| androidx.lifecycle (runtime-ktx, runtime-compose, viewmodel-compose) | 2.11.0 | Apache-2.0 | <https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt> |
| androidx.compose (BOM `2026.09.00`: ui, foundation, animation, runtime) | 1.12.1 | Apache-2.0 | <https://dl.google.com/android/maven2/androidx/compose/compose-bom/2026.09.00/compose-bom-2026.09.00.pom> |
| androidx.compose.material3 | 1.4.0 | Apache-2.0 | <https://dl.google.com/android/maven2/androidx/compose/compose-bom/2026.09.00/compose-bom-2026.09.00.pom> |
| androidx.compose.material:material-icons-core | 1.7.8 | Apache-2.0 | <https://dl.google.com/android/maven2/androidx/compose/compose-bom/2026.09.00/compose-bom-2026.09.00.pom> |
| androidx.navigation:navigation-compose | 2.10.2 | Apache-2.0 | <https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt> |
| androidx.room:room-runtime | 2.8.5 | Apache-2.0 | <https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt> |
| org.jetbrains.kotlinx:kotlinx-coroutines-android | 1.11.0 | Apache-2.0 | <https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt> |
| org.apache.commons:commons-csv | 1.14.1 | Apache-2.0 | <https://github.com/apache/commons-csv/blob/master/LICENSE.txt> |
| com.gyanoba.kexcel:kexcel-android | 0.1.2 | MIT | <https://github.com/ShreyashKore/kexcel/blob/main/LICENSE> |
| io.coil-kt.coil3:coil-compose | 3.6.3 | Apache-2.0 | <https://github.com/coil-kt/coil/blob/main/LICENSE.txt> |
| com.android.tools:desugar_jdk_libs | 2.1.5 | GPL-2.0 **with Classpath Exception** | <https://github.com/google/desugar_jdk_libs/blob/master/LICENSE> + `ADDITIONAL_LICENSE_INFO` |

`desugar_jdk_libs` is the only copyleft-adjacent runtime dependency; the Classpath Exception permits linking it with MIT-licensed code. Its licence and exception text must stay in the distributed notices.

## Test-only dependencies (not shipped)

JUnit 4 (`4.13.2`, EPL-1.0), androidx.test.ext:junit (`1.1.5`, Apache-2.0), androidx.test.espresso:espresso-core (`3.5.1`, Apache-2.0), Compose UI test artifacts (Apache-2.0).

## Build-only tools (not shipped)

Android Gradle Plugin `9.4.1` (Apache-2.0), Kotlin Compose plugin, KSP `2.3.12` (Apache-2.0), Room compiler, Gradle Foojay resolver convention (Apache-2.0).

## Attribution

- Apache-2.0 libraries: keep this notice and the Apache-2.0 text with any distributed APK or source archive.
- kexcel (MIT): its copyright notice is reproduced by this table plus the linked licence; no separate binary notice is embedded in the APK.
- No GPL/AGPL code is linked into the shipped app apart from `desugar_jdk_libs` under its Classpath Exception.
