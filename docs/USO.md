# Guardião: guia de uso

O Moto C Plus funciona como um display dedicado. São três telas: o painel do servidor à esquerda, o rosto no meio (onde ele começa) e a música do Spotify à direita.

## No dia a dia

- **O rosto** reage sozinho:
  - container caiu: susto, depois preocupado;
  - container voltou: comemora;
  - container novo: fogos;
  - CPU alta, disco cheio ou temperatura alta: fica bravo ou dramático;
  - servidor sem resposta: fica procurando;
  - tudo bem: tem manias aleatórias, incluindo os olhos de coração.
  - Tocar nele: olha para o dedo e diz "oi!". Dois toques são cócegas; de 3 em diante ele fica bravo e, a partir de 5, dramático. Depois de 3 s sem toque ele esquece.
  - O humor muda com a hora:
    - manhã (6h–12h): agitado, mexe muito e dá pulinhos;
    - tarde (12h–18h): vai ficando cansado, com sono cada vez mais frequente;
    - noite (18h–6h): calmo, olhos tranquilos e poucas manias.
- **Arrastar para a direita (painel):** CPU, RAM, disco e temperatura, gráfico da CPU na última hora, tempo ligado e a lista de containers, com os que têm problema primeiro (vermelho = fora, âmbar = não está bem). Embaixo fica a escolha de **tema**: a cor vale para o rosto, a música e o painel.
- **Arrastar para a esquerda (música):** tela de música com capa e botões ⏮ ⏯ ⏭. As barras estilo cava simulam um ritmo próprio de cada música enquanto ela toca e param no pause. Elas não seguem a batida real, porque o áudio fica no fone do PC. Depois de 60 s sem toque, qualquer tela volta para o rosto.
- **Configurações escondidas:** segure o **canto superior esquerdo por 5 s**. Ali ficam a URL do Glances, os limites de CPU, disco e temperatura, o brilho e o botão "Sair do modo kiosk".

## Pelo PC (com o celular no USB)

Antes de tudo: `. .tools/env.sh`

| Tarefa | Comando |
|---|---|
| Compilar e testar | `./gradlew :app:testDebugUnitTest :app:assembleDebug` |
| Instalar uma versão nova | `adb install -r -t app/build/outputs/apk/debug/app-debug.apk` |
| Configurar do zero (instalar e virar dono do aparelho) | `./device/provisionar.sh adb` |
| Reconectar o Spotify | `python3 device/spotify-login.py adb` e abrir o link no navegador do PC |
| Tirar o modo dedicado | `./device/remover-dono.sh adb` |
| Reativar os apps desativados | `./device/restaurar-apps.sh adb` |

O app de desenvolvedor do Spotify precisa ter `http://127.0.0.1:8888/callback` cadastrado em **Redirect URIs**.

## Limites conhecidos

- O logo e a animação de boot da Motorola continuam aparecendo, porque o bootloader está bloqueado.
- O Glances roda em container, então só enxerga o disco do sistema. Para ver outros discos, monte `-v /:/rootfs:ro` nele.
- As barras são simuladas: o Spotify não fornece mais a análise de batida para apps novos e o celular não ouve o fone.
