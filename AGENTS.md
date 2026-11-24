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
- **Diferenciação dos 4 Pilares (Grades, Matriz e Mapa):**
  - **Grade Dinâmica Interativa (Progresso & Pré-requisitos):**
    - Android: `android-app/app/src/main/java/br/ufop/campus/core/curriculum/DynamicCurriculumGrid.kt` + `ui/curriculum/DynamicCurriculumGridScreen.kt`
    - iOS: `ios-app/CampusUFOP/Core/Curriculum/DynamicCurriculumGrid.swift` + `Features/Curriculum/DynamicCurriculumGridView.swift`
  - **Matriz Curricular / Catálogo de Cursos UFOP (`54` cursos em JSON):**
    - Fonte única JSON: `android-app/app/src/main/assets/ufop_matrizes_2026_2.json` (copiada para `ios-app/CampusUFOP/Resources/Data/` no CI via `scripts/build-ios-ipa.sh`)
    - Android: `core/curriculum/UfopCurriculumCatalog.kt` + `ui/curriculum/CurriculumMatrixScreen.kt`
    - iOS: `Core/Curriculum/UfopCatalogs.swift` + `Features/Curriculum/CurriculumMatrixView.swift`
  - **Grade de Horários Semanal (Turmas matriculadas no semestre):**
    - Android: `ui/timetable/TimetableModalScreen.kt` | iOS: `Features/Timetable/TimetableGridView.swift`
  - **Mapa 2D Offline (Rust + UniFFI + Android + iOS):**
    - Assets & Contrato (`rust-core/assets/map_data/`): `maps/campus_manifest.json`, bases vetoriais (`maps/*.geojson`), overlays indoor (`maps/indoor/*.geojson`), ocupação (`schedule/occupancy_2026_2.json`) e contrato (`schema/map_schema_contract.json`).
    - Núcleo Rust (`rust-core/src/domain/map/` + `rust-core/src/ffi/`): `assets.rs`, `mod.rs` (`CampusMapEngine`), `occupancy.rs`, `overlay/` (`mod.rs`, `parser.rs`, `assembler.rs`), `projection.rs`, `render.rs`, `resolver.rs`, `search.rs`, `spatial.rs`, `types.rs`, `ffi/map_engine.rs`.
    - Android (`android-app/...`): `core/map/CampusMapController.kt`, `core/map/CampusMapMath.kt`, `ui/map/CampusMapCanvas.kt`, `ui/map/CampusMapComponents.kt` + binding UniFFI em `core/uniffi/campus_core.kt`.
    - iOS (`ios-app/CampusUFOP/`): `Core/Map/` (`CampusMapModels.swift`, `CampusMapBridge.swift`, `CampusMapController.swift`), `Features/Map/` (`CampusMapCanvasView.swift`, `CampusMapComponentsView.swift`) + bindings UniFFI em `CoreFFI/Generated/`.
- **Módulos principais após refatoração:**
  - Núcleo Rust (`rust-core/src/`): `lib.rs`, `tests.rs`, `db.rs`, `models.rs`, `ffi/` (`mod.rs`, `campus_engine.rs`, `map_engine.rs`), `repository/` (`mod.rs`, `helpers.rs`, `disciplines.rs`, `agenda.rs`, `assessments.rs`, `profile.rs`), `domain/` (`agenda.rs`, `attendance.rs`, `timetable.rs`, `map/`).
  - Núcleo/Contratos Kotlin (`core/ffi/`): `CampusContracts.kt`, `CampusDomainModels.kt`, `CampusEngineBridgeRepository.kt`, `InMemoryCampusRepository.kt`, `InMemoryRepositoryPersistence.kt`, `InMemoryRepositorySnapshotReader.kt`, `InMemoryRepositorySnapshotWriter.kt`, `InMemoryRepositoryDisciplines.kt`, `InMemoryRepositoryImports.kt`, `InMemoryRepositoryAgenda.kt`, `InMemoryRepositoryAssessments.kt`, `InMemoryRepositoryProfileAndGrid.kt`, `CampusOfficialSeeds.kt`, `CampusPortugueseTextNormalizer.kt`, `CampusEnrollmentParser.kt`, `CampusSyllabusParser.kt`, `CampusCalendarAndTranscriptParser.kt`, `CampusRepositoryHolder.kt`.
  - UI Android (`ui/`):
    - `ui/timetable/`: `TimetableModalScreen.kt`, `TimetableToolbarComponents.kt`, `TimetableComparisonComponents.kt`, `TimetableGridComponents.kt`, `TimetableDialogs.kt`, `TimetableHelpers.kt`.
    - `ui/agenda/`: `AgendaScreen.kt`, `AgendaHeaderAndEventsComponents.kt`, `AgendaCalendarControls.kt`, `AgendaMonthCalendarCard.kt`, `AgendaTimelineComponents.kt`, `AddCommitmentBottomSheet.kt`, `ClassCheckInBottomSheet.kt`, `AgendaViewModel.kt`.
    - `ui/curriculum/`: `DynamicCurriculumGridScreen.kt`, `DynamicGridHeaderAndFilters.kt`, `DynamicGridHeaderControls.kt`, `DynamicGridSemesterColumns.kt`, `DynamicGridCourseCard.kt`, `DynamicGridSheetsAndDialogs.kt`, `CurriculumMatrixScreen.kt`, `CurriculumMatrixCoursePicker.kt`, `CurriculumMatrixSemesterList.kt`, `CurriculumMatrixDialogs.kt`.
    - `ui/activities/`: `ActivitiesPlaceholderScreen.kt`, `ActivitiesFilterBar.kt`, `ActivitiesListComponents.kt`, `ActivitiesDialogs.kt`, `AssessmentDetailBottomSheet.kt`.
    - `ui/disciplines/`: `DisciplinesScreen.kt`, `DisciplinesCards.kt`, `DisciplinesFilterComponents.kt`, `DisciplinesDialogs.kt`, `AddCourseBottomSheet.kt`, `AddCourseCatalogSection.kt`, `AddCourseScheduleEditor.kt`, `AddCourseFormComponents.kt`, `DisciplineDetailBottomSheet.kt`, `DisciplineDetailComponents.kt`, `DisciplineDetailSlotEditor.kt`, `DisciplineDetailDialogs.kt`, `DisciplinesViewModel.kt`.
    - `ui/profile/`: `ProfilePlaceholderScreen.kt`, `ProfileMetricsTab.kt`, `ProfileSettingsTab.kt`, `ProfileUpdatesSubView.kt`, `ProfileFaqSubView.kt`, `ProfileCalendarSubView.kt`, `ProfileSecuritySubView.kt`, `ProfileFeedbackCard.kt`, `ProfileBackupAndLockDialogs.kt`, `ProfileSheets.kt`, `ProfileSharedComponents.kt`, `StudentOnboardingOverlay.kt`, `StudentOnboardingSteps.kt`, `StudentOnboardingHelpers.kt`.
  - iOS (`ios-app/CampusUFOP/`):
    - `CoreFFI/`: `CampusCoreDomainModels.swift`, `CampusCoreScheduleModels.swift`, `CampusCoreProfileAndBackupModels.swift`, `CampusCoreBridge.swift`, `CampusCoreBridgeExtensions.swift`, `CampusStoreSeeds.swift`, `CampusStoreAgendaAndAssessments.swift`, `CampusStoreDisciplinesAndTemplates.swift`, `CampusStoreProfileAndDynamicGrid.swift`.
    - `Core/Curriculum/` & `Core/Parsers/`: `DynamicCurriculumGrid.swift`, `DynamicCurriculumGridModels.swift`, `DynamicCurriculumGridBuilder.swift`, `DynamicCurriculumGridEngine.swift`, `UfopPdfParsers.swift`, `UfopEnrollmentPdfParser.swift`, `UfopSyllabusAndTranscriptParser.swift`.
    - `Features/`: `ProfileView.swift` (+ `ProfileSummarySectionView.swift`, `ProfileCalendarAndUpdatesViews.swift`, `ProfileSettingsAndFaqViews.swift`, `ScheduleShareImportSheetView.swift`), `DisciplineDetailSheetView.swift` (+ `DisciplineDetailComponentsView.swift`, `DisciplineDetailSlotEditorView.swift`, `DisciplineDetailAssessmentsView.swift`), `StudentOnboardingView.swift` (+ `StudentOnboardingStepsView.swift`, `StudentOnboardingComponentsView.swift`), `DynamicCurriculumGridView.swift` (+ `DynamicCurriculumGridComponentsView.swift`, `DynamicCurriculumGridSheetsView.swift`).
- **Higiene de Contexto / Arquivos de Dados (NÃO LER):**
  - Nunca ler, abrir ou imprimir no contexto os seguintes arquivos volumosos ou gerados:
    - `rust-core/assets/map_data/maps/**/*.geojson`
    - `rust-core/assets/map_data/schedule/occupancy_*.json`
    - `rust-core/assets/map_data/schema/*.json`
    - `android-app/app/src/main/assets/*.json`
    - `android-app/app/src/main/java/br/ufop/campus/core/ffi/CampusOfficialSeeds.kt`
    - `ios-app/CampusUFOP/CoreFFI/CampusStoreSeeds.swift`
    - `ios-app/CampusUFOP/Core/Curriculum/UfopCatalogs.swift`
    - `android-app/app/src/main/java/br/ufop/campus/core/uniffi/campus_core.kt`
    - `ios-app/CampusUFOP/CoreFFI/Generated/*`
  - Para validar esses dados ou bindings sem inflar contexto, usar exclusivamente `.\scripts\check-map-assets.ps1`, `cargo test --workspace` (ou `.\scripts\verify-core.ps1`) e consultas pontuais com `git grep`.
- **Comandos Prontos (Windows PowerShell):**
  - Verificação completa do núcleo Rust: `.\scripts\verify-core.ps1`
  - Validação de assets de mapa, JSON/GeoJSON, privacidade e bindings UniFFI: `.\scripts\check-map-assets.ps1`
  - Compilação FFI Android + geração de bindings Kotlin: `.\scripts\build-android-ffi.ps1`
  - Higiene Swift pré-push: `.\scripts\check-swift-hygiene.ps1`
  - Atualizar/verificar versão: `.\scripts\bump-version.ps1 -Version X.Y.Z -Code N` e `-CheckOnly -Strict`
  - Testes JVM Android: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA "Android\Sdk"; Push-Location android-app; .\gradlew.bat :app:testDebugUnitTest --no-daemon --offline; $ec = $LASTEXITCODE; Pop-Location; exit $ec`
  - Build & validação APK (`aapt2` / `apksigner`): `.\scripts\build-apk.ps1`; `& "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0\aapt2.exe" dump badging dist\Vida-Academica-vX.Y.Z-debug.apk | Select-String "package:"`; `& "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0\apksigner.bat" verify --print-certs dist\Vida-Academica-vX.Y.Z-debug.apk`
  - Validação JS Download Page: `node --check docs/app.js`
  - Auditoria de privacidade pré-commit: `git status --short; git grep --cached -n -E 'C:\\Users\\|/Users/|/home/'`
  - Publicação Pre-release Android (lê `CHANGELOG.md` com `<!-- versionCode: N -->`): `.\scripts\publish-release.ps1 -Tag vX.Y.Z -Prerelease -AndroidOnly`
