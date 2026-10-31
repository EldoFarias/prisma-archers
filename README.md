# Prisma Archers

Jogo casual 2D para Android: duelos de arqueiros por turnos. Arraste o dedo para trás, mire e solte;
acerte o corpo para tirar vida ou a cabeça para vencer na hora, com câmera lenta e bonecos de pano.
Kotlin + Jetpack Compose (desenho em Canvas, sem engine), monetizado só com AdMob, publicado só na Play Store.

## O que tem

- Fases infinitas e determinísticas pelo número: distância, desnível, vento e pontaria do inimigo
  aumentam; duelo chefe com capacete a cada 10 fases (`Levels`).
- Física de ragdoll (Verlet) com flechas que ficam cravadas no corpo e no chão; IA que erra menos a
  cada tiro (`Duel`).
- Modo Sobrevivência: um inimigo atrás do outro.
- 8 mundos (campo, deserto, neve, vulcão, noite, sakura, castelo, selva), 13 visuais de arqueiro,
  9 arcos e 7 rastros de flecha, com prévia animada na loja.
- Desafio diário, baú de 7 dias, missões diárias, roleta, conquistas, mapa, recordes.
- Anúncios: banner adaptativo, intersticial entre partidas (nunca nas 4 primeiras vitórias), premiado
  para continuar, escudo, triplicar moedas e testar visuais; consentimento UMP (GDPR/LGPD).
- 8 idiomas: en, pt, es, fr, de, tr, id, hi.

## APK de teste sem PC

Cada `push` na `main` roda o GitHub Actions, que gera o APK com anúncios de teste e publica em
**Releases → "teste"** (`PrismaArchers-teste.apk`). É só baixar no celular e instalar.

## Antes de publicar

Configure em *Settings → Secrets and variables → Actions*:

| Secret | Para quê |
|---|---|
| `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | Assinar o AAB de produção |
| `ADMOB_APP_ID`, `ADMOB_BANNER_ID`, `ADMOB_INTERSTITIAL_ID`, `ADMOB_REWARDED_ID` | IDs reais do AdMob |
