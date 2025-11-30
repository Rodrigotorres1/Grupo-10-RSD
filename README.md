# Grupo 10 RSD - Protocolo de Transporte Confiável 

---

## 1. Introdução

Este projeto, desenvolvido em Java, implementa uma aplicação cliente-servidor que simula, na camada de aplicação, um protocolo de transporte confiável inspirado na lógica do TCP. A comunicação base é realizada por sockets TCP/IP, que são tratados como um canal não confiável para fins de simulação. Toda a lógica de confiabilidade, retransmissão, controle de fluxo e detecção de erros é construída manualmente dentro do código da aplicação.

O objetivo principal é demonstrar, de forma prática, o funcionamento dos mecanismos essenciais de Transporte Confiável e dos protocolos ARQ (Automatic Repeat Request), permitindo ao usuário testar a resiliência do sistema em cenários controlados de perda e corrupção de mensagens.

---

## 2. Protocolos e Mecanismos Implementados 

O núcleo do projeto é a implementação completa do modelo de Janela Deslizante (Sliding Window), suportando dois protocolos distintos:

### A. Modos de Operação (ARQ)

| Modo | Confirmação | Lógica de Retransmissão |
| :--- | :--- | :--- |
| **Go-Back-N (GBN)** | **ACK Cumulativo** (em grupo). | Retransmite todos os pacotes a partir da base em caso de falha ou timeout. |
| **Repetição Seletiva (SR)** | **ACK Individual** (pacote por pacote). | Retransmite apenas o pacote específico que não foi reconhecido. |

### B. Mecanismos Essenciais

* **Janela Deslizante:** O tamanho da janela é negociado e definido pelo servidor no handshake (valor aleatório entre 1 e 5).
* **Número de Sequência:** Utilizado para ordenar pacotes no servidor e controlar a janela.
* **Checksum:** Calculado pela soma dos valores de caracteres do payload original (módulo 256), para verificação de integridade.
* **Temporizador (Timeout):** Implementado no cliente, com um valor fixo de 2000ms, para forçar retransmissões em caso de perda de ACKs.

---

## 3. Simulação de Canal Não Confiável 

A aplicação permite a injeção manual e aleatória de falhas para testar o comportamento do protocolo:

| Tipo de Simulação | Local | Como Ocorre | Finalidade |
| :--- | :--- | :--- | :--- |
| **Corrupção Determinística** | Cliente | O usuário escolhe o SEQ do pacote para corromper (erro no checksum). | Testar o **NACK** e a **retransmissão**. |
| **Perda Determinística** | Cliente | O usuário lista os SEQs dos pacotes de dados que o cliente deve *dropar* (não enviar). | Testar a **retransmissão por *timeout***. |
| **Perda Probabilística de ACKs** | Servidor | O servidor tem **20% de chance** de não enviar o ACK/NACK de volta ao cliente. | Testar o funcionamento do **Temporizador**. |

---

## 4. Protocolo de Comunicação e Formato do Pacote 

| Elemento | Formato | Descrição |
| :--- | :--- | :--- |
| **Handshake** | `modo:limite` | Cliente envia modo (individual/grupo) e limite de caracteres (mín. 30). Servidor responde com `OK:<tamanho_janela>`. |
| **Pacote de Dados** | `SEQ:x|DATA:conteúdo_cifrado|CHK:y` | O payload (`DATA`) possui no máximo 4 caracteres originais. |
| **Confirmação** | `ACK:x` ou `NACK:x` | Reconhecimento de pacote(s) recebido(s) ou corrompido/fora de ordem. |
| **Controle** | `END` ou `FIM` | `END`: Cliente solicita reconstrução da mensagem. `FIM`: Cliente encerra a conexão. |

---

## 5. Criptografia Simétrica 

O projeto implementa criptografia simétrica AES-128 como funcionalidade adicional. O cliente cifra o payload do pacote com uma chave fixa de 16 bytes e o codifica em Base64 antes da transmissão. O servidor descriptografa os dados antes de calcular o checksum e processar a entrega.

---

## 6. Como Rodar o Código

Para executar o projeto, siga os seguintes passos:

1.  **Pré-requisitos:** Certifique-se de ter o Java Development Kit (JDK) instalado.
2.  **Compilação:** No diretório do projeto, compile os arquivos:
    ```bash
    javac Servidor.java Cliente.java
    ```
3.  **Iniciar Servidor:** Abra um terminal e execute o servidor para que ele escute a porta `1234`:
    ```bash
    java Servidor
    ```
4.  **Iniciar Cliente:** Em outro terminal, execute o cliente e siga as instruções:
    ```bash
    java Cliente
    ```

### Parâmetros de Entrada do Cliente:

O cliente solicitará ao usuário:

1.  **Modo de Operação:** `1` (Repetição Seletiva/individual) ou `2` (Go-Back-N/grupo).
2.  **Limite de Caracteres da Mensagem** (mínimo 30).
3.  **SEQ a corromper** (para corrupção determinística, use `-1` para nenhum).
4.  **SEQs a perder** (lista separada por vírgula, e.g., `3, 4, 7`, use `-1` para nenhuma).
5.  **Mensagens a serem enviadas** (digite 'sair' para encerrar a conexão).

---

## Relatório

O relatório técnico detalha a lógica e a justificação das escolhas de implementação. Você pode acessá-lo diretamente:

[Relatorio G10 - RSD.pdf](https://github.com/user-attachments/files/23841492/Relatorio.G10.-.RSD.pdf)

---

## Integrantes
- Erick Belo  
- João Marcelo Montenegro  
- Rodrigo Torres  
- Victor Vilela  
- João Victor Nunes  
- Rafael Fernandes  

---
