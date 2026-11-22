# Como Contribuir — Vida Acadêmica

Obrigado pelo interesse em colaborar com o **Vida Acadêmica**, um aplicativo independente e de código aberto sob licença **GNU GPLv3**, feito por estudantes e **sem vínculo oficial com a Universidade Federal de Ouro Preto (UFOP)**.

---

## Fluxo de Contribuição e Controle de Revisão

Para manter a segurança, a qualidade técnica e a conformidade do projeto com as lojas de aplicativos:

1. **Contribuições externas acontecem via Fork + Pull Request:**
   - Faça um **Fork** deste repositório e crie uma branch descritiva a partir da **`develop`** (ex.: `fix/agenda-contraste` ou `feat/nova-matriz`).
   - Abra o **Pull Request apontando para a branch `develop`** (nunca direto para `main`).
2. **Aprovação obrigatória do mantenedor:**
   - Todo contribuidor externo precisa de **aprovação manual de `@NuncaMorto`** para executar os workflows de CI (`GitHub Actions`) no Pull Request.
   - Nenhum Pull Request pode ser integrado sem revisão e aprovação explícita do mantenedor (`CODEOWNERS`), resolução de todas as conversas e aprovação nos checks obrigatórios de qualidade e segurança.

---

## Verificações Obrigatórias Antes do PR

Execute os testes correspondentes às áreas que você alterou:

- **Núcleo Rust (`rust-core`):**
  ```powershell
  .\scripts\verify-core.ps1
  ```
  *(ou `cargo fmt --all -- --check`, `cargo clippy --workspace --all-targets -- -D warnings`, `cargo test --workspace`)*

- **Android (`android-app`):**
  ```powershell
  cd android-app
  .\gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin --no-daemon
  ```

---

## Regras de Privacidade, Segurança e Compliance

- **Nunca commite** binários de build (`*.apk`, `*.aab`, `dist/`), PDFs (`*.pdf`), arquivos locais (`android-app/local.properties`, `.env*`) ou chaves/keystores (`*.jks`, `*.keystore`, `*.p12`, `*.pem`, `*.key`).
- **Nunca inclua** e-mails pessoais, tokens, senhas ou caminhos absolutos locais com seu nome de usuário no código ou testes.
- **Compliance (App Store 5.2.1 & Google Play):** mantenha os textos de interface, metadados e assets livres de qualquer insinuação de vínculo oficial, endosso ou propriedade da UFOP, reforçando sempre o caráter estudantil independente.
- Ao enviar código ou correções, você concorda em licenciá-los sob a **GNU GPLv3** do repositório.
