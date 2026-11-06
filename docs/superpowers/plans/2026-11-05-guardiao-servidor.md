# Guardião do Servidor: plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** um app Android (Kotlin) que transforma o Moto C Plus num display dedicado. Um rosto de dois olhos brancos reage ao servidor (via Glances), e uma segunda tela, aberta com swipe, mostra e controla o Spotify com barras estilo cava.

**Architecture:**
- Lógica pura e testável em JVM: parsing do Glances, detector de eventos, motor de humor, PKCE e espectro.
- Ela é separada da camada Android: Views em Canvas, polling em `HandlerThread`, kiosk via Device Owner.
- Uma única `MainActivity` com um layout de duas páginas próprio, mais uma `SettingsActivity` escondida.

**Tech Stack:**
- Kotlin 2.0.21, Android Gradle Plugin 8.7.3, Gradle 8.11.1, JDK 21 (sdkman, já instalado).
- compileSdk/targetSdk 35, minSdk 24.
- Sem dependências de runtime além do `androidx.core:core-ktx:1.13.1`.
- Testes: JUnit 4.13.2 e `org.json:json:20240303` (só nos testes, porque o org.json do android.jar é stub).

**Spec:** `docs/superpowers/specs/2026-11-05-guardiao-servidor-design.md`

## Global Constraints

- Aparelho: Android 7.0 (API 24), armeabi-v7a, 900 MB de RAM, 720×1280 a 320 dpi, sem GMS. Instalação só por `adb`.
- **Tudo dentro de `/home/rique/Projects/finware-celular`**:
  - SDK em `.tools/android-sdk`;
  - Gradle e caches em `.tools/gradle-home`, com `GRADLE_USER_HOME` apontando para lá;
  - downloads em `.tools/downloads`;
  - capturas de tela e arquivos temporários em `.tmp/`.
  - Tudo isso fica no `.gitignore`.
  - `adb`: `.tools/android-sdk/platform-tools/adb`.
  - Exportar antes de cada comando: `export ANDROID_HOME=$PWD/.tools/android-sdk GRADLE_USER_HOME=$PWD/.tools/gradle-home`.
- **Git:** o usuário faz todos os commits e pushes, num repositório privado no GitHub. O implementador **nunca** roda `git commit`, `git push` nem `git init`; só sugere a mensagem e pausa.
- **Sem comentários desnecessários no código.** Só comentar o que o código não consegue dizer sozinho (por exemplo, o porquê de um limite). Nada de comentários que repetem o que a linha faz, de KDoc em tudo, nem de banners.
- Pacote: `dev.rique.guardiao`. Nome do app: "Guardião".
- Glances padrão: `http://192.168.15.6:61208`, API v4, sem autenticação.
- Visual: fundo `#0B0B0D`, moldura e olhos **brancos** `#FFFFFF`, cantos arredondados.
- Proibido: Compose, Glide/Coil, Retrofit, coroutines. Usar `HttpURLConnection`, `org.json`, `Handler`/`HandlerThread`.
- Meta de memória: PSS do app **< 40 MB** em `dumpsys meminfo dev.rique.guardiao`.
- Sem som.
- Textos de UI em português, exatamente os definidos na Task 4.
- O manifest declara `android:testOnly="true"`, para que o Device Owner possa ser removido via `adb shell dpm remove-active-admin`.

## Review Focus

1. **Glances fora do ar, lento (> 3 s) ou com JSON inválido:** o rosto vai para PROCURANDO depois de 30 s, sem crash e sem "susto". Testes na Task 2 (`parse_invalid_json_returns_null`) e na Task 3 (`unreachable_after_30s_is_problem_not_scare`).
2. **Reinício do app com container já caído:** não pode gerar susto, comemoração nem fogos falsos. O estado é persistido. Teste na Task 3 (`restored_state_does_not_reemit_events`).
3. **Vários containers caem de uma vez** (por exemplo, o Docker reinicia): um único susto, com legenda "5 containers caíram". Teste na Task 4 (`many_downs_single_scare_with_count`).
4. **Spotify sem nada tocando (HTTP 204), token expirado (401) ou sem dispositivo ativo (404 nos controles):** nada de crash; a tela mostra "nada tocando" ou "abra o Spotify no PC". Teste na Task 8 (`parse_204_is_nothing_playing`, `control_404_maps_to_no_device`).
5. **Microfone indisponível ou permissão negada:** as barras caem no modo decorativo. Teste na Task 9 (`decorative_bars_when_no_samples`).

---

### Task 1: Toolchain, projeto e app vazio rodando no celular

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/wrapper/*`, `gradlew`, `.gitignore`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/dev/rique/guardiao/MainActivity.kt`
- Test: `app/src/test/java/dev/rique/guardiao/SanityTest.kt`

**Interfaces:**
- Produces: projeto que compila com `./gradlew :app:assembleDebug` e testa com `./gradlew :app:testDebugUnitTest`. Pacote `dev.rique.guardiao`.

- [ ] **Step 1:** Instalar o Android SDK em `.tools/android-sdk`, dentro do projeto.
  - Baixar `commandlinetools-linux-11076708_latest.zip` de `https://dl.google.com/android/repository/` para `.tools/downloads/` e extrair em `.tools/android-sdk/cmdline-tools/latest`.
  - Rodar `sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"` e aceitar as licenças com `yes |`.
  - Escrever `local.properties` com `sdk.dir=/home/rique/Projects/finware-celular/.tools/android-sdk`. O `.gitignore` deve conter `.tools/`, `.tmp/`, `build/`, `.gradle/`, `local.properties`, `*.apk` e `.idea/`.
- [ ] **Step 2:** Gerar o wrapper do Gradle 8.11.1.
  - Baixar `gradle-8.11.1-bin.zip` para `.tools/downloads/`, extrair em `.tools/` e rodar `gradle wrapper --gradle-version 8.11.1` na raiz.
- [ ] **Step 3:** Escrever os scripts Gradle.
  - Plugins: AGP 8.7.3 e `org.jetbrains.kotlin.android` 2.0.21.
  - `namespace`/`applicationId` = `dev.rique.guardiao`; `minSdk 24`, `targetSdk 35`, `compileSdk 35`; `jvmTarget 17`.
  - `abiFilters` não é necessário (não há código nativo).
  - `testImplementation("junit:junit:4.13.2")` e `testImplementation("org.json:json:20240303")`.
- [ ] **Step 4:** Escrever `SanityTest.sanity()` com `assertEquals(4, 2 + 2)` e uma `MainActivity` vazia com fundo `#0B0B0D`.
- [ ] **Step 5:** Rodar `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Esperado: `BUILD SUCCESSFUL`.
- [ ] **Step 6:** Rodar `.tools/android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n dev.rique.guardiao/.MainActivity`. Esperado: `Success` e tela preta no celular.
- [ ] **Step 7:** **Pausa para o usuário.** Ele cria o repositório privado no GitHub, faz `git init`, o primeiro commit (sugerir `chore: projeto Android do Guardião`) e o push. Conferir antes, com `git status --ignored`, que `.tools/`, `.tmp/`, `build/` e `local.properties` estão ignorados.

### Task 2: Cliente e parsing do Glances

**Files:**
- Create: `app/src/main/java/dev/rique/guardiao/server/ServerSnapshot.kt`
- Create: `app/src/main/java/dev/rique/guardiao/server/GlancesParser.kt`
- Create: `app/src/main/java/dev/rique/guardiao/server/GlancesClient.kt`
- Test: `app/src/test/java/dev/rique/guardiao/server/GlancesParserTest.kt`

**Interfaces:**
- Produces:
  - `data class ContainerInfo(val name: String, val status: String)`
  - `data class FsInfo(val mount: String, val percent: Double)`
  - `data class SensorInfo(val label: String, val celsius: Double, val warning: Double?, val critical: Double?)`
  - `data class ServerSnapshot(val containers: List<ContainerInfo>, val cpuPercent: Double, val fs: List<FsInfo>, val sensors: List<SensorInfo>)`
  - `object GlancesParser { fun containers(json: String): List<ContainerInfo>?; fun cpu(json: String): Double?; fun fs(json: String): List<FsInfo>?; fun sensors(json: String): List<SensorInfo>? }`. Cada função retorna `null` para JSON inválido.
  - `class GlancesClient(baseUrl: String) { fun fetch(): ServerSnapshot? }`
    - Faz GET em `/api/4/containers`, `/api/4/cpu/total`, `/api/4/fs` e `/api/4/sensors`.
    - Timeout de conexão e de leitura de 3000 ms.
    - Retorna `null` se a chamada de containers ou de CPU falhar. Se `fs` ou `sensors` falharem, usa lista vazia.

- [ ] **Step 1: Testes que falham.** Usar fixtures copiadas da instância real:
  - `parse_containers`: entrada `[{"name":"jellyfin","status":"healthy"},{"name":"n8n","status":"running"}]`. Esperado: 2 itens, `jellyfin`/`healthy`.
  - `parse_cpu`: `{"total": 18.3}` resulta em `18.3`.
  - `parse_fs`: `[{"mnt_point":"/etc/hosts","fs_type":"ext4","percent":17.6}]` resulta em `FsInfo("/etc/hosts",17.6)`.
  - `parse_sensors_only_temperature`: entrada `[{"label":"Core 0","type":"temperature_core","value":32,"unit":"C","warning":74,"critical":94},{"label":"acpitz 0","type":"temperature_core","value":29,"unit":"C","warning":null,"critical":null},{"label":"fan1","type":"fan_speed","value":1200,"unit":"R","warning":null,"critical":null}]`. Esperado: 2 itens; o segundo com `warning == null`.
  - `parse_invalid_json_returns_null`: `containers("<html>")` resulta em `null`.
- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest --tests '*GlancesParserTest'`. Esperado: FAIL (classes inexistentes).
- [ ] **Step 3:** Implementar `ServerSnapshot.kt`, `GlancesParser`, que mantém só sensores com `type` começando por `temperature` e `unit == "C"`, e `GlancesClient`.
- [ ] **Step 4:** Rodar de novo. Esperado: PASS.
- [ ] **Step 5:** **Pausa para o usuário commitar.** Listar os arquivos alterados, sugerir a mensagem `feat: parser e cliente do Glances` e esperar ele confirmar que fez o commit e o push. Nunca rodar `git commit` nem `git push`.

### Task 3: Detector de eventos do servidor

**Files:**
- Create: `app/src/main/java/dev/rique/guardiao/server/EventDetector.kt`
- Test: `app/src/test/java/dev/rique/guardiao/server/EventDetectorTest.kt`

**Interfaces:**
- Consumes: `ServerSnapshot` (Task 2).
- Produces:
  - `sealed class ServerEvent { data class Down(val names: List<String>); data class Up(val names: List<String>); data class New(val names: List<String>) }`
  - `sealed class Problem(val severity: Int)`. Severidade maior vence:
    - `TempCritical(label, celsius)` = 60
    - `ContainersDown(names)` = 50
    - `DiskFull(mount, percent)` = 40
    - `TempWarning(label, celsius)` = 30
    - `CpuHigh(percent)` = 20
    - `Unhealthy(names)` = 10
    - `Unreachable` = 5
  - `data class Thresholds(val cpuPercent: Double = 85.0, val cpuSustainMs: Long = 60_000, val diskPercent: Double = 90.0, val defaultTempC: Double = 80.0)`
  - `class EventDetector(var thresholds: Thresholds = Thresholds())` com:
    - `fun onSnapshot(s: ServerSnapshot, nowMs: Long): List<ServerEvent>`
    - `fun onFailure(nowMs: Long)`
    - `fun problems(nowMs: Long): List<Problem>`, em ordem de severidade decrescente
    - `fun exportState(): String` e `fun importState(json: String)`

**Regras** (copiadas do spec):
- No ar = `running` ou `healthy`.
- `unhealthy` gera o problema `Unhealthy`, não `Down`.
- Caído = qualquer outro status ou ausente da lista.
- **Primeiro snapshot** sem estado importado: todos viram "conhecidos" e no ar/caídos conforme o status. Não emite eventos.
- `Down` só depois de **2 snapshots seguidos** caído. `Up` quando um container confirmado como caído volta ao ar.
- `New`: nome nunca visto, emitido no primeiro snapshot em que aparece.
- Ausente por **600 000 ms**: o container é esquecido e sai de "conhecidos" e de `ContainersDown`. Se reaparecer, emite `New`.
- `CpuHigh` quando a CPU fica ≥ `cpuPercent` continuamente por ≥ `cpuSustainMs`.
- `DiskFull` quando qualquer `fs.percent` ≥ `diskPercent`.
- Temperatura:
  - `TempCritical` se `celsius >= critical`;
  - `TempWarning` se `celsius >= (warning ?: defaultTempC)`.
- `Unreachable` quando a última leitura com sucesso foi há ≥ **30 000 ms**. Nunca gera `ServerEvent`.

- [ ] **Step 1: Testes que falham**, usando um helper `snap(vararg c: Pair<String,String>, cpu=10.0)`:
  - `first_snapshot_emits_nothing`
  - `down_needs_two_consecutive_reads`: 1ª leitura `exited` resulta em `[]`; a 2ª resulta em `[Down(["x"])]`.
  - `healthy_counts_as_up`
  - `unhealthy_is_problem_not_down`
  - `recovered_emits_up`
  - `new_container_emits_new`
  - `missing_10min_is_forgotten_then_new_on_return`
  - `cpu_high_requires_60s`: 90% em t=0 não gera problema; em t=60 000 gera `CpuHigh`.
  - `disk_full_at_90`
  - `temp_uses_sensor_thresholds`: 75 °C com warning 74 gera `TempWarning`; 95 °C com critical 94 gera `TempCritical`.
  - `temp_default_80_when_no_threshold`
  - `unreachable_after_30s_is_problem_not_scare`: `onFailure` em t=0 e t=30 000 deixa `problems` contendo `Unreachable`, e nenhum `ServerEvent` é gerado.
  - `restored_state_does_not_reemit_events`: um detector com "x" caído e confirmado exporta o estado; um novo detector importa e recebe um snapshot ainda com "x" caído. Esperado: `[]` em eventos e `ContainersDown(["x"])` em problemas.
  - `problems_sorted_by_severity`
- [ ] **Step 2:** Rodar `--tests '*EventDetectorTest'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar `EventDetector`. O estado é um `Map<String, Track>` com `Track(status, downReads, confirmedDown, lastSeenMs)`, além de `cpuHighSinceMs` e `lastSuccessMs`. O export usa `org.json`.
- [ ] **Step 4:** Rodar de novo. Esperado: PASS.
- [ ] **Step 5:** **Pausa para o usuário commitar.** Listar os arquivos alterados, sugerir a mensagem `feat: detector de eventos do servidor` e esperar ele confirmar que fez o commit e o push. Nunca rodar `git commit` nem `git push`.

### Task 4: Motor de humor (prioridades, manias e legendas)

**Files:**
- Create: `app/src/main/java/dev/rique/guardiao/mood/Expression.kt`
- Create: `app/src/main/java/dev/rique/guardiao/mood/MoodEngine.kt`
- Test: `app/src/test/java/dev/rique/guardiao/mood/MoodEngineTest.kt`

**Interfaces:**
- Consumes: `ServerEvent`, `Problem` (Task 3).
- Produces:
  - `enum class Expression { CONTENTE, CALMO, OBSERVANDO, CURIOSO, ASSUSTADO, PREOCUPADO, BRAVO, DRAMATICO, COMEMORANDO, PROCURANDO, SONOLENTO }`
  - `enum class Effect { NONE, FIREWORKS, SHAKE, BOUNCE }`
  - `data class MoodState(val expression: Expression, val gazeX: Float, val gazeY: Float, val blink: Boolean, val offsetX: Float, val offsetY: Float, val tilt: Float, val effect: Effect, val caption: String)`
    - `gaze*` e `offset*` vão de -1 a 1; `tilt` é em graus, de -8 a 8.
  - `class MoodEngine(private val random: java.util.Random)` com:
    - `fun onEvents(events: List<ServerEvent>, nowMs: Long)`
    - `fun state(problems: List<Problem>, nowMs: Long): MoodState`

**Regras:**
- **Camada 1, eventos:** duram de 3 a 6 s, e o evento novo substitui o atual.
  - `Down` resulta em ASSUSTADO + SHAKE por 4000 ms.
  - `Up` resulta em COMEMORANDO + BOUNCE por 4000 ms.
  - `New` resulta em COMEMORANDO + FIREWORKS por 6000 ms.
  - Se vários chegarem no mesmo `onEvents`, a prioridade é `Down` > `New` > `Up`.
- **Camada 2, problema de maior severidade:**
  - `TempCritical` e `DiskFull` resultam em DRAMATICO;
  - `ContainersDown` e `Unhealthy` resultam em PREOCUPADO;
  - `TempWarning` e `CpuHigh` resultam em BRAVO;
  - `Unreachable` resulta em PROCURANDO.
  - Com problema ativo, **não há manias**, só o piscar.
- **Camada 3, ocioso:** expressão base CONTENTE.
  - A cada `4000 + random.nextInt(16000)` ms começa uma mania com duração de 1200 a 2500 ms. Opções:
    - olhar para a esquerda ou direita (`gazeX = ±0.8`);
    - olhar para cima (`gazeY = -0.7`);
    - CURIOSO;
    - OBSERVANDO;
    - SONOLENTO (bocejo);
    - deslizar (`offsetX` aleatório de ±0.3);
    - inclinar (`tilt = ±6`).
  - Se nenhum problema ocorreu nos últimos 10 min, o peso de CALMO e SONOLENTO dobra.
  - Piscar: a cada 2500 a 6000 ms, por 120 ms, em qualquer camada exceto quando a expressão é SONOLENTO.

**Legendas** (texto exato):
| Situação | Legenda |
|---|---|
| Tudo bem | `tudo tranquilo` |
| Down de 1 container | `<nome> caiu!` |
| Down de N containers | `<N> containers caíram!` |
| Up de 1 / N | `<nome> voltou!` / `<N> containers voltaram!` |
| New de 1 / N | `container novo: <nome>` / `<N> containers novos!` |
| ContainersDown de 1 / N | `<nome> fora do ar` / `<N> containers fora do ar` |
| Unhealthy de 1 / N | `<nome> não está bem` / `<N> containers não estão bem` |
| CpuHigh | `CPU em <p>%` |
| DiskFull | `disco <mount> em <p>%` |
| TempWarning / TempCritical | `<label> a <c> °C` |
| Unreachable | `sem contato com o servidor` |

Porcentagens e temperaturas arredondadas para inteiro.

- [ ] **Step 1: Testes que falham**, com `Random(42)`:
  - `idle_is_contente_with_caption`
  - `down_event_scares_for_4s`: em t+3999 é ASSUSTADO; em t+4001, com `ContainersDown` ativo, é PREOCUPADO.
  - `many_downs_single_scare_with_count`: `Down(["a","b","c","d","e"])` gera a legenda `5 containers caíram!`.
  - `new_event_fireworks_6s`
  - `down_beats_new_in_same_batch`
  - `problem_maps_to_expression`: um caso por tipo de `Problem`.
  - `no_quirks_while_problem`: com `CpuHigh` ativo por 60 s simulados (passo de 100 ms), a expressão é sempre BRAVO e `gazeX == 0`.
  - `idle_quirk_happens_within_20s`: em 20 s simulados, a expressão ou o olhar mudam ao menos uma vez.
  - `blink_occurs`
- [ ] **Step 2:** Rodar `--tests '*MoodEngineTest'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Rodar de novo. Esperado: PASS.
- [ ] **Step 5:** **Pausa para o usuário commitar.** Listar os arquivos alterados, sugerir a mensagem `feat: motor de humor do guardião` e esperar ele confirmar que fez o commit e o push. Nunca rodar `git commit` nem `git push`.

### Task 5: Rosto em Canvas (olhos, transições, fogos)

**Files:**
- Create: `app/src/main/java/dev/rique/guardiao/face/EyeShape.kt`
- Create: `app/src/main/java/dev/rique/guardiao/face/FaceView.kt`
- Create: `app/src/main/java/dev/rique/guardiao/face/Fireworks.kt`
- Test: `app/src/test/java/dev/rique/guardiao/face/EyeShapeTest.kt`

**Interfaces:**
- Consumes: `MoodState`, `Expression`, `Effect` (Task 4).
- Produces:
  - `data class EyeShape(val width: Float, val height: Float, val topCut: Float, val innerCut: Float, val outerCut: Float, val bottomArc: Float)`
    - Os campos vão de 0 a 1, relativos ao olho.
    - `topCut` corta em diagonal (bravo, triste); `bottomArc > 0` desenha o olho como um arco "^" (contente, comemorando).
  - `object EyeShapes { fun of(e: Expression): EyeShape; fun lerp(a: EyeShape, b: EyeShape, t: Float): EyeShape }`
  - `class FaceView(context: Context) : View` com `fun update(state: MoodState)`. A View anima sozinha via `postInvalidateOnAnimation`, a no máximo 30 fps, e 15 fps quando a expressão é CALMO ou SONOLENTO.

**Desenho:**
- A "telinha" é um retângulo de cantos arredondados, com cerca de 70% da largura e proporção 4:3, centralizado. Moldura branca de 10 px, interior `#141417`.
- Olhos brancos.
- Abaixo da telinha, a `caption` em branco a 60% de opacidade, 18 sp.
- `offset*` move a telinha em até 40 px; `tilt` rotaciona; `gaze*` move os olhos dentro da tela em até 18% da largura.
- SHAKE: tremor de ±12 px por 600 ms. BOUNCE: pulo de 3 ciclos.
- FIREWORKS: 5 explosões de cerca de 40 partículas brancas e cinza-claro, com gravidade, ao longo de 6 s, num pool fixo de 200 partículas.
- Transição entre expressões: 200 ms com `lerp`.
- Inspiração visual: as expressões do pestinha (`docs/imagens/expressoes_bryan_jorge.png` do repo dele), **sem copiar assets**.

- [ ] **Step 1: Testes que falham:**
  - `lerp_endpoints`: `lerp(a,b,0)==a` e `lerp(a,b,1)==b`.
  - `lerp_midpoint`
  - `every_expression_has_shape`: todas as `Expression.values()` retornam uma forma.
  - `contente_is_arc`: `of(CONTENTE).bottomArc > 0`.
  - `assustado_bigger_than_calmo`
- [ ] **Step 2:** Rodar `--tests '*EyeShapeTest'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar `EyeShapes`, `FaceView` e `Fireworks`. Pré-alocar `Paint`, `Path` e partículas, sem alocar nada em `onDraw`.
- [ ] **Step 4:** Rodar de novo. Esperado: PASS.
- [ ] **Step 5:** Tela de demonstração temporária na `MainActivity`, alternando as expressões a cada 2 s.
  - Instalar e capturar com `adb exec-out screencap -p > .tmp/face-<expr>.png`.
  - Conferir visualmente que as 11 expressões são distintas e legíveis.
  - Remover a demo.
- [ ] **Step 6:** **Pausa para o usuário commitar.** Listar os arquivos alterados, sugerir a mensagem `feat: rosto do guardião em canvas` e esperar ele confirmar que fez o commit e o push. Nunca rodar `git commit` nem `git push`.

### Task 6: Integração: polling, duas páginas e configurações escondidas

**Files:**
- Create: `app/src/main/java/dev/rique/guardiao/settings/Settings.kt`
- Create: `app/src/main/java/dev/rique/guardiao/settings/SettingsActivity.kt`
- Create: `app/src/main/java/dev/rique/guardiao/ui/TwoPageLayout.kt`
- Create: `app/src/main/java/dev/rique/guardiao/server/ServerPoller.kt`
- Modify: `app/src/main/java/dev/rique/guardiao/MainActivity.kt`
- Test: `app/src/test/java/dev/rique/guardiao/settings/SettingsTest.kt`

**Interfaces:**
- Consumes: `GlancesClient`, `EventDetector`, `MoodEngine`, `FaceView`.
- Produces:
  - `class Settings(prefs: SharedPreferences)` com as propriedades:
    - `glancesUrl` (padrão `http://192.168.15.6:61208`)
    - `cpuPercent` (85), `diskPercent` (90), `defaultTempC` (80)
    - `brightness: Float` (0.15)
    - `spotifyClientId: String?`, `spotifyRefreshToken: String?`
    - `detectorState: String?`
    - `fun thresholds(): Thresholds`
  - `class ServerPoller(settings: Settings, detector: EventDetector, onUpdate: (events: List<ServerEvent>) -> Unit)` com `start()` e `stop()`.
    - Roda numa `HandlerThread` a cada 5000 ms.
    - `onUpdate` é entregue na main thread.
    - Persiste `detector.exportState()` depois de cada snapshot com sucesso.
  - `class TwoPageLayout(context: Context, first: View, second: View) : FrameLayout` com:
    - `var onPageChanged: ((Int) -> Unit)?`
    - `fun showPage(i: Int)`
    - Swipe horizontal com limiar de 25% da largura ou velocidade de fling, e animação de `translationX` em 250 ms.

- [ ] **Step 1: Teste que falha.** `settings_defaults_and_roundtrip`, usando um `SharedPreferences` falso em memória (implementar a interface no teste): os padrões batem com os listados acima, e gravar e ler `glancesUrl` funciona.
- [ ] **Step 2:** Rodar o teste. Esperado: FAIL.
- [ ] **Step 3:** Implementar `Settings` e rodar de novo. Esperado: PASS.
- [ ] **Step 4:** Implementar `ServerPoller` e `TwoPageLayout`, e montar a `MainActivity`.
  - Página 0: `FaceView`. Página 1: um placeholder escuro, que vira a música na Task 8.
  - Um tick de 100 ms na main thread chama `moodEngine.state(detector.problems(now), now)` e `faceView.update`.
  - O polling para no `onStop` e volta no `onStart`.
  - Na página 1, voltar sozinho à página 0 depois de 60 000 ms sem toque.
  - Segurar o canto superior esquerdo (área de 120×120 px) por 5000 ms abre a `SettingsActivity`.
- [ ] **Step 5:** Implementar a `SettingsActivity`, com layout programático simples sobre fundo escuro:
  - campos URL do Glances, CPU %, disco %, temperatura padrão e brilho (SeekBar);
  - botões "Salvar", "Conectar Spotify" (habilitado na Task 8), "Sair do modo kiosk" (habilitado na Task 7) e "Voltar".
- [ ] **Step 6: Verificação no aparelho.**
  - Instalar e abrir. Em até 10 s a legenda deve mostrar `tudo tranquilo`.
  - Pedir ao usuário um container descartável para teste, ou usar `docker stop`/`start` num container que ele indicar.
  - Conferir: susto, depois "<nome> fora do ar", e depois "<nome> voltou!".
  - **Não parar containers do usuário sem que ele autorize e indique qual.**
- [ ] **Step 7:** **Pausa para o usuário commitar.** Listar os arquivos alterados, sugerir a mensagem `feat: integra polling, páginas e configurações` e esperar ele confirmar que fez o commit e o push. Nunca rodar `git commit` nem `git push`.

### Task 7: Modo dispositivo dedicado (Device Owner + kiosk)

**Files:**
- Create: `app/src/main/java/dev/rique/guardiao/kiosk/AdminReceiver.kt`
- Create: `app/src/main/java/dev/rique/guardiao/kiosk/Kiosk.kt`
- Create: `app/src/main/res/xml/device_admin.xml`
- Modify: `app/src/main/AndroidManifest.xml`, `MainActivity.kt`, `SettingsActivity.kt`
- Create: `device/provisionar.sh`

**Interfaces:**
- Produces:
  - `object Kiosk { fun isOwner(ctx: Context): Boolean; fun apply(activity: Activity); fun exit(activity: Activity) }`
- `apply` faz, se for Device Owner:
  - `setLockTaskPackages([pkg])`
  - `setStatusBarDisabled(true)` e `setKeyguardDisabled(true)`
  - `addPersistentPreferredActivity` com filtro `MAIN`/`HOME`/`DEFAULT` para a `MainActivity`
  - `setPermissionGrantState(RECORD_AUDIO, GRANTED)`
  - `startLockTask()`
- `apply` faz, em qualquer caso:
  - modo imersivo sticky (`SYSTEM_UI_FLAG_IMMERSIVE_STICKY|HIDE_NAVIGATION|FULLSCREEN|LAYOUT_*`);
  - `FLAG_KEEP_SCREEN_ON`;
  - `screenBrightness = settings.brightness`.
- `exit` faz `stopLockTask()`, `setStatusBarDisabled(false)`, `setKeyguardDisabled(false)`, `clearPackagePersistentPreferredActivities` e abre `Settings.ACTION_SETTINGS`.

- [ ] **Step 1:** Manifest:
  - `android:testOnly="true"`;
  - a `MainActivity` com `intent-filter` HOME/DEFAULT/MAIN/LAUNCHER, `launchMode="singleTask"`, `screenOrientation="sensorLandscape"`, `excludeFromRecents`;
  - o receiver com `BIND_DEVICE_ADMIN` e `device_admin.xml` (políticas vazias bastam para o Device Owner);
  - permissões `INTERNET` e `RECORD_AUDIO`.
- [ ] **Step 2:** Implementar `Kiosk`. Chamar `apply` no `onResume` da `MainActivity`; ligar o botão "Sair do modo kiosk" a `exit`.
- [ ] **Step 3:** Escrever `device/provisionar.sh`, que recebe o caminho do adb como `$1`. Ele:
  - instala o APK;
  - roda `dpm set-device-owner dev.rique.guardiao/.kiosk.AdminReceiver`;
  - inicia o app.
- [ ] **Step 4:** Rodar o script. Esperado: `Success: Device owner set to package dev.rique.guardiao`.
- [ ] **Step 5: Verificação.** Reiniciar o celular (`adb reboot`), esperar `sys.boot_completed`, e capturar a tela com `screencap`. Esperado:
  - depois da animação de boot da Motorola, aparece direto o rosto;
  - sem barra de status, sem tela de bloqueio, sem launcher;
  - o botão Home e o de recentes não saem do app;
  - `dumpsys activity activities | grep mResumedActivity` mostra `dev.rique.guardiao`.
- [ ] **Step 6: Verificação da saída de emergência.** Segurar o canto por 5 s, tocar em "Sair do modo kiosk": as Configurações do Android abrem. Depois, `adb shell am start -n dev.rique.guardiao/.MainActivity` restaura o kiosk.
- [ ] **Step 7:** Desativar o launcher da Motorola com `adb shell pm disable-user --user 0 com.motorola.launcher3`. Acrescentar a linha em `device/apps-desativados.txt`. Reiniciar e repetir a verificação do Step 5.
- [ ] **Step 8:** **Pausa para o usuário commitar.** Listar os arquivos alterados, sugerir a mensagem `feat: modo dispositivo dedicado` e esperar ele confirmar que fez o commit e o push. Nunca rodar `git commit` nem `git push`.

### Task 8: Spotify (login PKCE, música atual, controles, tela)

**Files:**
- Create: `app/src/main/java/dev/rique/guardiao/music/Pkce.kt`
- Create: `app/src/main/java/dev/rique/guardiao/music/SpotifyApi.kt`
- Create: `app/src/main/java/dev/rique/guardiao/music/SpotifyLoginActivity.kt`
- Create: `app/src/main/java/dev/rique/guardiao/music/MusicView.kt`
- Modify: `MainActivity.kt`, `SettingsActivity.kt`, `AndroidManifest.xml`
- Test: `app/src/test/java/dev/rique/guardiao/music/SpotifyTest.kt`

**Interfaces:**
- Consumes: `Settings.spotifyClientId` e `spotifyRefreshToken`; `TwoPageLayout.onPageChanged`.
- Produces:
  - `object Pkce { fun verifier(random: SecureRandom): String; fun challenge(verifier: String): String; fun authUrl(clientId: String, challenge: String): String }`
    - O verifier tem 64 caracteres do alfabeto `[A-Za-z0-9-._~]`.
    - O challenge é base64url, sem padding, do SHA-256 do verifier.
    - A URL de autorização: `https://accounts.spotify.com/authorize?response_type=code&client_id=…&redirect_uri=guardiao%3A%2F%2Fcallback&code_challenge_method=S256&code_challenge=…&scope=user-read-currently-playing%20user-read-playback-state%20user-modify-playback-state`
  - `data class NowPlaying(val title: String, val artist: String, val coverUrl: String?, val isPlaying: Boolean)`
  - `sealed class PlayResult { data class Playing(val np: NowPlaying); object Nothing; object NoDevice; object AuthError; object NetError }`
  - `object SpotifyParser { fun nowPlaying(code: Int, body: String?): PlayResult; fun control(code: Int): PlayResult? }`
    - `control` devolve `null` em 2xx, `NoDevice` em 404 e `AuthError` em 401.
  - `class SpotifyApi(settings: Settings) { fun current(): PlayResult; fun play(); fun pause(); fun next(); fun previous() }`
    - O access token fica em memória e é renovado pelo refresh token (`POST https://accounts.spotify.com/api/token` com `grant_type=refresh_token&client_id=…`).
    - Em 401, renova uma vez e tenta de novo.
    - Se o Spotify devolver um `refresh_token` novo, ele é salvo.
  - `class MusicView(context: Context) : View` com `fun show(r: PlayResult, cover: Bitmap?)`, `var onControl: ((String) -> Unit)?` e `var bars: FloatArray`.

- [ ] **Step 1: Testes que falham:**
  - `challenge_known_vector`: verifier `dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk` gera o challenge `E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM` (vetor da RFC 7636).
  - `verifier_length_and_alphabet`
  - `auth_url_contains_scopes_and_redirect`
  - `parse_playing`: corpo com `{"is_playing":true,"item":{"name":"Song","artists":[{"name":"A"},{"name":"B"}],"album":{"images":[{"url":"big","width":640},{"url":"mid","width":300},{"url":"small","width":64}]}}}` gera `Playing(NowPlaying("Song","A, B","mid",true))`.
  - `parse_204_is_nothing_playing`
  - `parse_401_is_auth_error`
  - `control_404_maps_to_no_device`
- [ ] **Step 2:** Rodar `--tests '*SpotifyTest'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar `Pkce`, `SpotifyParser` e `SpotifyApi`. Rodar de novo. Esperado: PASS.
- [ ] **Step 4:** Implementar a `SpotifyLoginActivity`:
  - uma WebView abre `authUrl`;
  - `shouldOverrideUrlLoading` captura `guardiao://callback?code=…`;
  - troca o código pelo token (`POST /api/token` com `grant_type=authorization_code&code&redirect_uri&client_id&code_verifier`), salva o refresh token e fecha.
  - Ligar ao botão "Conectar Spotify" da `SettingsActivity`, que antes pede o Client ID num campo.
- [ ] **Step 5:** Implementar a `MusicView`:
  - capa de 300 dp centralizada, com cantos de 12 dp;
  - título em branco, 22 sp; artista em branco a 60%, 16 sp;
  - três botões desenhados (⏮ ⏯ ⏭) com área de toque mínima de 64 dp;
  - barras atrás de tudo, em branco a 18% de opacidade;
  - textos fixos: `nada tocando` para `Nothing`, `abra o Spotify no PC` para `NoDevice`, `reconecte o Spotify nas configurações` para `AuthError`, `sem internet` para `NetError`.
  - Na `MainActivity`, a página 1 visível inicia o polling de `current()` a cada 3000 ms numa `HandlerThread`. A capa é baixada só quando `coverUrl` muda, e o polling para ao sair da página.
- [ ] **Step 6: Verificação, com o usuário.**
  - Pedir que ele crie o app em `https://developer.spotify.com/dashboard`, com Redirect URI `guardiao://callback` e "Web API" marcada, e informe o Client ID.
  - Fazer login no celular, tocar música no PC e conferir capa, nome e os três botões.
- [ ] **Step 7:** **Pausa para o usuário commitar.** Listar os arquivos alterados, sugerir a mensagem `feat: tela de música do Spotify` e esperar ele confirmar que fez o commit e o push. Nunca rodar `git commit` nem `git push`.

### Task 9: Barras estilo cava pelo microfone

**Files:**
- Create: `app/src/main/java/dev/rique/guardiao/music/Spectrum.kt`
- Create: `app/src/main/java/dev/rique/guardiao/music/MicCapture.kt`
- Modify: `MainActivity.kt`
- Test: `app/src/test/java/dev/rique/guardiao/music/SpectrumTest.kt`

**Interfaces:**
- Consumes: `MusicView.bars` (Task 8).
- Produces:
  - `class Spectrum(val bars: Int = 24, val fftSize: Int = 512, val sampleRate: Int = 22050)` com:
    - `fun process(samples: ShortArray): FloatArray`: FFT radix-2 com janela de Hann, agrupamento logarítmico de 50 Hz a 10 kHz em `bars` faixas, valores de 0 a 1.
    - Queda estilo cava: cada barra cai no máximo 0.04 por quadro e sobe na hora.
    - `fun decorative(tMs: Long): FloatArray`: ondas senoidais lentas com amplitude máxima de 0.25.
  - `class MicCapture(onBars: (FloatArray) -> Unit)` com `start()` e `stop()`.
    - `AudioRecord` com `MIC`, 22050 Hz, mono, PCM16, numa thread própria, a cerca de 30 quadros/s.
    - Se o `AudioRecord` falhar ao iniciar, ou o nível ficar em silêncio por mais de 5 s (RMS < 0.01), passa a entregar `decorative`.

- [ ] **Step 1: Testes que falham:**
  - `sine_1khz_peaks_in_right_bar`: um seno de 1 kHz deixa a barra máxima dentro da faixa que contém 1 kHz.
  - `silence_gives_zero_bars`
  - `falloff_limited`: um pico seguido de silêncio cai exatamente 0.04 por quadro.
  - `values_in_0_1`
  - `decorative_bars_when_no_samples`: `decorative(t)` sempre fica entre 0 e 0.25 e muda com `t`.
- [ ] **Step 2:** Rodar `--tests '*SpectrumTest'`. Esperado: FAIL.
- [ ] **Step 3:** Implementar `Spectrum` e `MicCapture`. Rodar de novo. Esperado: PASS.
- [ ] **Step 4:** Na `MainActivity`, ligar o `MicCapture` só quando a página 1 estiver visível, passando o resultado a `musicView.bars`.
- [ ] **Step 5: Verificação no aparelho.** Com música alta no PC, as barras reagem. Com o PC em silêncio por 5 s, entram as barras decorativas. Conferir que, na página 0, o microfone está desligado: `dumpsys audio | grep -i "record"` sem gravação ativa.
- [ ] **Step 6:** **Pausa para o usuário commitar.** Listar os arquivos alterados, sugerir a mensagem `feat: barras estilo cava pelo microfone` e esperar ele confirmar que fez o commit e o push. Nunca rodar `git commit` nem `git push`.

### Task 10: Verificação final de leveza e estabilidade

**Files:**
- Modify: `device/apps-desativados.txt` (se necessário)
- Create: `docs/USO.md`. Explica como abrir as configurações escondidas, conectar o Spotify, sair do kiosk e reverter (remover o Device Owner, reativar os apps).

- [ ] **Step 1:** Reiniciar e deixar 10 min na tela do guardião. Rodar `adb shell dumpsys meminfo dev.rique.guardiao | grep "TOTAL"`. Esperado: TOTAL PSS < 40 000 K.
- [ ] **Step 2:** Rodar `adb shell top -n 1 | grep guardiao` na página 0. Esperado: CPU < 10%.
- [ ] **Step 3:** Ficar 5 min na página de música e repetir os Steps 1 e 2. Esperado: PSS < 40 MB.
- [ ] **Step 4:** Rodar `./gradlew :app:testDebugUnitTest`. Esperado: todos passam.
- [ ] **Step 5:** Escrever `docs/USO.md`. **Pausa para o usuário commitar**, com a mensagem sugerida `docs: guia de uso do guardião`.
