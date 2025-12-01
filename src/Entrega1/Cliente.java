package entrega3;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

public class Cliente {

    private static final String CHAVE_SECRETA = "ChaveSecreta1234"; 
    private static final SecretKey CHAVE = new SecretKeySpec(CHAVE_SECRETA.getBytes(), "AES");
    private static final String ALGORITMO_CRIPTOGRAFIA = "AES";
    
    private static final Set<Integer> PACOTES_CORROMPIDOS_INJETADOS = new HashSet<>();
    private static final Set<Integer> PACOTES_PARA_PERDER_INJETADOS = new HashSet<>();

    public static void main(String[] args) {
        String enderecoServidor = "localhost";
        int porta = 1234;
        final int TIMEOUT_MS = 2000;

        Scanner scanner = new Scanner(System.in);
        String modoOperacao = exibirMenuModo(scanner);

        System.out.print("Digite o limite de caracteres (minimo 30, ex: 50): ");
        int limiteMensagem = Integer.parseInt(scanner.nextLine().trim());
        
        if (limiteMensagem < 30) {
            System.out.println("Aviso: O limite minimo de 30 caracteres sera utilizado.");
            limiteMensagem = 30;
        } 
        
        int tamanhoMaxPayload = 4;

        // --- NOVO MENU DE SIMULAÇÃO DE FALHAS ---
        int seqParaCorromper = -1;
        String perdasInput = "-1";
        
        System.out.println("\nComo você quer que a comunicação ocorra?");
        System.out.println("1 - COMUNICACAO SEGURA (Sem perdas/erros determinísticos)");
        System.out.println("2 - SIMULAR PERDA DE PACOTE(S)");
        System.out.println("3 - SIMULAR PACOTE CORROMPIDO");
        System.out.print("Opção: ");
        
        String opcaoFalha = scanner.nextLine().trim();

        if (opcaoFalha.equals("2")) {
            System.out.print("Pacotes a serem perdidos (separados por virgula): ");
            perdasInput = scanner.nextLine().trim();
        } else if (opcaoFalha.equals("3")) {
            System.out.print("Pacote a ser corrompido: ");
            seqParaCorromper = Integer.parseInt(scanner.nextLine().trim());
        }
        
        // Processa a lista de perdas se a opção 2 foi escolhida
        if (!perdasInput.equals("-1") && !perdasInput.isEmpty()) {
            try {
                String[] sequencias = perdasInput.split(",");
                for (String seq : sequencias) {
                    PACOTES_PARA_PERDER_INJETADOS.add(Integer.parseInt(seq.trim()));
                }
            } catch (NumberFormatException e) {
                System.out.println("Aviso: Formato de pacotes a perder invalido. Nenhuma perda deterministica sera aplicada.");
            }
        }
        // --- FIM NOVO MENU ---

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
                
                if (mensagemCompleta.length() < 30) {
                    System.out.println("ERRO: A mensagem digitada deve ter no minimo 30 caracteres para ser enviada.");
                    continue; 
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
                
                PACOTES_CORROMPIDOS_INJETADOS.clear();

                while (base < totalPacotes) {
                    
                    int tempSeqNum = nextSeqNum;
                    while (tempSeqNum < base + windowSize && tempSeqNum < totalPacotes) {
                        
                        if (modoOperacao.equals("individual") && acked.contains(tempSeqNum)) {
                            tempSeqNum++;
                            continue;
                        }

                        String dados = pacotesDados.get(tempSeqNum);
                        
                        String dadosCifrados = criptografar(dados);
                        
                        int checksum = calcularChecksum(dados);
                        
                        String dadosCorrompidos = dadosCifrados;
                        boolean isRetransmissao = pacotesEnviados.containsKey(tempSeqNum);
                        
                        if (tempSeqNum == seqParaCorromper && !PACOTES_CORROMPIDOS_INJETADOS.contains(tempSeqNum)) {
                            dadosCorrompidos = introduzirErro(dadosCifrados);
                            PACOTES_CORROMPIDOS_INJETADOS.add(tempSeqNum);
                            System.out.println("SIMULACAO: ERRO DE INTEGRIDADE introduzido no pacote SEQ:" + tempSeqNum + ".");
                        }
                        
                        String pacote = "SEQ:" + tempSeqNum + "|DATA:" + dadosCorrompidos + "|CHK:" + checksum;
                        
                        if (PACOTES_PARA_PERDER_INJETADOS.contains(tempSeqNum) && !isRetransmissao) {
                            System.out.println("SIMULACAO: PERDA DE PACOTE SEQ:" + tempSeqNum + " (nao enviado).");
                            PACOTES_PARA_PERDER_INJETADOS.remove(tempSeqNum);
                        } else {
                            saida.println(pacote);
                            
                            if (isRetransmissao) {
                                System.out.println("Retransmitindo SEQ:" + tempSeqNum);
                            }
                        }

                        if (base == tempSeqNum) {
                            startTime = System.currentTimeMillis();
                        }
                        
                        pacotesEnviados.put(tempSeqNum, dados);
                        
                        if (tempSeqNum == nextSeqNum) {
                            nextSeqNum++;
                        }
                        tempSeqNum++;
                    }

                    long tempoDecorrido = System.currentTimeMillis() - startTime;
                    if (base < nextSeqNum && tempoDecorrido >= TIMEOUT_MS) {
                        System.out.println("\nTIMEOUT! Reenviando pacotes a partir de SEQ:" + base);
                        
                        if (modoOperacao.equals("individual")) {
                            int pacoteParaRetransmitir = -1;
                            for (int i = base; i < totalPacotes && i < base + windowSize; i++) {
                                if (!acked.contains(i)) {
                                    pacoteParaRetransmitir = i;
                                    break;
                                }
                            }
                            
                            if (pacoteParaRetransmitir != -1) {
                                nextSeqNum = pacoteParaRetransmitir; 
                            } else {
                                nextSeqNum = base; 
                            }

                        } else { 
                            nextSeqNum = base;
                        }

                        startTime = System.currentTimeMillis();
                        continue;
                    }
                    
                    try {
                        String ack = entrada.readLine();
                        
                        if (ack == null) break;

                        System.out.println("ACK/NACK Recebido: " + ack);
                        
                        if (ack.startsWith("ACK:")) {
                            int ackSeq = Integer.parseInt(ack.substring(4));
                            
                            if (modoOperacao.equals("grupo")) {
                                if (ackSeq >= base) {
                                    System.out.println("Janela GBN: Nova Base em " + (ackSeq + 1) + " (ACKs 0-" + ackSeq + " confirmados).");
                                    base = ackSeq + 1;
                                    startTime = System.currentTimeMillis();
                                }
                            } else {
                                acked.add(ackSeq);
                                if (ackSeq == base) {
                                     while (acked.contains(base)) {
                                        acked.remove(base);
                                        base++;
                                    }
                                    System.out.println("Janela SR: Nova Base em " + base + " (ACKs 0-" + (base-1) + " confirmados).");
                                    startTime = System.currentTimeMillis();
                                }
                            }
                            
                        } else if (ack.startsWith("NACK:")) {
                            int nackSeq = Integer.parseInt(ack.substring(5));
                            System.out.println("NACK recebido para SEQ:" + nackSeq + ". Preparando reenvio.");
                            
                            if (modoOperacao.equals("grupo")) {
                                nextSeqNum = base;
                                startTime = System.currentTimeMillis();
                            } else {
                                if (!acked.contains(nackSeq)) {
                                     nextSeqNum = nackSeq;
                                     startTime = System.currentTimeMillis();
                                }
                            }
                        }
                    } catch (SocketTimeoutException e) {
                        
                    } catch (IOException e) {
                        break;
                    }
                }

                saida.println("END");

                System.out.print("\n--- Mensagem Reconstruida (Cliente, para verificacao) ---\n");
                for (String parte : pacotesDados) {
                    System.out.print(parte);
                }
                System.out.println("\n----------------------------------------------------------");
            }

        } catch (IOException e) {
            System.err.println("Erro de comunicacao: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Erro de criptografia/protocolo: " + e.getMessage());
        } finally {
            scanner.close();
        }
    }

    public static String exibirMenuModo(Scanner scanner) {
        System.out.println("\nEscolha o modo de operacao (Define o protocolo de retransmissao):");
        System.out.println("1 - individual (Repeticao Seletiva / Selective Repeat)");
        System.out.println("2 - grupo (Go-Back-N)");
        System.out.print("Opcao: ");
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
