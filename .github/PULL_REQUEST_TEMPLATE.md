## Resumo das Alterações

<!-- Descreva de forma clara e objetiva o que este Pull Request faz e qual problema resolve. -->

## Tipo de Mudança

- [ ] Correção de bug (`fix`)
- [ ] Nova funcionalidade (`feat`)
- [ ] Melhoria de UI / Acessibilidade (`IHC/WCAG`)
- [ ] Segurança / Privacidade / Compliance (`security`)
- [ ] Documentação / Infraestrutura (`docs` / `chore`)

## Como Foi Testado

<!-- Informe quais comandos e cenários você validou localmente. -->
- [ ] Orquestrador local (`.\scripts\verify-all.ps1 -ChangedOnly` ou `-Full`)
- [ ] Rust (`.\scripts\verify-core.ps1` — `fmt`, `clippy`, `test`)
- [ ] Mapa 2D & UniFFI (`.\scripts\check-map-assets.ps1` / `.\scripts\sync-uniffi-bindings.ps1 -CheckOnly`)
- [ ] Android (`:app:testDebugUnitTest` / compilação Kotlin)
- [ ] iOS / Web / Scripts (`.\scripts\check-swift-hygiene.ps1`, `node --check docs/app.js`, se aplicável)
- [ ] Paridade Android ↔ iOS e notas de versão sincronizadas (`.\scripts\bump-version.ps1 -CheckOnly`)

## Checklist Obrigatório

- [ ] Este Pull Request aponta para a branch **`develop`** (ou é promoção controlada para `main` pelo mantenedor).
- [ ] Não inclui binários (`.apk`, `.aab`, `.pdf`, `.so`, `.a`, `.xcframework`), chaves (`.jks`, `.keystore`, `.pem`) nem `local.properties` / `.env`.
- [ ] Não expõe e-mails pessoais, tokens ou caminhos absolutos do computador local.
- [ ] Mantém a conformidade com a política de projeto **estudantil independente (sem vínculo oficial com a UFOP)**.
- [ ] Li e sigo a licença **GNU GPLv3** e as diretrizes em `.github/CONTRIBUTING.md`.
