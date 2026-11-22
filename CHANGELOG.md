# Changelog & Patch Notes — Vida Acadêmica UFOP

Todas as compilações instaláveis (Android `.apk` e iOS `.ipa` não assinado) ficam disponíveis na página de [Releases do repositório](https://github.com/NuncaMorto/UFOP-App/releases).

> **Sobre os formatos distribuídos:**
> - **Android (`Vida-Academica-UFOP-vX.Y.Z-debug.apk`):** APK direto para instalação no Android 8.0+.
> - **iOS (`Vida-Academica-UFOP-vX.Y.Z-unsigned.ipa`):** IPA compilado sem assinatura paga da Apple; instalável via AltStore, SideStore, Sideloadly, TrollStore ou Xcode.

---

## v1.9.6 — Filtro Multi-Tipo em Atividades, Disciplinas & Alinhamento

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
