package entrega3;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

public class Cliente {

    private static final String CHAVE_SECRETA = "UmaChaveDe16Bytes"; 
    private static final SecretKey CHAVE = new SecretKeySpec(CHAVE_SECRETA.getBytes(), "AES");
    private static final String ALGORITMO_CRIPTOGRAFIA = "AES";

    public static void main(String[] args) {
        String enderecoServidor = "localhost";
        int porta = 1234;
        final int TIMEOUT_MS = 2000;

        Scanner scanner = new Scanner(System.in);
        String modoOperacao = exibirMenuModo(scanner);

        System.out.print("Digite o limite máximo de caracteres por mensagem (ex: 30): "); 
        int limiteMensagem = Integer.parseInt(scanner.nextLine().trim());
        if (limiteMensagem < 30) limiteMensagem = 30; 
        
        int tamanhoMaxPayload = 4;

        System.out.print("Pacote a ser corrompido (ou -1 para nenhum): ");
        int seqParaCorromper = Integer.parseInt(scanner.nextLine().trim());
        
        System.out.print("Probabilidade de perda de pacote (0.0 a 1.0): ");
        double probPerda = Double.parseDouble(scanner.nextLine().trim());

        String mensagemHandshake = modoOperacao + ":" + limiteMensagem;

        try (Socket socket = new Socket(enderecoServidor, porta);
             BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter saida = new PrintWriter(socket.getOutputStream(), true)) {

            System.out.println("Conectado ao servidor!");
            saida.println(mensagemHandshake);

            String resposta = entrada.readLine();
            if (!resposta.startsWith("OK:")) {
                System.out.println("Handshake falhou: " + resposta);
                return;
            }
            
            int windowSize = Integer.parseInt(resposta.substring(3));
            System.out.println("Handshake confirmado! Tamanho da Janela: " + windowSize);

            socket.setSoTimeout(TIMEOUT_MS);

            while (true) {
                System.out.print("\nDigite a mensagem (ou 'sair'): ");
                String mensagemCompleta = scanner.nextLine();

                if (mensagemCompleta.equalsIgnoreCase("sair")) {
                    saida.println("FIM");
                    break;
                }
                
                String mensagemAEnviar = mensagemCompleta.length() > limiteMensagem ? 
                                        mensagemCompleta.substring(0, limiteMensagem) : 
                                        mensagemCompleta;

                List<String> pacotesDados = new ArrayList<>();
                for (int i = 0; i < mensagemAEnviar.length(); i += tamanhoMaxPayload) {
                    int fim = Math.min(i + tamanhoMaxPayload, mensagemAEnviar.length());
                    pacotesDados.add(mensagemAEnviar.substring(i, fim));
                }

                Map<Integer, String> pacotesEnviados = new HashMap<>();
                Set<Integer> acked = new HashSet<>();
                int base = 0;
                int nextSeqNum = 0;
                int totalPacotes = pacotesDados.size();
                long startTime = 0;

                while (base < totalPacotes) {
                    
                    while (nextSeqNum < base + windowSize && nextSeqNum < totalPacotes) {
                        String dados = pacotesDados.get(nextSeqNum);
                        
                        String dadosCifrados = criptografar(dados);
                        
                        int checksum = calcularChecksum(dados); 
                        
                        String dadosCorrompidos = (nextSeqNum == seqParaCorromper) ? 
                                                introduzirErro(dadosCifrados) : 
                                                dadosCifrados;
                        
                        String pacote = "SEQ:" + nextSeqNum + "|DATA:" + dadosCorrompidos + "|CHK:" + checksum;
                        
                        if (Math.random() < probPerda) {
                            System.out.println("SIMULAÇÃO: Pacote SEQ:" + nextSeqNum + " perdido (não enviado).");
                        } else {
                            saida.println(pacote);
                            System.out.println("Enviado SEQ:" + nextSeqNum + " (Dados: " + dados + ")");
                        }

                        if (base == nextSeqNum) {
                            startTime = System.currentTimeMillis();
                        }
                        
                        pacotesEnviados.put(nextSeqNum, dados);
                        nextSeqNum++;
                    }

                    try {
                        String ack = entrada.readLine();
                        if (ack != null) {
                            System.out.println("Recebido do servidor: " + ack);
                            
                            if (ack.startsWith("ACK:")) {
                                int ackSeq = Integer.parseInt(ack.substring(4));
                                
                                if (modoOperacao.equals("grupo")) { 
                                    if (ackSeq >= base) {
                                        base = ackSeq + 1;
                                    }
                                    if (base < totalPacotes) {
                                        startTime = System.currentTimeMillis();
                                    }
                                } else {
                                    acked.add(ackSeq);
                                    while (acked.contains(base)) {
                                        base++;
                                    }
                                    if (base < totalPacotes) {
                                        startTime = System.currentTimeMillis(); 
                                    }
                                }
                            } else if (ack.startsWith("NACK:")) {
                                int nackSeq = Integer.parseInt(ack.substring(5));
                                System.out.println("NACK recebido para SEQ:" + nackSeq + ". Preparando reenvio.");
                                
                                if (modoOperacao.equals("grupo")) { 
                                    nextSeqNum = base; 
                                } else {
                                    nextSeqNum = nackSeq; 
                                }
                                startTime = System.currentTimeMillis(); 
                            }
                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("\nTimeout! Reenviando pacotes a partir de SEQ:" + base);
                        
                        nextSeqNum = base; 
                        startTime = System.currentTimeMillis();
                        continue;
                    } catch (IOException e) {
                        break; 
                    }
                }

                saida.println("END");

                System.out.print("\n--- Mensagem Reconstruída (Cliente, para verificação) ---\n");
                for (String parte : pacotesDados) {
                    System.out.print(parte);
                }
                System.out.println("\n----------------------------------------------------------");
            }

        } catch (IOException e) {
            System.err.println("Erro de comunicação: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Erro de criptografia/protocolo: " + e.getMessage());
        } finally {
            scanner.close();
        }
    }

    public static String exibirMenuModo(Scanner scanner) {
        System.out.println("\nEscolha o modo de operação (Define o protocolo de retransmissão):");
        System.out.println("1 - individual (Repetição Seletiva / Selective Repeat)");
        System.out.println("2 - grupo (Go-Back-N)");
        System.out.print("Opção: ");
        String opcao = scanner.nextLine();

        return opcao.equals("2") ? "grupo" : "individual";
    }

    public static int calcularChecksum(String dados) {
        int soma = 0;
        for (char c : dados.toCharArray()) {
            soma += c;
        }
        return soma % 256;
    }

    public static String criptografar(String dados) throws Exception {
        if (dados.isEmpty()) return "";
        Cipher cipher = Cipher.getInstance(ALGORITMO_CRIPTOGRAFIA);
        cipher.init(Cipher.ENCRYPT_MODE, CHAVE);
        byte[] bytesCifrados = cipher.doFinal(dados.getBytes());
        return Base64.getEncoder().encodeToString(bytesCifrados);
    }
    
    public static String introduzirErro(String dadosCifrados) {
        if (dadosCifrados.length() == 0) return dadosCifrados;
        char[] chars = dadosCifrados.toCharArray();
        chars[0] = 'Z';
        return new String(chars);
    }
}
