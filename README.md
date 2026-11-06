# Dockmon

Um bichinho virtual que mora num Moto C Plus antigo e vigia o servidor de casa.

<p align="center"><img src="docs/img/cocegas.png" width="640" alt="Zé Pingo sorrindo depois de levar cócegas"></p>

> **Feito por IA.** Todo o código, os testes, os scripts e esta documentação foram escritos pelo Claude (Anthropic), via Claude Code, a partir das ideias e dos testes no aparelho do autor do projeto.

O nome dele é **Zé Pingo**. Ele não come nem dorme por obrigação. Ele se assusta quando um container cai, comemora quando volta, solta fogos quando aparece container novo e fica bravo quando a CPU esquenta. Quando está tudo bem, olha para os lados, inclina a cabeça e às vezes fica apaixonado sem motivo nenhum.

O celular vira um display dedicado: liga direto no Zé Pingo, sem launcher, barra de status ou tela de bloqueio.

## As três telas

Arrastando para os lados:

| ← Painel | Zé Pingo (início) | Música → |
|---|---|---|
| CPU, RAM, disco e temperatura, gráfico da CPU na última hora, tempo ligado, lista dos containers com os problemáticos primeiro, escolha de tema | o rosto, que reage ao servidor e à hora do dia | o que toca no Spotify: capa, ⏮ ⏯ ⏭ e barras estilo cava |

Depois de 60 s sem toque, qualquer tela volta para o Zé Pingo.

<p align="center"><img src="docs/img/painel.png" width="640" alt="Painel do servidor com medidores e lista de containers"></p>

## Humor

**O que acontece no servidor:**

| Situação | Reação |
|---|---|
| Container caiu | susto (olhos vermelhos com glitch), depois preocupado |
| Container voltou | comemora |
| Container novo | fogos de artifício |
| CPU alta ou temperatura alta | bravo |
| Disco cheio ou temperatura crítica | dramático |
| Servidor sem resposta | fica procurando |
| Tudo bem | manias aleatórias, olhos de coração de vez em quando |

**Quando você toca nele** (ele olha para onde o dedo tocou):

| Toques seguidos | Reação |
|---|---|
| 1 | "oi!" e um pulinho (à noite: "hm? é de noite...", com sono) |
| 2 | "hehe, cócegas!" |
| 3–4 | "ei, para de me cutucar!", bravo |
| 5 ou mais | "socorro!", dramático |

<p align="center">
  <img src="docs/img/bravo.png" width="320" alt="Zé Pingo bravo">
  <img src="docs/img/socorro.png" width="320" alt="Zé Pingo dramático">
</p>

**A hora do dia:**
- **Manhã (6h–12h):** agitado, mexe toda hora e dá pulinhos.
- **Tarde (12h–18h):** vai cansando, com sono cada vez mais frequente.
- **Noite (18h–6h):** calmo, olhos tranquilos e poucas manias.

## Como funciona

- **Servidor:** o app lê a API do [Glances](https://github.com/nicolargo/glances) (`/api/4`) a cada 5 s. Nada precisa ser instalado no servidor além do Glances.
- **Música:** a API Web do Spotify com login PKCE, feito uma vez pelo PC. A música toca no PC e o celular só mostra e controla. As barras são simuladas a partir do andamento de cada faixa, porque o Spotify não fornece a análise de áudio para apps novos.
- **Modo dedicado:** o app é *Device Owner*. Usa lock task, desativa a barra de status e o bloqueio de tela e se define como HOME. 82 apps de fábrica foram desativados (lista em `device/apps-desativados.txt`).
- **Leve de propósito:** Kotlin puro com Canvas, sem Compose, coroutines ou bibliotecas de rede. Ocupa cerca de 15 MB de RAM e desenha a uns 13 fps quando está parado.

```
app/src/main/java/dev/rique/guardiao/
├── face/      rosto (Canvas), formatos dos olhos, fogos
├── mood/      motor de humor e ciclo do dia
├── server/    cliente e parser do Glances, detector de eventos
├── panel/     painel do servidor
├── music/     Spotify (PKCE, API, poller), tela e ritmo das barras
├── ui/        paginação por arraste e temas
├── settings/  preferências e tela de configuração escondida
└── kiosk/     modo dedicado (Device Owner)
```

## Hardware

Moto C Plus (XT1726), Android 7.0, MT6737M, cerca de 900 MB de RAM, tela 720×1280. O bootloader segue bloqueado, então o logo da Motorola ainda aparece no boot. Qualquer Android 7+ deve servir.

## Montando o seu

Requisitos: JDK 21, Android SDK (API 35) e um servidor com Glances acessível na rede.

1. **Compilar:**
   ```sh
   ./gradlew :app:testDebugUnitTest :app:assembleDebug
   ```
2. **Preparar o celular:** ative a depuração USB e remova todas as contas do aparelho. O Android só aceita definir um *Device Owner* sem contas.
3. **Instalar e tornar o app dono do aparelho:**
   ```sh
   ./device/provisionar.sh adb
   ```
4. **Configurar:** segure o canto superior esquerdo da tela por 5 s para abrir as configurações. Ali ficam a URL do Glances, os limites de alerta e o brilho.
5. **Spotify (opcional):**
   1. Crie um app em <https://developer.spotify.com/dashboard> com o redirect `http://127.0.0.1:8888/callback`.
   2. Coloque o Client ID nas configurações.
   3. Rode:
      ```sh
      python3 device/spotify-login.py adb
      ```

## Voltando ao normal

```sh
./device/remover-dono.sh adb     # sai do modo dedicado
./device/restaurar-apps.sh adb   # reativa os apps de fábrica
```

Também dá para sair pelo botão "Sair do modo kiosk" nas configurações.

Guia do dia a dia: [docs/USO.md](docs/USO.md).

## Licença

MIT
