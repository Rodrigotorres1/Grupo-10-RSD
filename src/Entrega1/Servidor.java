package entrega3;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

public class Servidor {


    private static final String CHAVE_SECRETA = "ChaveSecreta1234"; 
    private static final SecretKey CHAVE = new SecretKeySpec(CHAVE_SECRETA.getBytes(), "AES");
    private static final String ALGORITMO_CRIPTOGRAFIA = "AES";

    public static void main(String[] args) {
        int porta = 1234;
        try (ServerSocket serverSocket = new ServerSocket(porta)) {
            System.out.println("Servidor aguardando conexão na porta " + porta + "...");
            
            try (Socket socket = serverSocket.accept();
                 BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter saida = new PrintWriter(socket.getOutputStream(), true)) {

                System.out.println("Cliente conectado: " + socket.getInetAddress());

                String handshake = entrada.readLine();
                System.out.println("Handshake recebido: " + handshake);

                String[] partes = handshake.split(":");
                if (partes.length != 2 || (!partes[0].equals("individual") && !partes[0].equals("grupo")) || !partes[1].matches("\\d+")) {
                    saida.println("ERRO");
                    System.out.println("Handshake inválido.");
                    return;
                }

                String modoOperacao = partes[0];
                int windowSize = 5;
                
                saida.println("OK:" + windowSize);
                System.out.println("Modo: " + modoOperacao);
                System.out.println("Tamanho da Janela (server): " + windowSize);

                Map<Integer, String> pacotesRecebidos = new TreeMap<>();
                int expectedSeqNum = 0;
                int lastAckSeqNum = -1;

                boolean simularPerdaACK = Math.random() < 0.2;
                if (simularPerdaACK) {
                    System.out.println("Simulando perda de ACK com chance de 20% para a sessão.");
                }

                while (true) {
                    String pacote = entrada.readLine();

                    if (pacote == null) break;

                    if ("FIM".equals(pacote)) {
                        System.out.println("Conexão encerrada pelo cliente.");
                        return;
                    }

                    if ("END".equals(pacote)) {
                        StringBuilder mensagemReconstruida = new StringBuilder();
                        for (String parte : pacotesRecebidos.values()) {
                            mensagemReconstruida.append(parte);
                        }
                        System.out.println("\n--- Mensagem Reconstruída (Servidor) ---");
                        System.out.println(mensagemReconstruida.toString());
                        System.out.println("---------------------------------------\n");
                        pacotesRecebidos.clear();
                        expectedSeqNum = 0;
                        lastAckSeqNum = -1;
                        continue;
                    }

                    System.out.println("\nPacote recebido: " + pacote);

                    if (pacote.contains("SEQ:") && pacote.contains("|DATA:") && pacote.contains("|CHK:")) {
                        try {
                            String[] partesPacote = pacote.split("\\|");
                            int seq = -1;
                            String dadosCifrados = "";
                            int chkCliente = -1;

                            for (String parte : partesPacote) {
                                if (parte.startsWith("SEQ:")) {
                                    seq = Integer.parseInt(parte.substring(4));
                                } else if (parte.startsWith("DATA:")) {
                                    dadosCifrados = parte.substring(5);
                                } else if (parte.startsWith("CHK:")) {
                                    chkCliente = Integer.parseInt(parte.substring(4));
                                }
                            }
                            
                            String dadosDescriptografados = descriptografar(dadosCifrados);

                            int chkServidor = calcularChecksum(dadosDescriptografados);

                            System.out.println("  SEQ: " + seq);
                            System.out.println("  DATA (descifrada): " + dadosDescriptografados);
                            System.out.println("  CHK recebido: " + chkCliente);
                            System.out.println("  CHK calculado: " + chkServidor);
                            
                            String resposta = "";

                            if (chkCliente != chkServidor) {
                                resposta = "NACK:" + seq;
                                System.out.println("Pacote SEQ:" + seq + " está corrompido (checksum inválido). NACK enviado.");
                            }
                            
                            else if (seq >= expectedSeqNum && seq < expectedSeqNum + windowSize) {
                                pacotesRecebidos.put(seq, dadosDescriptografados);
                                
                                if (modoOperacao.equals("grupo")) {
                                    if (seq == expectedSeqNum) {
                                        while (pacotesRecebidos.containsKey(expectedSeqNum)) {
                                            pacotesRecebidos.remove(expectedSeqNum);
                                            expectedSeqNum++;
                                        }
                                        resposta = "ACK:" + (expectedSeqNum - 1);
                                        System.out.println("Pacote válido (GBN). ACK cumulativo para: " + resposta);
                                        lastAckSeqNum = expectedSeqNum - 1;
                                    } else {
                                        resposta = "ACK:" + lastAckSeqNum; // Envia o ACK do último pacote em ordem
                                        System.out.println("Pacote fora de ordem (GBN). Enviando ACK para último aceito: " + resposta);
                                        pacotesRecebidos.remove(seq);
                                    }
                                } else {
                                    resposta = "ACK:" + seq;
                                    System.out.println("Pacote válido (SR). ACK enviado para: " + seq);
                                    lastAckSeqNum = seq;
                                    while (pacotesRecebidos.containsKey(expectedSeqNum)) {
                                        expectedSeqNum++;
                                    }
                                }

                                if (expectedSeqNum > lastAckSeqNum) {
                                    System.out.println("Janela deslizou para: " + expectedSeqNum);
                                }
                                
                            } else {
                                resposta = "NACK:" + seq;
                                System.out.println("Pacote SEQ:" + seq + " fora da janela. NACK enviado.");
                            }

                            if (Math.random() > 0.8 && simularPerdaACK) {
                                System.out.println("SIMULAÇÃO: Confirmação '" + resposta + "' perdida (não enviada).");
                            } else {
                                saida.println(resposta);
                            }

                        } catch (Exception e) {
                            System.out.println("Erro ao processar pacote: " + e.getMessage());
                        }
                    } else {
                        System.out.println("Pacote inválido: " + pacote);
                    }
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int calcularChecksum(String dados) {
        int soma = 0;
        for (char c : dados.toCharArray()) {
            soma += c;
        }
        return soma % 256;
    }

    public static String descriptografar(String dadosCifrados) throws Exception {
        if (dadosCifrados.isEmpty()) return "";
        Cipher cipher = Cipher.getInstance(ALGORITMO_CRIPTOGRAFIA);
        cipher.init(Cipher.DECRYPT_MODE, CHAVE);
        byte[] bytesCifrados = Base64.getDecoder().decode(dadosCifrados);
        byte[] bytesDescriptografados = cipher.doFinal(bytesCifrados);
        return new String(bytesDescriptografados);
    }
}
