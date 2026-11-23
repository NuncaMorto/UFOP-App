# Regras do Projeto — Vida Acadêmica (`Vida-Academica`)

## 1. Git, Branches, Commits e Push Contínuo
- Fluxo em duas branches principais no repositório `NuncaMorto/Vida-Academica`:
  - **`origin/develop`:** branch padrão de desenvolvimento e testes contínuos (Android + iOS sempre sincronizados). Após qualquer alteração aprovada e verificada, fazer commit claro e enviar (`git push`) para `origin/develop`. Compilações de teste são publicadas como **Pre-release** no GitHub.
  - **`origin/main`:** branch estável reservada para lançamentos oficiais (`v1.0.0` em diante), recebendo merge de `develop` somente quando Android e iOS estiverem validados.
- Quando houver incremento de versão do app (`versionName` / `versionCode`), criar e enviar também a tag anotada correspondente (`vX.Y.Z`, ex.: `v1.9.6` ou `v1.0.0`).
- Nunca faça commit ou push sem rodar antes a suíte de verificação aplicável às áreas alteradas.

## 2. Versionamento
- Para mudanças pequenas, correções e ajustes incrementais de UI/UX, usar versionamento patch no formato `x.x.y` (ex.: `1.9.12` -> `1.9.13`) em `android-app/app/build.gradle.kts`, incrementando `versionCode` a cada release.
- **`BuildConfig.VERSION_NAME` é a fonte única de verdade em runtime no Android:** `CampusRepositoryHolder.CURRENT_APP_VERSION` e `FeedbackSender.kt` lêem diretamente `BuildConfig.VERSION_NAME` (não duplicar literais hardcoded nem em código nem em testes unitários).
- Usar `.\scripts\bump-version.ps1 -Version <X.Y.Z> -Code <N>` para atualizar sincronizadamente `android-app/app/build.gradle.kts`, `ios-app/project.yml`, `docs/app.js`, `docs/index.html` e `ReleaseNotesSheetView.swift`.
- Antes de fechar o release, garantir sincronismo das notas em `CHANGELOG.md` (com `<!-- versionCode: N -->` no rodapé de cada seção de versão, lido por `publish-release.ps1` / `LIVE_VERSION_CODES`), `ReleaseNotesDialog.kt` e `ReleaseNotesSheetView.swift`, validando com `.\scripts\bump-version.ps1 -Version <X.Y.Z> -Code <N> -CheckOnly -Strict`.

## 3. Privacidade e Higiene Pré-Commit (Repo Pronto para Público)
- Nunca commitar binários de build ou distribuição (`dist/`, `*.apk`, `*.aab`), PDFs (`*.pdf`), arquivos locais de ambiente (`android-app/local.properties`, `.env*`) ou chaves/keystores (`*.jks`, `*.keystore`, `*.p12`, `*.pem`, `*.key`).
- Nunca gravar e-mails pessoais, tokens ou caminhos absolutos com nome de usuário local no código-fonte, testes ou scripts rastreados pelo Git:
  - Configurar o destinatário de feedback via `feedback.recipient` em `android-app/local.properties` (ignorado pelo Git) ou variável `FEEDBACK_RECIPIENT`, consumido via `BuildConfig.FEEDBACK_ENDPOINT`.
  - Em scripts Python (`scripts/`), usar variáveis de ambiente ou `Path.home()`.
- Antes de cada `git commit`, auditar o índice com `git status --short` e `git grep --cached` garantindo ausência de caminhos pessoais, segredos ou extensões bloqueadas.

## 4. Verificação e Armadilhas Conhecidas
- **Shell Windows (PowerShell):** encadear comandos com `;` (e checagem de `$LASTEXITCODE`), nunca `&&`.
- **Núcleo Rust (`rust-core`):** após alterações em Rust/SQLite/UniFFI, rodar `.\scripts\verify-core.ps1` (`cargo fmt --check`, `cargo clippy -- -D warnings`, `cargo test --workspace`).
- **Android (`android-app`):** definir `JAVA_HOME` (`C:\Program Files\Android\Android Studio\jbr`) e `ANDROID_HOME` (`%LOCALAPPDATA%\Android\Sdk`) e rodar `.\gradlew.bat :app:testDebugUnitTest --no-daemon --offline` (ou usar `.\scripts\build-apk.ps1` para gerar APK).
- **Testes JVM Android:** usar `java.util.Base64` em testes unitários locais (nunca `android.util.Base64`, que lança `RuntimeException` fora do emulador).
- **Compose / IHC:**
  - Não usar `LazyVerticalGrid` com spans dinâmicos alternando entre modo bloco e barra (causou travamento/desaparecimento de cards em `DisciplinesScreen.kt`); preferir `LazyColumn` com chaves distintas por modo de exibição.
  - Garantir alvo tátil mínimo de `48dp` com `minimumInteractiveComponentSize()` sem inflar desnecessariamente a altura visual de pílulas/filtros compactos quando a opção de acessibilidade de alvos grandes estiver desligada.

## 5. Mapa Rápido do Projeto
- **Diferenciação dos 3 conceitos de "Grade / Matriz":**
  - **Grade Dinâmica Interativa (Progresso & Pré-requisitos):**
    - Android: `android-app/app/src/main/java/br/ufop/campus/core/curriculum/DynamicCurriculumGrid.kt` + `ui/curriculum/DynamicCurriculumGridScreen.kt`
    - iOS: `ios-app/CampusUFOP/Core/Curriculum/DynamicCurriculumGrid.swift` + `Features/Curriculum/DynamicCurriculumGridView.swift`
  - **Matriz Curricular / Catálogo de Cursos UFOP (`54` cursos em JSON):**
    - Fonte única JSON: `android-app/app/src/main/assets/ufop_matrizes_2026_2.json` (copiada para `ios-app/CampusUFOP/Resources/Data/` no CI via `scripts/build-ios-ipa.sh`)
    - Android: `core/curriculum/UfopCurriculumCatalog.kt` + `ui/curriculum/CurriculumMatrixScreen.kt`
    - iOS: `Core/Curriculum/UfopCatalogs.swift` + `Features/Curriculum/CurriculumMatrixView.swift`
  - **Grade de Horários Semanal (Turmas matriculadas no semestre):**
    - Android: `ui/timetable/TimetableScreen.kt` | iOS: `Features/Timetable/TimetableView.swift`
- **Módulos principais após refatoração:**
  - Perfil Android (`ui/profile/`): `ProfilePlaceholderScreen.kt`, `ProfileMetricsTab.kt`, `ProfileSettingsTab.kt`, `ProfileUpdatesSubView.kt`, `ProfileFaqSubView.kt`, `ProfileCalendarSubView.kt`, `ProfileSecuritySubView.kt`, `ProfileFeedbackCard.kt`, `ProfileBackupAndLockDialogs.kt`, `ProfileSheets.kt`, `ProfileSharedComponents.kt`, `StudentOnboardingOverlay.kt`, `StudentOnboardingSteps.kt`, `StudentOnboardingHelpers.kt`.
  - Disciplinas Android (`ui/disciplines/`): `DisciplineDetailBottomSheet.kt`, `DisciplineDetailComponents.kt`, `DisciplineDetailSlotEditor.kt`, `DisciplineDetailDialogs.kt`.
  - Núcleo/Contratos Kotlin (`core/ffi/`): `CampusContracts.kt`, `CampusDomainModels.kt`, `CampusEngineBridgeRepository.kt`, `InMemoryCampusRepository.kt`, `CampusOfficialSeeds.kt`, `CampusPortugueseTextNormalizer.kt`, `CampusEnrollmentParser.kt`, `CampusSyllabusParser.kt`, `CampusCalendarAndTranscriptParser.kt`, `CampusRepositoryHolder.kt`.
  - Ponte iOS (`ios-app/CampusUFOP/CoreFFI/`): `CampusCoreModels.swift`, `CampusCoreBridge.swift`, `CampusCoreBridgeExtensions.swift`, `CampusStoreSeeds.swift`, `CampusStoreAgendaAndAssessments.swift`, `CampusStoreDisciplinesAndTemplates.swift`, `CampusStoreProfileAndDynamicGrid.swift`.
- **Comandos Prontos (Windows PowerShell):**
  - Higiene Swift pré-push: `.\scripts\check-swift-hygiene.ps1`
  - Atualizar/verificar versão: `.\scripts\bump-version.ps1 -Version X.Y.Z -Code N` e `-CheckOnly -Strict`
  - Testes JVM Android: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA "Android\Sdk"; Push-Location android-app; .\gradlew.bat :app:testDebugUnitTest --no-daemon --offline; $ec = $LASTEXITCODE; Pop-Location; exit $ec`
  - Build & validação APK (`aapt2` / `apksigner`): `.\scripts\build-apk.ps1`; `& "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0\aapt2.exe" dump badging dist\Vida-Academica-vX.Y.Z-debug.apk | Select-String "package:"`; `& "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0\apksigner.bat" verify --print-certs dist\Vida-Academica-vX.Y.Z-debug.apk`
  - Validação JS Download Page: `node --check docs/app.js`
  - Auditoria de privacidade pré-commit: `git status --short; git grep --cached -n -E 'C:\\Users\\|/Users/|/home/'`
  - Publicação Pre-release Android (lê `CHANGELOG.md` com `<!-- versionCode: N -->`): `.\scripts\publish-release.ps1 -Tag vX.Y.Z -Prerelease -AndroidOnly`
