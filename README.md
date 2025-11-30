# 📌 Grupo-10-RSD

---

## 👥 Integrantes
- Erick Belo  
- João Marcelo Montenegro  
- Rodrigo Torres  
- Victor Vilela  
- João Victor Nunes  
- Rafael Fernandes  

---

## Introdução

Este projeto implementa uma aplicação cliente-servidor que simula, na camada de aplicação, um protocolo de transporte confiável inspirado no funcionamento do TCP, porém construído manualmente. A comunicação ocorre por meio de sockets, mas toda a lógica de confiabilidade é implementada na própria aplicação. O cliente envia mensagens fragmentadas em pacotes pequenos, enquanto o servidor valida a integridade e a ordem desses pacotes e reconstrói a mensagem original. O trabalho utiliza mecanismos como checksum, janela deslizante, confirmações positivas e negativas, retransmissão por timeout e modos distintos de operação, permitindo ao usuário escolher entre Selective Repeat e Go-Back-N. O objetivo é demonstrar de forma prática como funciona o controle interno de entrega confiável em protocolos reais.

---

## Como Rodar o Código

Para executar o projeto, é necessário compilar os arquivos Java e iniciar primeiro o servidor. Dentro do diretório do projeto, compile Servidor.java e Cliente.java utilizando o comando javac. Em seguida, execute o servidor com java Servidor para que ele abra a porta 1234 e aguarde a conexão. Em outro terminal, execute java Cliente e siga as instruções exibidas. O cliente solicitará o modo de operação, o limite de caracteres da mensagem, a simulação de corrupção e a probabilidade de perda. Após isso, basta enviar as mensagens desejadas para que o sistema realize a transmissão, trate ACKs e NACKs e permita a reconstrução final no servidor.

---

## Criptografia Simétrica (Ponto Extra)

O projeto implementa criptografia simétrica AES como funcionalidade adicional. Antes do envio, cada pacote é cifrado pelo cliente e convertido para Base64. O servidor descriptografa os dados e realiza a validação normalmente. Esse recurso adiciona segurança ao processo e demonstra como a criptografia pode ser integrada ao fluxo de transmissão sem interferir nos mecanismos de confiabilidade da aplicação.

---

## Relatório

[Relatório Código Infraestrutura de Comunicação - G10 (2).pdf](https://github.com/user-attachments/files/23841037/Relatorio.Codigo.Infraestrutura.de.Comunicacao.-.G10.2.pdf)

---

