# Thor Ergo 1.0

**Teclado tátil multilingue e trackpad para o ecrã inferior do AYN Thor com ArmadaOS.**

Fork independente de [Armada Dual Screen Input](https://github.com/sa1utyeggs/armada-dual-screen-input),
com interface ergonómica, teclas grandes e sete disposições de teclado.
**Design gráfico e disposição do teclado: seekingalphacrawler.**

[English](README.md) · [Descarregar a versão 1.0](https://github.com/seekingalphacrawler/Thor-Ergo/releases/tag/v1.0) · [Imagens da interface](docs/INTERFACE.md) · [Créditos](CREDITS.md)

![Teclado português e trackpad](docs/images/keyboard-pt.png)

*As imagens são renderizadas pelo código da aplicação. Mostram a área da app,
não fotografias de um Thor nem uma barra fictícia do Plasma. A fonte efetiva e
a altura disponível dependem da sessão gráfica do dispositivo.*

## Ambiente testado

| Componente | Configuração testada |
|---|---|
| Computador usado na instalação | **Windows 11** |
| Dispositivo | **AYN Thor Pro — 12 GB de RAM** |
| Sistema operativo | **ArmadaOS 20261109.dfed5d6** (edição 20261109) |
| Ecrã superior | Steam Game Mode iniciado e desbloqueado |
| Ecrã inferior | **Plasma Mobile ativo**, através do Armada Control |

**Os instaladores macOS e Linux estão incluídos, mas não foram testados pelo
autor em computadores com esses sistemas.** Outros modelos/versões do SO e
todas as combinações de idiomas e jogos não foram extensivamente testados.

Este é um projeto pessoal concluído, disponibilizado tal como está, sem
compromisso de manutenção ativa, atualizações ou apoio técnico. São bem-vindos
forks da comunidade. [Âmbito dos testes e apoio](SUPPORT.md).

## Funcionalidades

- Letras grandes, trackpad fixo no canto inferior direito, botões de rato,
  páginas de números/símbolos/Fn e setas nos separadores alternativos.
- Legendas minúsculas/maiúsculas de acordo com Shift e Caps Lock. Mantém uma
  letra elegível pressionada, desliza até à variante e levanta o dedo.
- Sete disposições e menus traduzidos, com escolha independente do idioma da
  interface ou correspondência automática com o teclado.
- Botão **AYN opcional**: um clique apaga o ecrã inferior e outro volta a
  acendê-lo, sem encerrar o Plasma Mobile nem suspender o Thor.
- Instalação completa na área do utilizador, por SSH, com diagnóstico e reversão.

## Descarregar e instalar

Descarrega **`Thor-Ergo-1.0.zip`** na página [Releases](https://github.com/seekingalphacrawler/Thor-Ergo/releases/tag/v1.0).
Escolhe esse anexo, não os arquivos automáticos **Source code** do GitHub.
Extrai a pasta inteira antes de executar qualquer instalador.

### Preparar o Thor

Ativa o SSH, liga o Thor e o computador à mesma rede, guarda o trabalho e fecha
os jogos. Mantém o Thor acordado, sem botões pressionados.

**Ecrã superior: Steam Game Mode iniciado e desbloqueado. Ecrã inferior: Plasma
Mobile ativo através do Armada Control no ecrã superior. Não mudes todo o Thor
para Desktop Mode.** O instalador verifica a sessão inferior, mas não a ativa.

| Local da execução | Ficheiro ou comando |
|---|---|
| **Windows 11 — testado** | Duplo clique em `Install-Windows.cmd`, sem administrador |
| macOS — não testado pelo autor | No Terminal, dentro da pasta extraída: `sh Install-macOS.command` |
| Linux — não testado pelo autor | No terminal, dentro da pasta extraída: `sh Install-Linux.sh` |
| Diretamente no Thor, incluindo uma sessão SSH | Dentro da pasta extraída: `sh Install-on-Thor.sh` |

Os instaladores Windows/macOS/Linux instalam **no Thor por SSH**: não executam
o teclado no computador. No Windows são necessários PowerShell 5.1+ e o cliente
OpenSSH; macOS/Linux usam OpenSSH e ferramentas POSIX. Não é necessário Python
no computador usado para a instalação remota.

Escolhe **1 — Install / update**. Introduz o IP local, utilizador SSH
(predefinido: `armada`) e porta (predefinida: `22`). A indicação **SSH password
(default: "armada")** precede o pedido seguro do OpenSSH: escreve a palavra-passe
atual. O instalador não a guarda. Também é possível autenticar com chave SSH.
Confirma a identidade do dispositivo; chaves alteradas não são aceites
ou removidas automaticamente.

**Não é necessária uma aplicação ou patch anterior.** O ZIP contém a aplicação,
código-fonte, runtime privado GtkLayerShell, dados Pinyin e licenças. Não são
descarregados pacotes nem usados sudo, desbloqueio do SO ou alterações de
permissões. O instalador verifica uma cópia separada e exige confirmação de uma
primeira imagem; uma falha de ativação solicita reversão automática. Pode
conservar preferências compatíveis e a instalação existente para Undo.

## Idiomas

| Idioma | Disposição |
|---|---|
| Inglês (EUA) | QWERTY americano e respetiva pontuação |
| Português (Portugal) | QWERTY, ç e acentos portugueses |
| Espanhol (Espanha) | QWERTY, ñ, ¿, ¡ e vogais acentuadas |
| Francês (França) | AZERTY e acentos franceses |
| Alemão (Alemanha) | QWERTZ, ä, ö, ü e ß |
| Italiano (Itália) | QWERTY e vogais acentuadas |
| Chinês simplificado | Composição Pinyin offline e candidatos paginados |

Na roda dentada → **Idiomas**, a interface pode acompanhar o teclado ou ter
outro idioma. O Pinyin é uma implementação básica e offline, não um sistema de
previsão contextual; não aprende nem guarda o histórico de escrita.
A escrita não-US usa uma interface privada do Gamescope, cuja aceitação depende
da aplicação/jogo. As teclas físicas US pressupõem uma disposição US no destino.
O teclado global do sistema não é alterado.

## Menus

| Números e pontuação | Símbolos e acentos |
|---|---|
| ![Números](docs/images/numbers.png) | ![Símbolos](docs/images/symbols.png) |

| Teclas de função | Idiomas |
|---|---|
| ![Fn](docs/images/functions.png) | ![Idiomas](docs/images/settings-languages.png) |

| Definições do teclado | Definições do rato |
|---|---|
| ![Teclado](docs/images/settings-keyboard.png) | ![Rato](docs/images/settings-mouse.png) |

| Botão AYN | Sobre e créditos |
|---|---|
| ![AYN](docs/images/settings-ayn.png) | ![Créditos](docs/images/settings-about.png) |

O [guia visual](docs/INTERFACE.md) inclui imagens maiores, a seleção
de acentos e um exemplo de composição Pinyin.

## Botão AYN

Com jogos fechados e todos os botões soltos, abre **Definições → Botão AYN**
e ativa a opção. Fica desligada após a instalação. Aguarda **Pronto** e a
indicação de encaminhamento pelo InputPlumber. Um clique apaga o ecrã inferior;
outro volta a ligá-lo. O auxiliar funciona independentemente da janela do teclado.

Apenas o mapeamento do AYN é temporariamente encaminhado. Desativar a opção tenta
repor a função anterior. Desativa-a antes de mudar perfis de comando ou de
atribuir o mesmo botão a outra aplicação. A cópia privada de recuperação fica
no Thor e não deve ser publicada. Não são necessárias alterações de permissões,
leitura direta do dispositivo físico ou atalhos no Plasma.

## Reversão e diagnóstico

Abre novamente o instalador: **2 — Undo**, **3 — Diagnostics** ou **4 — Wake lower
screen**. Também existem os atalhos `Diagnose-AYN-*`. Undo repõe a instalação
imediatamente anterior e as preferências; numa instalação nova, retira os
serviços e lançadores criados. Revê os diagnósticos antes de os partilhar:
a saída recolhida no dispositivo pode conter nomes e caminhos locais.

Os ficheiros ficam em `~/.local/share/thor-ergo/`, `~/.config/thor-ergo/` e nos
serviços do utilizador, fora da imagem imutável do SO. Uma atualização normal
do Armada deverá conservá-los, mas **não se garante compatibilidade futura**.
Não foi validada uma atualização OTA real. Uma reinstalação que apague os dados
é uma situação diferente.

## Código e créditos

Código da app em `distribution/Thor/app/src/`, componentes de entrada em `distribution/Thor/app/vendor/`,
instaladores e testes incluídos. Testes isolados: `python3 -B distribution/Thor/selftest.py`,
num ambiente com as dependências de teste.

Consulta [CREDITS.md](CREDITS.md), [LICENSE](LICENSE) e os
[avisos de terceiros](distribution/Thor/app/THIRD_PARTY.md). Os autores e licenças originais
são preservados. Não é uma edição oficial de AYN, ArmadaOS, Valve ou dos
projetos de origem. **Design gráfico e disposição do teclado: seekingalphacrawler.**
