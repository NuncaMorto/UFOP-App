# Changelog & Patch Notes — Vida Acadêmica

> **Aviso de Independência:** Aplicativo independente, desenvolvido por estudantes sob licença **GNU GPLv3** e sem qualquer vínculo oficial, endosso ou afiliação com a Universidade Federal de Ouro Preto (UFOP).

Todas as compilações instaláveis (Android `.apk` e iOS `.ipa` não assinado) ficam disponíveis na página de [Releases do repositório](https://github.com/NuncaMorto/Vida-Academica/releases).

> **Sobre os formatos distribuídos:**
> - **Android (`Vida-Academica-vX.Y.Z-debug.apk`):** APK direto para instalação no Android 8.0+.
> - **iOS (`Vida-Academica-vX.Y.Z-unsigned.ipa`):** IPA compilado sem assinatura paga da Apple; instalável via AltStore, SideStore, Sideloadly, TrollStore ou Xcode.

---

<!-- versionCode: 28 -->
## v1.9.11 — Rotação da Grade Dinâmica, Matrizes por Ingresso & Aba Atualizações

- **Android (`1.9.11` · Beta) & iOS (`1.9.11`):**
  - **Rotação e área segura na Grade Dinâmica (Android):** removida a opção duplicada de tela cheia; ao selecionar **Horizontal** ou **Vertical**, o app alterna automaticamente entre paisagem e retrato em tela inteira usando a área segura (`WindowInsets.safeDrawing`), eliminando vazamentos de fundo e deslocamentos laterais.
  - **Controles simétricos e recolher por ícone:** o botão de recolher/expandir agora mostra apenas uma seta para dar mais espaço ao nome do curso; a pílula Horizontal/Vertical tem espaçamento uniforme em volta da opção selecionada e a mesma altura do botão Limpar.
  - **Versionamento curricular pelo semestre de ingresso:** a matriz de Eng. de Controle e Automação passa a identificar **2024/1 · Currículo 2** (ingressantes de 2024/1 a 2025/2, preservando seleções anteriores) e adiciona a **2026/1 · Currículo 3** (ingressantes a partir de 2026/1), detectada automaticamente pela matrícula/histórico.
  - **Perfil reorganizado:** **Atualizações & Histórico de Patches** ganhou aba própria fora de FAQ & Ajuda, e **Refazer Tour Guiado** foi movido para **Configurações / Ajustes**.

## v1.9.10 — Mais Espaço na Grade Dinâmica (Cabeçalho Recolhível)

- **Android (`1.9.10` · Beta) & iOS (`1.9.10`):**
  - **Cabeçalho mais limpo:** removido o título duplicado `Grade Dinâmica` de dentro do cartão superior (Android).
  - **Botão Recolher / Expandir:** permite recolher os controles superiores (seletor de matriz, orientação, limpar, tela cheia e legenda) para ampliar a área visível da grade mantendo o resumo compacto de horas e progresso.

<!-- versionCode: 27 -->

---

## v1.9.9 — Correção do Atualizador Interno Android

- **Android (`1.9.9` · Beta · Atualizador In-App):**
  - **Corrigido o fechamento das telas sem concluir a atualização:** após baixar e validar o hash **SHA-256**, o app agora entrega o pacote diretamente ao instalador nativo do sistema (`ACTION_VIEW` + `FileProvider` com `FLAG_ACTIVITY_NEW_TASK` e `<queries>` no manifesto), exibindo o progresso real de instalação e o botão **Abrir**.
  - **Cache protegido e retomada segura:** o APK verificado não é apagado enquanto a nova versão ainda não assumiu, permitindo reabrir o instalador com um toque sem precisar baixar tudo de novo.
  - **Fallback reforçado e diagnóstico visível:** o modo alternativo por sessão (`PackageInstaller`) foi endurecido para exigir confirmação explícita (`USER_ACTION_REQUIRED`), encerrar sessões órfãs e persistir eventuais erros de sistema para exibição na reabertura.
- **iOS (`1.9.9`):** sincronização de versão e notas mantendo a Grade Dinâmica Interativa e todos os recursos da 1.9.8.

<!-- versionCode: 26 -->

---

## v1.9.8 — Grade Dinâmica Interativa (Matriz 2023 de Automação)

- **Android (`1.9.8` · Beta · Material 3 Expressive) & iOS (`1.9.8` · Apple Liquid Glass):**
  - **Nova aba Grade Dinâmica no Perfil (logo abaixo de Métricas & CRE / resumo de CRE):** teste inicial com a **Matriz Curricular 2023 (Currículo 2) de Engenharia de Controle e Automação** (`54` disciplinas · `2970h` na grade · `10` períodos).
  - **Desbloqueio interativo e cascata transitiva:** marque disciplinas concluídas para liberar automaticamente as próximas matérias que cumprem pré-requisitos, veja os requisitos pendentes ao tocar em disciplinas bloqueadas e desmarque dependentes automaticamente em cascata quando remover um pré-requisito.
  - **Modos Horizontal e Vertical com rolagem livre:** alterne entre colunas por período (Horizontal) ou linhas por período (Vertical) com preferência salva, rolagem livre horizontal e vertical nos dois modos (além de modo tela cheia) e salvamento automático na persistência local criptografada.

<!-- versionCode: 25 -->

---

## v1.9.7 — Atualizador In-App, GPLv3 & Adequação para Lojas

- **Android (`1.9.7` · Beta):**
  - **Atualização automática dentro do app:** verificação silenciosa via GitHub Releases, barra de progresso em segundo plano (com opção de continuar usando o app), checagem criptográfica **SHA-256** antes de instalar e reinicialização automática preservando 100% das disciplinas, faltas e dados criptografados.
  - **Botão manual em Perfil → FAQ:** `Verificar atualizações (GitHub)` com status em tempo real e sem poluir a barra de notificações do Android.
- **Compliance, Privacidade & Projeto:**
  - Adequação de identificadores (`io.github.nuncamorto.vidaacademica`), metadados e textos às políticas de propriedade intelectual da App Store (5.2.1) e Google Play, com aviso explícito de projeto estudantil independente.
  - Licença **GNU GPLv3** adicionada (`LICENSE`) e nova página web estática de downloads em **`nuncamorto.github.io/Vida-Academica`**.

<!-- versionCode: 24 -->

---

## v1.9.6 — Paridade iOS Liquid Glass, Filtro Multi-Tipo & Disciplinas

- **iOS (`Vida Academica UFOP` · Apple Liquid Glass):** paridade completa das atualizações **v1.5 → v1.9.6** adotando design **Apple Liquid Glass** (Xcode 26 / iOS 26 SDK com fallback `.ultraThinMaterial` para iOS 17–18):
  - Cadastro inicial em 4 etapas (Atestado de Matrícula PDF com detecção automática de EAD e salas oficiais 2026.2, Planos de Ensino, 7 cores de destaque e acessibilidade).
  - Criptografia local **AES-256-GCM** no Keychain, bloqueio por PIN e **Face ID / Touch ID**, remoção imediata de PDFs temporários da memória, backup portátil `.ufopbackup` e saída segura de conta.
  - Catálogo com **54 matrizes curriculares da UFOP**, histórico escolar PDF com cálculo e simulador de CRE e alerta de matriz legada (`< 2026/1`).
  - **Calendários Oficiais UFOP 2026.2, 2027.1 e 2027.2** integrados na Agenda dinâmica (7 dias + navegação entre semanas + check-in em qualquer data).
  - **Disciplinas** em **Blocos** ou **Barras** (sem selo redundante e sem truncamento), **Grade Horária** Vertical/Horizontal com check-in rápido, **Atividades** com seletor multi-tipo `Todos os tipos ▾`, Tour Guiado animado, Notas de Atualização e Widget Liquid Glass.
- **Android (`1.9.6`):**
  - Correção na aba **Disciplinas** ao alternar entre **Grade** e **Barras** (sem ocultar matérias ou fechar ao abrir detalhes) e leitura limpa de `12/18 · Atenção`.
  - Novo seletor multi-tipo em **Atividades** (`Todos os tipos ▾`) para filtrar um ou mais tipos (Provas, Trabalhos, Seminários e Concluídas) na mesma linha.
  - Alinhamento corrigido do chip **Pendente / Concluída** na ficha da avaliação.

---

## v1.9.5 — Salas Oficiais 2026.2, Disciplinas em Grade/Barras & Polimento Visual

- **Salas oficiais 2026.2 (ICEB e Bloco de Salas):** identificação automática da sala real no atestado e na grade; outros prédios permanecem sem sala fixa até nova tabela.
- **Disciplinas em Grade ou Barras:** novo modo de exibição escolhido pelo aluno e cartões mais limpos, sem excesso de selos.
- **Agenda sem redundância:** aviso `"Agora / Próxima aula"` integrado diretamente no card da aula quando você já está no dia dela.
- **Grade Horária com memória de orientação:** o app lembra se você prefere **Vertical** ou **Horizontal** ao sair e voltar.
- **Botões e filtros proporcionais:** visual mais compacto e elegante mantendo conforto de toque.

---

## v1.9.0 — Calendários Oficiais, Agenda Redesenhada & Acessibilidade

- **Calendários Acadêmicos Oficiais UFOP 2026.2, 2027.1 e 2027.2** integrados com suporte à Resolução CUNI 2945 e Calendário Presencial 2027.
- **Agenda redesenhada:** 7 dias da semana visíveis sem corte, card inteligente *Agora / Próxima aula*, aviso de período letivo/recesso e remoção do relógio analógico redundante.
- **Seletores nativos Material 3 de Data e Hora** com compensação de fuso horário UTC-3 (sem retroceder dias ao selecionar datas) e entrada por relógio ou teclado.
- **Grade Horária semanal interativa:** toque na aula para detalhes completos, filtro de horários vazios, modo compacto legível e suporte a rotação livre de tela.
- **Auditoria completa de Acessibilidade (IHC/WCAG AA no tema escuro):** contraste aprimorado, alvos de toque `>= 48dp`, navegação TalkBack aprimorada e confirmação com diálogo antes de excluir registros.

---

## v1.8.0 — Segurança, Acessibilidade & Novo Cadastro

- Criptografia local **AES-256-GCM (AndroidKeyStore)** protegendo dados acadêmicos em disco e remoção imediata de PDFs residuais após cada importação.
- Bloqueio opcional do aplicativo por senha e biometria (digital/facial), proteção contra capturas de tela (`FLAG_SECURE`) e bloqueio progressivo contra tentativas incorretas.
- Backup completo criptografado (`.ufopbackup`) com restauração no cadastro inicial ou em Configurações, e fluxo seguro de saída da conta com aviso de backup.
- Sistema global de Acessibilidade: perfil rápido TalkBack, escala de fonte (`100%` a `145%`), alto contraste WCAG, negrito reforçado, redução de movimento e áreas de toque de `56dp`.
- Cadastro inicial em 4 etapas: atestado com detecção automática de disciplinas EAD, envio opcional de Planos de Ensino (PDF) por disciplina, 7 cores de destaque (incluindo Amarelo, Ciano e Marrom) e acessibilidade.
- Tour Guiado animado pelas abas principais, nova área de FAQ & Ajuda e histórico de Notas de Atualização.

---

## v1.7.5 — Personalização, Widget & Matriz Legada

- Personalização de cores de destaque com persistência durável e sincronização imediata com o widget da tela inicial.
- Correção definitiva do widget acadêmico com atualização automática ao salvar aulas ou alternar tema.
- Reorganização do Perfil em sub-abas dedicadas: Métricas & CRE, Personalização e Configurações.
- Melhorias de legibilidade no Modo Claro (barra de status transparente com ícones escuros e contraste de interruptores) e proteção para Matriz Curricular Legada (`< 2026/1`).

---

## v1.7 — Catálogo UFOP, Matriz Curricular & Histórico

- Catálogo oficial com **54 cursos da UFOP** para seleção rápida e acompanhamento detalhado da matriz curricular.
- Importação de Histórico Escolar (PDF) com cálculo automático de CRE, carga horária integralizada e simulação de notas.
- Exportação e compartilhamento de grades horárias (`.ufopgrade`) com amigos e escolha de pasta de armazenamento.

---

## v1.6.5 — Atestado PDF, Planos de Ensino & Calendário

- Leitura inteligente do **Atestado de Matrícula (PDF)** do Minha UFOP para importar disciplinas, prédios e grade semanal.
- Importação de avaliações a partir de Planos de Ensino (PDF) e Calendário Acadêmico oficial assinado (SHA-256).
- Controle completo de presenças, faltas e aulas canceladas com cores por departamento.
