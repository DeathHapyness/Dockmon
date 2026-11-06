# Guardião do Servidor: design

Data: 2026-11-05 · Aparelho: Moto C Plus XT1726

## Intenção

Transformar o Moto C Plus num aparelho dedicado: um "bichinho" que vive na tela e expressa, com emoções, o estado do servidor doméstico do usuário. Arrastando para o lado, o usuário vê e controla a música que está tocando no Spotify. O aparelho não deve parecer um Android: nada de launcher, barras ou tela de bloqueio.

Ele não é um pet para cuidar. O usuário não alimenta, não faz carinho e não brinca. O bichinho reage ao servidor e tem vida própria.

## Objetivos

- Tela principal sempre ocupada pelo bichinho.
- Reações a eventos do servidor:
  - Container caiu: **susto**. Se ele continuar fora, **preocupado/triste** até voltar.
  - Container voltou: **comemora**.
  - Container novo: **fogos de artifício**.
  - CPU alta, disco quase cheio, temperatura alta: expressões de desconforto (bravo, assustado, dramático, conforme o caso).
  - Tudo bem: contente, calmo.
- Vida própria: comportamentos aleatórios sem motivo (olhar para os lados, piscar, se mexer, bocejar, ficar curioso, observar). Quanto mais estável o servidor, mais relaxado. Com problema ativo, as manias param e ele fica focado no problema.
- Tela de música (arrastar para o lado): capa, título, artista e botões de voltar, pausar/tocar e pular. Ao fundo, barras estilo cava.
- Leveza máxima no aparelho de 900 MB de RAM.

## Não-objetivos

- Sono, bolinha, cutucar, arrastar, jogar, carinho, caçar e aprendizado/rede neural.
- Qualquer software no PC do usuário (a música não pode depender do PC).
- Trocar as telas de boot (logo e bootanimation da Motorola). Isso é a fase 2, separada, e exige desbloqueio do bootloader.
- Google Play Services (estão desativados no aparelho).

## Anti-objetivos (falharia mesmo funcionando)

- Expor a interface Android (barra de status, navegação, notificações, launcher, lockscreen) em uso normal.
- Deixar o celular pesado ou travando.
- Falso alarme constante: o bichinho não pode se assustar por oscilações momentâneas.
- Abrir acesso administrativo ao servidor na rede (por exemplo, a API do Docker exposta).

## Restrições

- Android 7.0 (API 24), armeabi-v7a, 900 MB de RAM, 720×1280 a 320 dpi, sem GMS, bootloader bloqueado.
- Instalação apenas via `adb`.
- O servidor fica na mesma rede Wi-Fi e já roda o Glances.
- Spotify: conta **Premium** (necessária para controlar a reprodução pela Web API).
- A estética é **inspirada** no pestinha (GPL-3), sem copiar código nem assets.

## Abordagens consideradas

**Fonte de dados do servidor:**
1. **Glances já existente, API REST v4: escolhida.** Custo zero a mais no servidor e nenhum código nosso lá.
2. Container próprio mínimo (Go, docker.sock somente leitura): fica como plano B se o Glances não fornecer algum dado (por exemplo, temperatura).
3. API do Docker exposta na rede: descartada. Dá acesso root e não tem temperatura.

**Tecnologia do app:**
1. **Kotlin nativo com View customizada desenhando em Canvas: escolhida (decisão do Claude).** É o menor consumo de RAM e CPU.
2. Flutter: descartado, consome RAM demais.
3. App web dentro de WebView: descartado, também pesado.

**Música:**
1. **Spotify Web API direto do celular: escolhida.** O usuário vetou software no PC.
2. Agente no PC (MPRIS + cava): descartado pelo usuário.

**Barras estilo cava:** microfone do celular com FFT. Reage ao som do ambiente. Se o usuário estiver de fone, as barras ficam em animação decorativa, mais baixa.

## Decisões do usuário

- Visual: telinha escura de cantos arredondados, com **moldura fina (4 px) e olhos brancos** (não neon).
- Orientação: **horizontal** (o celular fica em pé deitado), girando para os dois lados (`sensorLandscape`). As emoções aparecem pelo formato dos olhos, como no pestinha.
- Containers: susto ao cair, comemoração ao voltar, fogos quando aparece um novo.
- Também monitorar CPU alta, disco e temperatura.
- Reações aleatórias "sem motivo".
- Música acessível arrastando para o lado; o foco é sempre o bichinho.
- Usar o Glances existente.

## Design

### Telas

Um `ViewPager` com duas páginas, ou um gesto equivalente mais leve, numa única Activity.

1. **Guardião (página inicial):** fundo escuro e a "telinha" com olhos brancos, grande e centralizada. Ela flutua levemente. Uma linha de texto discreta embaixo mostra o motivo do humor atual (por exemplo, "nginx caiu", "CPU 93%"). Fogos e efeitos ocupam a tela inteira.
2. **Música:** capa grande, título e artista, botões ⏮ ⏯ ⏭. Atrás, barras brancas translúcidas no estilo cava. Sem música tocando, mostra "nada tocando". Depois de **60 s** sem toque, a tela volta sozinha para o Guardião (decisão do Claude, para manter o foco no bichinho).

### Motor de emoções

- Estados: CONTENTE, CALMO/OBSERVANDO, CURIOSO, ASSUSTADO, PREOCUPADO/TRISTE, BRAVO, DRAMÁTICO, COMEMORANDO, FOGOS.
- Prioridade:
  1. Evento novo (susto, comemoração, fogos): animação de 3 a 6 s.
  2. Problema ativo: humor contínuo.
  3. Ocioso: vida própria.
- Vida própria: a cada 4 a 20 s (aleatório), escolhe uma mania. Olhar para a esquerda ou direita, piscar, piscar devagar, deslizar alguns pixels, inclinar, bocejar, olhar para cima. Pesos variam conforme a estabilidade recente.
- Transições: os olhos interpolam o formato entre expressões (cerca de 150 a 300 ms), sem cortes.

### Detecção de eventos (polling do Glances)

- O app consulta o Glances a cada **5 s** (decisão do Claude), usando a API **v4**:
  - `/api/4/containers`: nome e status;
  - `/api/4/cpu` ou `/api/4/quicklook`: CPU;
  - `/api/4/fs`: disco;
  - `/api/4/sensors`: temperatura.
- **Instância real verificada (2026-11-05):**
  - `http://192.168.15.6:61208`, Glances **4.5.6**, sem autenticação, cerca de 35 containers Docker.
  - Status vistos: `running` e `healthy`. Campos usados: `name`, `status`, `uptime`.
  - O próprio container do Glances aparece na lista.
- Status **no ar**: `running` ou `healthy`.
  - `unhealthy` não conta como queda: é um humor contínuo de **preocupado/doente** com aquele container.
  - Qualquer outro status (`exited`, `restarting`, `dead`, `paused`) ou sumir da lista conta como **caído**.
- Container **caiu**: estava no ar e passou a caído. Confirmado em **2 leituras seguidas** para evitar falso alarme.
- Container **voltou**: estava caído e voltou a ficar no ar.
- Container que sumiu da lista e não volta em **10 min** é considerado **removido de propósito** (decisão do Claude). O bichinho deixa de se preocupar com ele, e ele sai da lista de conhecidos. Se reaparecer, é tratado como novo e solta fogos.
- Container **novo**: nome que nunca foi visto antes. A lista de nomes conhecidos é persistida. Na primeira execução, todos são marcados como conhecidos, sem fogos.
- **CPU alta:** acima de 85% por 60 s ou mais (decisão do Claude).
- **Disco:** acima de 90% em qualquer ponto de montagem (decisão do Claude).
  - Hoje o Glances, por rodar em container, só enxerga o disco do sistema (cerca de 17%).
  - Para enxergar outros discos (por exemplo, mídia do Jellyfin), o usuário precisa montar o `/` do host no container do Glances (`-v /:/rootfs:ro`). Opcional; o app usa o que vier em `/fs`.
- **Temperatura:** usa os limites que o próprio Glances informa por sensor.
  - `warning` (74 °C nos núcleos) deixa o bichinho incomodado.
  - `critical` (94 °C) deixa ele dramático.
  - Sensores sem limite usam 80 °C como padrão.
- Todos os limites ficam numa tela de configuração.
- **Servidor inacessível:** o Glances não responde por mais de 30 s. O bichinho fica "procurando" (olhos semicerrados, olhando em volta) e o texto mostra "sem contato com o servidor". Não há susto, porque pode ser o Wi-Fi do celular.

### Spotify

- O usuário cria um app no Spotify Developer Dashboard, uma única vez, e informa o Client ID.
- Login: Authorization Code com PKCE, aberto numa WebView dentro do app, com redirect URI usando um esquema próprio (`guardiao://callback`). O refresh token fica salvo de forma persistente.
- Escopos: `user-read-currently-playing`, `user-read-playback-state`, `user-modify-playback-state`.
- Polling de `/v1/me/player/currently-playing` a cada 3 s, **apenas com a tela de música visível**. Com a tela do Guardião visível, nada é consultado.
- Controles: `POST /v1/me/player/previous|next`, `PUT /v1/me/player/play|pause`.
- A capa é baixada uma vez por faixa, no tamanho de 300 px.

### Barras (microfone)

- `AudioRecord` a 22 kHz mono e FFT pequena (512 pontos), agrupada em cerca de 24 barras com suavização de queda no estilo cava.
- Ativo **somente** com a tela de música visível.
- A permissão de microfone é concedida automaticamente pelo Device Owner.

### Modo dispositivo dedicado (kiosk)

- O app é **Device Owner**, configurado via `adb shell dpm set-device-owner`. O aparelho não tem contas, então isso é possível.
- Ele também é a Activity HOME padrão, roda em lock task (`setLockTaskPackages`) e usa:
  - `setStatusBarDisabled(true)` e `setKeyguardDisabled(true)`;
  - modo imersivo sticky;
  - `FLAG_KEEP_SCREEN_ON`.
- Brilho baixo fixo e configurável (decisão do Claude: o celular fica ligado na tomada como display).
- Depois de validar, o launcher da Motorola (`com.motorola.launcher3`) é desativado para economizar cerca de 23 MB.
- **Saída de emergência (decisão do Claude):** segurar o canto superior esquerdo por 5 s abre uma tela de configuração escondida com:
  - endereço e porta do Glances;
  - Client ID e login do Spotify;
  - limites de CPU, disco e temperatura;
  - brilho;
  - "sair do modo kiosk", que encerra o lock task e abre as Configurações do Android.
- Recuperação extra sempre disponível: `adb shell dpm remove-active-admin` (funciona para o próprio Device Owner em modo de teste, via `testOnly` no manifest) ou reset de fábrica.

### Som

Sem som (decisão do Claude): o aparelho é um display silencioso. O sistema de som já está desativado nas configurações.

### Persistência

`SharedPreferences` guardam: configurações, containers conhecidos e o último estado de cada container, para não haver um "susto" falso ao reiniciar.

### Leveza

- Uma Activity e Views customizadas.
- Sem bibliotecas pesadas: `HttpURLConnection` ou OkHttp, `org.json`, sem Compose, sem Glide.
- Animação a 30 fps no Guardião; a 15 fps quando parado em CALMO.
- Meta: abaixo de 40 MB de PSS.

## Deixado para quem for construir

- Os desenhos exatos de cada expressão dos olhos, inspirados no conjunto do pestinha, em branco.
- O desenho dos fogos e da comemoração.
- A escolha entre ViewPager ou um swipe próprio.
- Os nomes exatos dos campos do Glances v4, verificados contra a instância real do usuário.
- A forma de embutir a configuração inicial (Glances e Spotify) no primeiro uso.

## Fora deste design (fase 2, separada)

Trocar o logo de boot e a bootanimation. Exige desbloqueio do bootloader, que é incerto no XT1726, ou TWRP portado. Terá design próprio e autorização explícita a cada passo.
