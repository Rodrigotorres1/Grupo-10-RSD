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
            System.out.println("Servidor aguardando conexao na porta " + porta + "...");
            
            try (Socket socket = serverSocket.accept();
                 BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter saida = new PrintWriter(socket.getOutputStream(), true)) {

                System.out.println("Cliente conectado: " + socket.getInetAddress());

                String handshake = entrada.readLine();
                System.out.println("Handshake recebido: " + handshake);

                String[] partes = handshake.split(":");
                if (partes.length != 2 || (!partes[0].equals("individual") && !partes[0].equals("grupo")) || !partes[1].matches("\\d+")) {
                    saida.println("ERRO");
                    System.out.println("Handshake invalido.");
                    return;
                }

                String modoOperacao = partes[0];
                
                int windowSize = 1 + new Random().nextInt(5);
                
                saida.println("OK:" + windowSize);
                System.out.println("Modo: " + modoOperacao);
                System.out.println("Tamanho da Janela (server): " + windowSize);

                Map<Integer, String> pacotesRecebidos = new TreeMap<>();
                int expectedSeqNum = 0;
                int lastAckSeqNum = -1;

                boolean simularPerdaACK = Math.random() < 0.2;
                if (simularPerdaACK) {
                    System.out.println("SIMULACAO: Perda de ACK ativa (20%).");
                }

                while (true) {
                    String pacote = entrada.readLine();

                    if (pacote == null) break;

                    if ("FIM".equals(pacote)) {
                        System.out.println("Conexao encerrada pelo cliente.");
                        return;
                    }

                    if ("END".equals(pacote)) {
                        StringBuilder mensagemReconstruida = new StringBuilder();
                        
                        int ultimoSeqReconstruido = 0;
                        while(pacotesRecebidos.containsKey(ultimoSeqReconstruido)) {
                            mensagemReconstruida.append(pacotesRecebidos.get(ultimoSeqReconstruido));
                            ultimoSeqReconstruido++;
                        }
                        
                        System.out.println("\n--- Mensagem Reconstruida (Servidor) ---");
                        System.out.println(mensagemReconstruida.toString());
                        System.out.println("---------------------------------------\n");
                        
                        pacotesRecebidos.clear();
                        expectedSeqNum = 0;
                        lastAckSeqNum = -1;
                        continue;
                    }

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
                        String resposta = "";

                        // 1. CHECKSUM INVALIDO
                        if (chkCliente != chkServidor) {
                            resposta = "NACK:" + seq;
                            System.out.println("Pacote SEQ:" + seq + " corrompido (checksum invalido). NACK enviado.");
                        }
                        
                        // 2. PACOTE DENTRO DA JANELA E VALIDO
                        else if (seq >= expectedSeqNum && seq < expectedSeqNum + windowSize) {
                            
                            if (modoOperacao.equals("grupo")) {
                                if (seq == expectedSeqNum) {
                                    pacotesRecebidos.put(seq, dadosDescriptografados);
                                    expectedSeqNum++;
                                    while (pacotesRecebidos.containsKey(expectedSeqNum)) {
                                        expectedSeqNum++;
                                    }
                                    resposta = "ACK:" + (expectedSeqNum - 1);
                                    System.out.println("ACK cumulativo para SEQ:" + (expectedSeqNum - 1) + " (GBN).");
                                    lastAckSeqNum = expectedSeqNum - 1;
                                } else {
                                    // Fora de ordem (GBN)
                                    resposta = "ACK:" + (expectedSeqNum - 1);
                                    System.out.println("Pacote SEQ:" + seq + " fora de ordem. Descartado. Reenviando ACK:" + (expectedSeqNum - 1) + " (GBN).");
                                    lastAckSeqNum = expectedSeqNum - 1;
                                }
                            } else {
                                // Repetição Seletiva (SR)
                                if (!pacotesRecebidos.containsKey(seq)) {
                                     pacotesRecebidos.put(seq, dadosDescriptografados);
                                }
                                resposta = "ACK:" + seq;
                                System.out.println("ACK enviado para SEQ:" + seq + " (SR).");
                                lastAckSeqNum = seq;
                                while (pacotesRecebidos.containsKey(expectedSeqNum)) {
                                    expectedSeqNum++;
                                }
                            }
                            
                        } 
                        // 3. PACOTE DUPLICADO (SEQ < expectedSeqNum)
                        else if (seq < expectedSeqNum) {
                            resposta = "ACK:" + seq; 
                            System.out.println("Pacote SEQ:" + seq + " duplicado. Reenviando ACK:" + resposta);
                        } 
                        // 4. PACOTE FORA DA JANELA (MUITO A FRENTE)
                        else {
                            resposta = "NACK:" + seq;
                            System.out.println("Pacote SEQ:" + seq + " fora da janela. NACK enviado.");
                        }

                        // SIMULAÇÃO DE PERDA DE ACK
                        if (Math.random() > 0.8 && simularPerdaACK) {
                            System.out.println("SIMULACAO: Confirmacao '" + resposta + "' perdida (nao enviada).");
                        } else {
                            saida.println(resposta);
                        }

                    } catch (Exception e) {
                        System.out.println("Erro ao processar pacote: " + e.getMessage());
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
