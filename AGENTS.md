# Regras do Projeto — Vida Acadêmica UFOP (`UFOP-App`)

## 1. Git, Commits e Push Contínuo
- Após qualquer alteração aprovada/implementada e verificada no app, criar um commit claro no Git e enviar (`git push`) para `origin/main` no repositório `NuncaMorto/UFOP-App`.
- Quando houver incremento de versão do app (`versionName` / `versionCode`), criar e enviar também a tag anotada correspondente (`vX.Y.Z`, ex.: `v1.9.6`).
- Nunca faça commit ou push sem rodar antes a suíte de verificação aplicável às áreas alteradas.

## 2. Versionamento
- Para mudanças pequenas, correções e ajustes incrementais de UI/UX, usar versionamento patch no formato `x.x.y` (ex.: `1.9.5` -> `1.9.6` -> `1.9.7`) em `android-app/app/build.gradle.kts`, incrementando `versionCode` a cada release.
- Ao alterar `versionName`, manter sincronizadas as referências de versão em `android-app/app/src/main/java/br/ufop/campus/ui/profile/FeedbackSender.kt` e `android-app/app/src/main/java/br/ufop/campus/ui/profile/ReleaseNotesDialog.kt`.

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
