# Política de Segurança — Vida Acadêmica

O **Vida Acadêmica** é um aplicativo independente e de código aberto (**GNU GPLv3**), desenvolvido por estudantes para apoiar a organização acadêmica sem vínculo oficial com a UFOP.

---

## Versões Suportadas

| Versão / Canal | Status de Suporte |
|---|---|
| `1.9.x` (Beta Android · branch `develop`) | ✅ Suportada (atualizações de segurança ativas) |
| `1.0.x` (Estável futura · branch `main`) | ✅ Suportada |
| `< 1.9.0` | ❌ Não suportada — atualize para a versão mais recente |

---

## Como Reportar uma Vulnerabilidade

**Por favor, não abra Issues públicas** contendo detalhes técnicos ou passos para explorar vulnerabilidades de segurança.

Reporte qualquer falha em privado usando o canal oficial do repositório:

👉 **[Reportar vulnerabilidade em privado (GitHub Security Advisory)](https://github.com/NuncaMorto/Vida-Academica/security/advisories/new)**

Ao enviar seu relato, inclua quando possível:
- Descrição da vulnerabilidade e impacto potencial.
- Passos para reproduzir o problema (versão do app, sistema operacional ou arquivo envolvido).
- Sugestão de mitigação, caso tenha uma proposta.

Responderemos o mais rápido possível para validar, corrigir e coordenar a divulgação responsável após o lançamento da correção.

---

## Modelo de Segurança do Aplicativo

- **Arquitetura offline-first e zero telemetria:** dados acadêmicos ficam armazenados apenas no dispositivo do estudante, sem servidores proprietários nem rastreadores.
- **Criptografia local:** persistência e backups portáteis protegidos com **AES-256-GCM** e chaves isoladas no **AndroidKeyStore** / **iOS Keychain**, além de bloqueio biométrico/PIN opcional.
- **Processamento efêmero de PDFs:** Atestado de Matrícula, Planos de Ensino e Histórico Escolar são lidos exclusivamente em memória para extração de dados e descartados em seguida.
- **Atualizador interno verificado (Android):** downloads feitos exclusivamente via HTTPS em domínios confiáveis do GitHub (`NuncaMorto/Vida-Academica`), com checagem obrigatória de hash **SHA-256** antes de iniciar a instalação.
