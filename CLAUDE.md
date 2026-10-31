# Prisma Archers — contexto do projeto

Duelo de arqueiros 2D por turnos (estilo "Bowmasters"): o jogador puxa o dedo para trás, mira e solta;
bonecos de pano (ragdoll), tiro na cabeça = vitória na hora com câmera lenta.
Android, só Play Store. Kotlin + Jetpack Compose (tudo desenhado em `Canvas`, sem OpenGL, sem engine).
Dono: Eldo (EldoFarias). Comunicação em português. **Monetização só AdMob** (banner, intersticial, premiado) —
sem outras redes, sem assinatura, sem compras no app.

## Como compilar e rodar

- Cada push na `main` gera o APK em Releases → `teste` (`PrismaArchers-teste.apk`, `.github/workflows/android.yml`).
  Erros de compilação aparecem como anotações do job (`gh api repos/EldoFarias/prisma-archers/check-runs/<job>/annotations`).
- Debug usa sempre IDs de **teste** do AdMob (pacote `com.farias.prismaarchers.debug`); release lê os IDs reais dos
  secrets `ADMOB_*` e **falha** se faltar algum ou se for ID de teste. `applicationId` = `com.farias.prismaarchers`.
- Teste dos duelos na JVM (sem Android), da raiz (`t` = tabela de vitórias por fase com um robô "humano"):
  ```bash
  P=app/src/main/java/com/farias/prismaarchers/game
  CP=$(ls /opt/gradle/lib/*.jar | tr '\n' ':'); java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -cp "$CP" $P/Archery.kt $P/ArcheryGame.kt tools/DuelTest.kt -d /tmp/dt && java -cp "$CP:/tmp/dt" DuelTestKt t
  ```
- Atalhos só no debug: `adb shell am start -n com.farias.prismaarchers.debug/com.farias.prismaarchers.MainActivity --es screen game --ei level 12`
  (`home`, `chest`, `game`, `daily`, `shop`, `map`, `stats`).

## Regras do jogo (`game/Archery.kt`, puro Kotlin, testável na JVM)

- Unidades em metros, y para cima. Jogador em (0, 0) virado para a direita; inimigo em (`distance`, `heightDiff`)
  virado para a esquerda, cada um numa plataforma de pedra (`Platform`).
- `Ragdoll`: 12 pontos (cabeça, pescoço, peito, quadril, cotovelos, mãos, joelhos, pés). Vivo = molas para a pose
  de mira (braço do arco no ângulo `aim`, mão de trás puxada por `draw`); morto = Verlet com gravidade, ossos rígidos
  e colisão com as plataformas. `hitTest` testa a ponta da flecha contra cabeça (círculo) e ossos (cápsulas).
- Dano (`Part`): cabeça 100 (mata), tronco 34, braço 18, perna 22. Capacete (`enemyHelmet`, chefes e às vezes a partir
  da fase 25): cabeça vale 50. Flecha do inimigo na cabeça do jogador = `PLAYER_HEAD_DAMAGE` (50). Escudo (vídeo)
  bloqueia o próximo acerto do inimigo.
- Flecha: velocidade `MIN_SPEED`..`MAX_SPEED` pela força, gravidade + vento (`spec.wind`, m/s², a partir da fase 6).
  `Duel.preview` dá a parte inicial da trajetória para a guia de pontos.
- IA (`planEnemyShot`): resolve o tiro perfeito (`solveShot`, busca em grade + refinamento) e soma um erro vertical
  gaussiano em metros que diminui com `enemySkill` e a cada tiro (o inimigo "aprende", até `ENEMY_LEARN_FLOOR`).
- `Levels.level(n)` determinístico: distância 13→27+ m, desnível, vento, vida e habilidade sobem até ~fase 80.
  Chefe a cada 10 (`isBoss`): mais vida, capacete, prêmio ×3. `daily(dia)` igual para todos no dia.
- Continuar por vídeo (`Duel.revivePlayer`) uma vez por fase: volta com 50 de vida.
- Flechas especiais (`ArrowKind`, estoque em `Progress.arrowStock`, 2 de cada para novatos; loja: pacote de 3; vídeo: +1):
  fogo (×1,5 e queima), tripla (3 em leque, ×0,6 cada), bomba (explode, dano em área `BLAST_*`), gelo (inimigo perde a vez).
  Várias flechas podem voar juntas (`Duel.inFlight`); o turno passa quando todas caem (`land`).
- Flecha rápida que mata atravessa (`PIERCE_SPEED`); capacete voa no 1º tiro na cabeça (`helmetOn`).
- Chefes com habilidade (`BossPower`, pela armadura): cavaleiro = bolha que aguenta 2 acertos, samurai = mira melhor,
  viking = 2 flechas, rei = se cura a cada turno.
- Torres com base: cada mundo tem `World.base` (`TowerStyle`: penhasco, mesa, montanha, prédio, castelo, pedra) e
  `World.ground` (`GroundStyle`: mar, rua com carros, lava, areia, neve, grama); o chão é a 3ª plataforma do `Duel`.
- Posições variadas por fase (`LevelSpec.playerH`/`enemyH`, altura de cada plataforma acima do chão): torres de alturas
  diferentes, um arqueiro no chão e o outro no alto, ou os dois embaixo (a diferença cresce com a fase). Sobre água/lava
  (`Duel.liquid`), quem está "no chão" fica numa pedra baixa (`LOW_ROCK`). Largura das plataformas também varia.
- Água/lava: o chão não é sólido; corpo que cai espirra (`ArcheryGame.splash`: gotas, coluna, névoa, ondas circulares
  `ripples`), freia e afunda devagar (`Ragdoll.waterY`), com bolhas; a água da frente esconde o corpo aos poucos.
- Golpe que não mata: reação de dor (`Ragdoll.hurtBy`: inclina para trás, solta o arco, mão no ferimento, olhos
  apertados) e empurrão para trás (`knockFor`, menor no jogador). Quem passa da beirada de uma plataforma alta cai e
  morre (`checkFalls`, evento `Fell`, título "DESPENCOU!").
- Câmera do golpe final: segue a flecha até o impacto e acaba `KILL_CAM_AFTER` (1,3 s) depois, sempre no corpo.
- Sobrevivência (`GameHost.playEndless`): um inimigo atrás do outro (`survivalSpec(k)`), +25 de vida entre eles;
  recorde em `Progress.bestEndless` (inimigos derrotados).

## Arquitetura

| Arquivo | O que tem |
|---|---|
| `game/Archery.kt` | Física (`Ragdoll`, `Arrow`), `Duel` (turnos, acertos, IA), `Levels`. |
| `game/ArcheryGame.kt` | Câmera (panorama padrão, segue a flecha, câmera do golpe final com `Duel.predictKill`), câmera lenta, tremida, partículas (`Particles`, sem alocar), pássaros/balões bônus (`Prop`), capacete voando, gravação do replay (`Recorder`, 30 fps em arrays fixos), demo do menu. |
| `game/GameHost.kt` | Estado da partida para o Compose, sons, vibração, pontos, moedas, missões, conquistas, sobrevivência, títulos de combo (`TitleShow`), provocações (balão), nome/nível do inimigo, clima (`Weather`, relâmpago), flecha especial escolhida. Tudo na thread principal: `GameView` chama `frame(dt)` a cada quadro. |
| `ui/ArcheryPainter.kt` | `GameView` (laço com `withFrameNanos`, o Canvas só lê um `tick` → nada recompõe) e o desenho: céu, 2 camadas de cenário em `Path` cacheado por mundo, plataformas, arqueiros (roupas/chapéus/arcos), flechas, rastro, guia, barras de vida, marcadores fora da tela, vinheta da câmera lenta. |
| `data/Looks.kt` | 13 roupas (`Outfit`, chapéu `Hat`), 9 arcos (`Bow`, `BowStyle`), 8 mundos (`World`: céu, cenário, pedra, partículas). |
| `data/Achievements.kt`, `data/Trails.kt`, `data/Missions.kt`, `data/Progress.kt` | Conquistas, rastros das flechas, missões diárias e tudo que persiste. |
| `ui/` | Splash (arqueiro acerta o alvo), Home (sobre o duelo demo), Game (mira por arrasto, HUD, vento, pausa/derrota/vitória), Shop (roupas/arcos/rastros com prévia animada, prêmio surpresa, teste por vídeo), Map (mundos de 10 fases), Extras (conquistas, roleta), Stats, Rewards (missões, baú), Dialogs. |
| `ads/AdsManager.kt` | AdMob + UMP. Intersticial a cada 2 partidas e 90 s, nunca antes de 4 vitórias. |
| `audio/` | `SoundManager` (`DRAW`, `SHOOT`, `HIT`, `HEADSHOT`, `STICK`…), `MusicPlayer` (chill/sunny por mundo, epic em chefe e sobrevivência), `Haptics`. |

O `GameView` fica atrás de todas as telas; no menu roda a demo com dois robôs. Splash, loja, mapa e estatísticas
cobrem a tela inteira e pausam o desenho (`host.renderPaused`).

## Convenções

- Textos em 8 idiomas (en, pt, es, fr, de, tr, in, hi): editar `tools/strings.py` e rodar `python3 tools/strings.py app/src/main/res`.
- Sons: `python3 tools/make_sfx.py app/src/main/res/raw` (apague os .wav que o jogo não usa). Músicas:
  `python3 tools/make_music.py app/src/main/res/raw/music_<estilo>.ogg <chill|sunny|epic>`. Ícone e arte da loja:
  `python3 tools/make_art.py .`; textos da loja: `python3 store/listings.py`.
- Gradientes radiais com raio 0 derrubam o app: proteger tamanhos < 1 px.
- Em tablet o app inteiro é desenhado maior (`Scaled` no MainActivity). Tela cheia (sem barra de status) em todas as telas.
- **Desempenho é prioridade (o Eldo quer o jogo extremamente leve):** nada de alocar no laço de desenho (Paths,
  Strokes e textos ficam em cache no `ArcheryPainter`; partículas em arrays); o cenário é montado uma vez por mundo;
  "Cenário detalhado" nas configurações desliga a camada distante e as partículas do ar; animações do Compose leem
  o tempo só dentro de `graphicsLayer`/`drawWithContent`.

## Forma de trabalho

- Não tirar prints nem rodar no emulador: só publicar o APK (push na `main`); o Eldo testa no celular e manda prints.

## Pendências

1. Eldo testar no celular: sensação da mira (`FULL_POWER_DRAG` em `GameScreen.kt`), câmera, dificuldade, desempenho.
2. Prints da loja (o Eldo tira no celular). Ícone, gráfico e textos (8 idiomas) prontos.
3. Seguir `store/PUBLICAR.md`: keystore + secrets `KEYSTORE_*`, app e blocos no AdMob + secrets `ADMOB_*`, Play Console.
4. `docs/privacy.html` publicado no GitHub Pages (link em `ui/Dialogs.kt`).
