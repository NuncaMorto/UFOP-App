# Vida Acadêmica · UFOP (`UFOP-App`)

Aplicativo acadêmico offline-first para estudantes da **UFOP** (Universidade Federal de Ouro Preto), construído sobre um núcleo compartilhado de alto desempenho em **Rust + SQLite + UniFFI**, com clientes nativos em **Kotlin / Jetpack Compose** (Android) e **Swift / SwiftUI** (iOS).

---

## Downloads & Patch Notes (Releases)

Todos os pacotes instaláveis e o histórico completo de notas de atualização ficam publicados na aba oficial de [**Releases do repositório**](https://github.com/NuncaMorto/UFOP-App/releases) e documentados em [CHANGELOG.md](./CHANGELOG.md):

- **Android (`.apk`):** baixe `Vida-Academica-UFOP-vX.Y.Z-debug.apk` na página de Releases e abra no aparelho para instalar (habilite *"Instalar apps desconhecidos"* se solicitado). Confira a integridade via `SHA256SUMS.txt`.
- **iOS (`.ipa` não assinado):** baixe `Vida-Academica-UFOP-vX.Y.Z-unsigned.ipa` na página de Releases e instale via **AltStore**, **SideStore**, **Sideloadly**, **TrollStore** ou **Xcode**. *(Compilações em CI sem conta paga do Apple Developer Program geram `.ipa` não assinado — o conteúdo do app é idêntico e pode ser assinado localmente para o seu iPhone.)*
- **Como publicar uma nova versão:**
  1. Atualize a versão e as notas de atualização em [CHANGELOG.md](./CHANGELOG.md), crie a tag anotada (`git tag -a vX.Y.Z -m "Vida Acadêmica UFOP vX.Y.Z"`) e envie com `git push origin vX.Y.Z`, **ou**
  2. Use `scripts/publish-release.ps1 -Tag vX.Y.Z` para criar/atualizar a Release com os binários de `dist/` e `SHA256SUMS.txt` automaticamente (o workflow de CI em `.github/workflows/build-and-release.yml` automatiza os builds Android `.apk` e iOS `.ipa` no GitHub Actions).

---

## Índice

1. [Downloads & Patch Notes (Releases)](#downloads--patch-notes-releases)
2. [Visão Geral e Arquitetura](#visão-geral-e-arquitetura)
3. [Estrutura do Repositório](#estrutura-do-repositório)
4. [Núcleo Compartilhado (`rust-core`)](#núcleo-compartilhado-rust-core)
   - [Regra de Faltas da UFOP](#regra-de-faltas-da-ufop)
   - [Migrações SQLite (001 a 004)](#migrações-sqlite-001-a-004)
   - [API `CampusEngine` e UniFFI](#api-campusengine-e-uniffi)
5. [Aplicativo Android (`android-app`)](#aplicativo-android-android-app)
6. [Aplicativo iOS (`ios-app`)](#aplicativo-ios-ios-app)
7. [Como Instalar no Celular Android (Sideloading & ADB)](#como-instalar-no-celular-android-sideloading--adb)
8. [Scripts e Comandos Rápidos](#scripts-e-comandos-rápidos)
   - [Geração do APK Android](#1-geração-do-apk-android-windows--powershell)
   - [Verificação do Núcleo Rust](#2-verificação-do-núcleo-rust-windows--powershell)
   - [Compilação FFI e Bindings Android](#3-compilação-ffi-e-bindings-android)
   - [Compilação XCFramework e Bindings iOS](#4-compilação-xcframework-e-bindings-ios)
   - [Geração Manual de Bindings UniFFI](#5-geração-manual-de-bindings-uniffi)

---

## Visão Geral e Arquitetura

O projeto CAMPUS unifica as regras acadêmicas, modelo relacional e lógica de negócio da UFOP em uma única biblioteca Rust (`campus_core`). Por meio do **UniFFI**, as mesmas estruturas e funções são expostas de forma type-safe para Android e iOS, garantindo:

- **Offline-first determinístico:** O banco de dados SQLite opera localmente no dispositivo (com rusqlite bundled).
- **Consistência de regras:** Cálculo de faltas, resolução de conflitos de horário e templates curriculares são validados no núcleo Rust.
- **Experiência nativa:** Cada plataforma implementa sua interface seguindo as diretrizes de design modernas (Material 3 no Android e Human Interface Guidelines no iOS 17+), com suporte integral a Tema Claro e Escuro.
- **Isolamento e resiliência:** Ambos os clientes possuem pontes FFI robustas com fallback em memória para execução contínua mesmo em ambientes de desenvolvimento sem o binário nativo compilado.

```text
┌────────────────────────────────────────────────────────┐
│                      CAMPUS · UFOP                     │
├───────────────────────────┬────────────────────────────┤
│   Android (Jetpack Compose)│     iOS (SwiftUI / iOS 17+) │
│   - Material 3 (Dark/Light)│     - Adaptive Theme       │
│   - JNA + UniFFI Bridge    │     - Observable Store     │
│   - InMemory Repository    │     - Fallback Bridge      │
└─────────────┬─────────────┴─────────────┬──────────────┘
              │                           │
              └─────────────┬─────────────┘
                            ▼
           ┌─────────────────────────────────┐
           │     UniFFI Binding Layer        │
           │  (campus_core.kt / Swift FFI)   │
           └────────────────┬────────────────┘
                            ▼
           ┌─────────────────────────────────┐
           │      rust-core (Rust 2024)      │
           │  - CampusEngine & Repositories  │
           │  - SQLite (001-004 Migrations)  │
           │  - UFOP Attendance & Calendar   │
           └─────────────────────────────────┘
```

---

## Estrutura do Repositório

```text
.
├── Cargo.toml                                 # Workspace Rust raiz
├── rust-core/                                 # Crate campus_core (lógica de negócio, SQLite, UniFFI)
│   ├── Cargo.toml                             # Rust 2024, rusqlite bundled, uniffi 0.28
│   ├── uniffi.toml                            # Mapeamento de pacotes Kotlin e módulos Swift
│   ├── migrations/
│   │   ├── 001_initial_schema.sql             # Tabelas: departments, courses, schedule_slots, class_sessions, commitments, assessments
│   │   ├── 002_seed_ufop_2026_2.sql           # Dados base do semestre 2026.2 (Cálculo II, Algoritmos, Álgebra Linear, Física I)
│   │   ├── 003_sprint2_ufop_scale.sql         # Campi UFOP (Ouro Preto, Mariana, João Monlevade) e calendário acadêmico
│   │   └── 004_sprint3_course_templates.sql   # Templates curriculares oficiais (BCC DECOM, ENGC DECSI, Ciclo Básico EM)
│   └── src/
│       ├── lib.rs                             # CampusEngine, setup_scaffolding! e 17 testes unitários
│       ├── db.rs                              # Gerenciador SQLite e execução de migrações idempotentes
│       ├── models.rs                          # Estruturas de dados exportadas via UniFFI
│       ├── repository.rs                      # Operações CRUD, busca, transações e templates
│       ├── domain/
│       │   ├── attendance.rs                  # Regra oficial de faltas da UFOP
│       │   └── agenda.rs                      # Formatação de datas em português
│       └── bin/
│           └── uniffi-bindgen.rs              # CLI do uniffi-bindgen
├── android-app/                               # Cliente Android (Kotlin 2.0 + Jetpack Compose + Material 3)
│   ├── build.gradle.kts
│   └── app/
│       ├── build.gradle.kts                   # SDK 35, Java 17, Compose BOM, JNA 5.14
│       └── src/main/
│           ├── jniLibs/                       # Bibliotecas compiladas (.so) para arm64-v8a, armeabi-v7a, x86_64
│           └── java/br/ufop/campus/
│               ├── MainActivity.kt            # Entrada da aplicação e injeção do repositório
│               ├── core/
│               │   ├── ffi/
│               │   │   └── CampusContracts.kt # Interface CampusCoreRepository, CampusEngineBridgeRepository (JNA) e InMemoryCampusRepository
│               │   └── uniffi/
│               │       └── campus_core.kt     # Bindings Kotlin gerados via UniFFI
│               └── ui/
│                   ├── navigation/            # Barra inferior com 4 abas
│                   ├── theme/                 # Paleta de cores UFOP, tipografia e tema Material 3
│                   ├── agenda/                # Aba 1: Linha do tempo diária, clima e check-in de presença
│                   ├── disciplines/           # Aba 2: Cards, busca rápida, detalhes e histórico de faltas/cancelamentos
│                   ├── activities/            # Aba 3: Central de avaliações e compromissos extras
│                   ├── profile/               # Aba 4: Perfil acadêmico, status de risco e troca de campus
│                   └── timetable/             # Modal: Matriz semanal, templates UFOP e restauração de padrão
├── ios-app/                                   # Cliente iOS (Swift 5.9+ / SwiftUI iOS 17+)
│   ├── Package.swift                          # SPM com isolamento de build (`exclude: ["CoreFFI/Generated"]`)
│   └── CampusUFOP/
│       ├── App/                               # Ciclo de vida e MainTabView (4 abas)
│       ├── Theme/                             # Dynamic Color Palette adaptativa (Dark / Light)
│       ├── CoreFFI/
│       │   ├── CampusCoreBridge.swift         # CampusStore observável (@Observable) e fallback in-memory
│       │   └── Generated/                     # Bindings Swift + headers C gerados pelo UniFFI
│       └── Features/
│           ├── Agenda/                        # AgendaView e ClassCheckInSheetView
│           ├── Disciplines/                   # DisciplinesView, DisciplineDetailSheetView com histórico de marcações
│           ├── Activities/                    # Central de avaliações e compromissos
│           ├── Profile/                       # Resumo acadêmico, campi e calendário UFOP
│           └── Timetable/                     # TimetableGridView com matriz, templates e restauração 2026.2
├── dist/                                      # Pacotes prontos para distribuição e instalação
│   └── CAMPUS-UFOP-v1.0-debug.apk             # Pacote APK instalável em smartphones Android
└── scripts/
    ├── build-apk.ps1                          # Geração do APK debug Android e instalação via adb
    ├── verify-core.ps1                        # Verificação (fmt, clippy, tests) com auto-detecção MSVC vcvars64.bat
    ├── build-android-ffi.ps1                  # Compilação cargo-ndk e uniffi-bindgen para Android
    └── build-ios-xcframework.sh               # Compilação lipo + xcodebuild XCFramework para iOS
```

---

## Núcleo Compartilhado (`rust-core`)

### Regra de Faltas da UFOP

De acordo com as normas acadêmicas da UFOP, o limite de infrequência corresponde a no máximo **25% da carga horária** expressa em horas-aula de 50 minutos. A fórmula exata implementada em `domain/attendance.rs` é:

$$\text{max\_absences} = \frac{\text{workload\_hours} \times 3}{10}$$

Exemplos práticos:
- **30 horas** $\rightarrow$ **9 faltas** permitidas
- **60 horas** $\rightarrow$ **18 faltas** permitidas
- **90 horas** $\rightarrow$ **27 faltas** permitidas

**Contabilização de faltas atuais:**
- Total de faltas = `manual_absences + absent_session_weights`.
- Aulas com status `CANCELLED` **não contabilizam falta** (+0), incrementando apenas o contador de aulas canceladas da disciplina.

### Migrações SQLite (001 a 004)

1. **`001_initial_schema.sql`**: Define as entidades relacionais fundamentais (`departments`, `courses`, `schedule_slots`, `class_sessions`, `commitments`, `assessments`) com chaves estrangeiras e índices otimizados para busca por data e semestre.
2. **`002_seed_ufop_2026_2.sql`**: Popula o semestre base `2026.2` com 4 disciplinas (Cálculo II, Algoritmos, Álgebra Linear, Física I), slots semanais, sessões de aula, compromissos e avaliações com datas de exemplo.
3. **`003_sprint2_ufop_scale.sql`**: Introduz as tabelas `campuses` (com suporte aos campi de Ouro Preto - Morro do Cruzeiro, Mariana - ICHS e João Monlevade - ICEA) e `academic_calendar_events` (marcos do calendário escolar da UFOP).
4. **`004_sprint3_course_templates.sql`**: Cria o catálogo de grades curriculares padrão da UFOP (`tpl_bcc_decom` para Ciência da Computação, `tpl_ec_decsi` para Engenharia de Computação e `tpl_eng_basico` para Ciclo Básico da Escola de Minas), permitindo substituição controlada da grade.

### API `CampusEngine` e UniFFI

A classe principal `CampusEngine` expõe métodos de alto nível para os clientes nativos:

- **Agenda & Presença:**
  - `get_agenda_day(iso_date)`: Visão diária com previsão de temperatura, sessões de aula, compromissos e seletor de dias da semana.
  - `get_class_check_in(slot_id, iso_date)`: Dados detalhados para preenchimento de presença.
  - `save_class_check_in(slot_id, iso_date, status, color_override_hex)`: Atualiza status (`ATTENDING`, `ABSENT`, `CANCELLED`) e cor opcional da disciplina.
- **Disciplinas & Detalhes:**
  - `list_departments()`: Departamentos acadêmicos e paleta de cores.
  - `list_disciplines(semester, department_id)`: Disciplinas com cálculo de progresso de faltas e alertas de risco.
  - `get_discipline_detail(course_id)`: Visão completa com avaliações associadas e notas de estudo.
  - `update_discipline_notes(course_id, notes)`: Atualiza anotações persistidas.
  - `create_course(...)` e `delete_course(course_id)`: Gestão de disciplinas no semestre.
- **Grade Semanal & Slots:**
  - `get_weekly_timetable(semester)`: Matriz semanal de horários com faixas e células mapeadas por dia da semana (segunda a sábado).
  - `add_schedule_slot(...)`: Adiciona horário com validação estrita contra sobreposição/conflitos.
  - `delete_schedule_slot(slot_id)`: Exclui slot existente.
- **Sprint 4 (Histórico, Busca e Restauração):**
  - `list_absence_history(course_id)`: Histórico cronológico (`session_date DESC`) de faltas (`ABSENT`) e aulas canceladas (`CANCELLED`) com peso e data formatada.
  - `search_campus_items(semester, query)`: Pesquisa unificada e case-insensitive sobre disciplinas, avaliações e compromissos.
  - `reset_semester_to_default_seed(semester)`: Restaura atomicamente as 4 disciplinas e dados padrão do semestre 2026.2.

---

## Aplicativo Android (`android-app`)

O cliente Android é desenvolvido com **Kotlin 2.0** e **Jetpack Compose Material 3**:

- **Navegação em 4 Abas:**
  1. **Agenda:** Linha do tempo com cartões de aula e compromissos, previsão de temperatura do campus, pills de dias da semana e Bottom Sheet de check-in (`ClassCheckInBottomSheet`).
  2. **Disciplinas:** Grade de disciplinas com barra de progresso visual no padrão UFOP (verde $\rightarrow$ amarelo $\rightarrow$ vermelho de alerta), filtro por departamento acadêmico, campo de busca rápida (`OutlinedTextField`), folha de detalhes com anotações e **Histórico de marcações** com pills diferenciadas (`Falta (+1)` vs `Cancelada · 0 faltas`).
  3. **Atividades:** Central com listagem de provas, trabalhos e seminários vinculados a cada disciplina, badges de data (`20 NOV`, `24 NOV`) e alternância de conclusão.
  4. **Perfil:** Resumo com totalizador de faltas, disciplinas em situação de risco (>= 75% do limite de faltas), seletor de campus ativo (Ouro Preto, Mariana, João Monlevade) e calendário acadêmico.
- **Grade de Horários (`TimetableModalScreen`):**
  - Matriz visual semanal de horários.
  - Diálogo de confirmação para aplicação de templates oficiais da UFOP.
  - Opção no menu de ações para **Restaurar grade padrão 2026.2**.
- **Camada de Integração FFI / JNA e Persistência On-Device:**
  - `CampusContracts.kt`: Interface unificada `CampusCoreRepository` consumida por todas as ViewModels (`DisciplinesViewModel`, `AgendaViewModel`, `ActivitiesViewModel`, `TimetableViewModel`, `ProfileViewModel`).
  - `CampusEngineBridgeRepository`: Ponte primária que se comunica com o motor nativo em Rust via classes JNA geradas pelo UniFFI (`campus_core.kt`) e bibliotecas compartilhadas `.so` em `jniLibs/`. Emite chamadas seguras com fallback automático.
  - `InMemoryCampusRepository`: Repositório resiliente que opera de forma totalmente autônoma em previews, testes e smartphones físicos sem `.so` nativo. Implementa persistência local em JSON (`campus_ufop_state.json`) no armazenamento interno do app (`filesDir`), retendo o estado de faltas, notas, avaliações e campus ativo entre execuções.

---

## Aplicativo iOS (`ios-app`)

O cliente iOS é construído em **Swift 5.9+ / SwiftUI** com suporte a **iOS 17+**:

- **Design e Temas:** Paleta dinâmica adaptativa ao Dark Mode e Light Mode via `CampusTheme.swift`.
- **Paridade Funcional Completa:**
  - 4 abas estruturadas em `MainTabView` (Agenda, Disciplinas, Atividades e Perfil).
  - Folha de detalhes `DisciplineDetailSheetView` com visualização do **Histórico de marcações** (faltas e cancelamentos) e anotações.
  - Barra de busca rápida em `DisciplinesView` para filtragem ágil por nome ou professor.
  - Matriz semanal `TimetableGridView` com importação/exportação JSON, aplicação de templates com alerta de confirmação e restauração de padrão 2026.2.
- **Isolamento de Compilação SPM (`Package.swift`):**
  - Configurado com `exclude: ["CoreFFI/Generated"]` para prevenir colisões de símbolos entre os bindings UniFFI pré-gerados e a implementação do bridge `CampusCoreBridge.swift`.
  - O bridge atua como `CampusStore` observável (`@Observable`), permitindo reatividade automática em toda a hierarquia de Views.

---

## Como Instalar no Celular Android (Sideloading & ADB)

O aplicativo **Vida Acadêmica · UFOP** pode ser instalado diretamente em qualquer smartphone ou tablet Android (versão 8.0 / API 26 ou superior). O pacote distribuível gerado em `dist/Vida-Academica-UFOP-vX.Y.Z-debug.apk` (também publicado na página de [**Releases**](https://github.com/NuncaMorto/UFOP-App/releases)) contém o aplicativo completo com ícone adaptativo da UFOP, suporte aos temas Claro/Escuro (Material 3) e arquitetura offline-first resiliente.

### 1. Gerando o Pacote APK (`scripts/build-apk.ps1`)

Para compilar o APK debug assinado e pronto para uso:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1
```

O script automatiza:
- Configuração do Java JDK/JBR (`JAVA_HOME`) e do Android SDK (`ANDROID_HOME`).
- Compilação via Gradle Wrapper (`.\gradlew.bat :app:assembleDebug`).
- Cópia do binário para `dist\Vida-Academica-UFOP-vX.Y.Z-debug.apk`.
- Cálculo e exibição do tamanho em MB e do hash criptográfico SHA-256 para auditoria de integridade.
- Detecção automática de dispositivos conectados via `adb devices` e instalação direta caso um celular esteja plugado via USB.

---

### 2. Formas de Instalação no Smartphone

#### Opção A: Instalação via Cabo USB (ADB) — Rápida para Desenvolvedores

1. No celular Android, ative o **Modo Desenvolvedor** (acesse *Configurações > Sobre o Telefone* e toque 7 vezes em *Número da Versão*).
2. Em *Configurações > Opções do Desenvolvedor*, ative a **Depuração USB**.
3. Conecte o smartphone ao computador via cabo USB e autorize a chave RSA no aviso que aparecer na tela do aparelho.
4. Verifique no terminal se o dispositivo está conectado:
   ```powershell
   adb devices
   ```
5. Instale ou atualize o aplicativo mantendo os dados salvos (substitua `v1.9.6` pela versão atual):
   ```powershell
   adb install -r dist\Vida-Academica-UFOP-v1.9.6-debug.apk
   ```

#### Opção B: Instalação Manual Direta no Aparelho (Sideloading)

Não exige cabo nem comandos no celular:

1. Baixe `Vida-Academica-UFOP-vX.Y.Z-debug.apk` direto da página de [**Releases**](https://github.com/NuncaMorto/UFOP-App/releases) (ou transfira de `dist/` para o aparelho via USB ou Google Drive).
2. No celular, abra o aplicativo de **Arquivos** (ou Gerenciador de Arquivos / Downloads) e toque sobre o `.apk`.
3. Se o Android exibir a mensagem *"Para sua segurança, seu smartphone não tem permissão para instalar apps desconhecidos desta fonte"*:
   - Toque em **Configurações**.
   - Habilite a opção **"Permitir desta fonte"** (ou **"Instalar apps desconhecidos"**).
   - Volte para a tela anterior.
4. Toque em **Instalar** (ou **Atualizar**) e aguarde a conclusão.
5. Toque em **Abrir** para iniciar o **Vida Acadêmica UFOP**.

---

### 3. Persistência de Dados On-Device (`campus_ufop_state.json`)

Para assegurar total estabilidade e portabilidade em aparelhos onde a biblioteca nativa Rust (`.so`) não estiver presente para a arquitetura do processador, o aplicativo utiliza o mecanismo de persistência local contínua via JSON:

- **Local dos Dados:** O estado é persistido no armazenamento interno privado do aplicativo (`context.filesDir/campus_ufop_state.json`), sem depender de serviços em nuvem ou conexões remotas.
- **Informações Persistidas:**
  - Presenças, faltas registradas (+1) e aulas canceladas (+0 faltas) feitas na aba **Agenda**.
  - Anotações de estudo, links e notas de disciplinas na aba **Disciplinas**.
  - Avaliações (provas, trabalhos, seminários) criadas ou marcadas como concluídas na aba **Atividades**.
  - Compromissos acadêmicos e extracurriculares adicionados.
  - Campus ativo selecionado (Ouro Preto - Morro do Cruzeiro, Mariana - ICHS ou João Monlevade - ICEA).
  - Horários customizados ou alterações na grade de disciplinas.
- **Restauração Limpa:** Se desejar redefinir todas as informações e voltar ao estado original do semestre `2026.2`, acesse a **Grade de Horários** (ícone no cabeçalho), abra o menu de opções e toque em **"Restaurar grade padrão 2026.2"**. O arquivo de persistência será reescrito com a semente padrão da UFOP.

---

## Scripts e Comandos Rápidos

### 1. Geração do APK Android (Windows / PowerShell)

Compila o aplicativo Android em modo debug, copia o artefato para a pasta `dist/` e opcionalmente instala no dispositivo conectado via ADB:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1
```

### 2. Verificação do Núcleo Rust (Windows / PowerShell)

O script `scripts/verify-core.ps1` automatiza a validação completa do core:
- Detecta se `link.exe` está ausente do PATH no Windows e inicializa automaticamente o ambiente MSVC a partir de `vcvars64.bat` (`C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\VC\Auxiliary\Build\vcvars64.bat` ou equivalente).
- Executa em sequência:
  1. `cargo fmt --all -- --check`
  2. `cargo clippy --workspace --all-targets -- -D warnings`
  3. `cargo test --workspace`
- Interrompe a execução imediatamente com código de saída não-zero caso ocorra qualquer falha.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-core.ps1
```

### 3. Compilação FFI e Bindings Android

Compila a biblioteca nativa `campus_core` via `cargo-ndk` para as ABIs `arm64-v8a`, `armeabi-v7a` e `x86_64`, depositando os arquivos `.so` em `android-app/app/src/main/jniLibs/`, além de gerar os bindings Kotlin:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-android-ffi.ps1
```

> **Pré-requisitos:** Android NDK configurado (`ANDROID_NDK_HOME`) e `cargo install cargo-ndk`.

### 4. Compilação XCFramework e Bindings iOS

Compila o núcleo Rust para alvos iOS (`aarch64-apple-ios`, `aarch64-apple-ios-sim`, `x86_64-apple-ios`), cria a biblioteca estática combinada com `lipo`, gera os bindings Swift e empacota o `CampusCoreFFI.xcframework`:

```bash
./scripts/build-ios-xcframework.sh
```

> **Pré-requisitos:** macOS com Xcode instalado e ferramentas de linha de comando (`xcodebuild`).

### 5. Geração Manual de Bindings UniFFI

Para gerar manualmente os bindings Kotlin ou Swift a partir de uma biblioteca compilada para o host:

**Compilar a biblioteca de host:**
```powershell
cargo build -p campus_core
```

**Gerar bindings Kotlin para o Android:**
```powershell
cargo run -p campus_core --bin uniffi-bindgen -- generate `
    --library target/debug/campus_core.dll `
    --language kotlin `
    --config rust-core/uniffi.toml `
    --out-dir android-app/app/src/main/java
```
*(No Linux/macOS, substitua `campus_core.dll` por `libcampus_core.so` ou `libcampus_core.dylib`)*

**Gerar bindings Swift para o iOS:**
```powershell
cargo run -p campus_core --bin uniffi-bindgen -- generate `
    --library target/debug/campus_core.dll `
    --language swift `
    --config rust-core/uniffi.toml `
    --out-dir ios-app/CampusUFOP/CoreFFI/Generated
```

> **Dica:** Caso a CLI do UniFFI esteja instalada globalmente (`cargo install uniffi-bindgen --version 0.28.3`), é possível executar diretamente:
> ```powershell
> uniffi-bindgen generate --library target/debug/campus_core.dll --language kotlin --config rust-core/uniffi.toml --out-dir android-app/app/src/main/java
> ```
