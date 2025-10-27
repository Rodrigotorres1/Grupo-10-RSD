package entrega2;

import java.io.*;
import java.net.*;
import java.util.*;

public class Cliente {

    public static String exibirMenuModo(Scanner input) {
        System.out.println("Escolha o modo de operação:");
        System.out.println("1. Individual");
        System.out.println("2. Grupo");
        System.out.print("Escolha uma opção: ");
        String escolha = input.nextLine().trim();

        switch (escolha) {
            case "1":
                return "individual";
            case "2":
                return "grupo";
            default:
                System.out.println("Opção inválida. Usando modo padrão: individual.");
                return "individual";
        }
    }

    public static void main(String[] args) {
        String hostServidor = "localhost";
        int portaServidor = 1234;

        Scanner leitorConsole = new Scanner(System.in);
        String tipoConexao = exibirMenuModo(leitorConsole);

        int tamanhoCargaUtil = 4; 

        try (Socket conexaoSocket = new Socket(hostServidor, portaServidor);
             BufferedReader leitorSocket = new BufferedReader(new InputStreamReader(conexaoSocket.getInputStream()));
             PrintWriter escritorSocket = new PrintWriter(conexaoSocket.getOutputStream(), true)) {

            System.out.println("Conectado ao servidor!");
            
            String dadosHandshake = tipoConexao; 
            escritorSocket.println(dadosHandshake);

            String respostaServidor = leitorSocket.readLine();
            if (!"OK".equals(respostaServidor)) {
                System.out.println("Handshake falhou.");
                return;
            }

            System.out.println("Handshake confirmado!");

            while (true) {
                
                int numSequencia = 0; // <-- CORREÇÃO: Movido para dentro do loop
                
                String inputUsuario;
                while (true) {
                    System.out.print("\nDigite a mensagem (mín 30 caracteres) ou 'sair': ");
                    inputUsuario = leitorConsole.nextLine();
                    
                    if (inputUsuario.equalsIgnoreCase("sair")) {
                        break; 
                    }
                    if (inputUsuario.length() >= 30) {
                        break; 
                    }
                    System.out.println("Erro: A mensagem deve ter no mínimo 30 caracteres.");
                }
                
                if (inputUsuario.equalsIgnoreCase("sair")) {
                    escritorSocket.println("FIM");
                    break;
                }

                if (tipoConexao.equals("individual")) {
                    System.out.println("\nModo INDIVIDUAL:");
                    Map<Integer, String> bufferMensagem = new TreeMap<>();

                    for (int i = 0; i < inputUsuario.length(); i += tamanhoCargaUtil) {
                        int fim = Math.min(i + tamanhoCargaUtil, inputUsuario.length());
                        String payload = inputUsuario.substring(i, fim);
                        String segmento = "SEQ:" + numSequencia + "|DATA:" + payload;

                        escritorSocket.println(segmento);
                        System.out.println("Enviado: " + segmento);

                        String confirmacao = leitorSocket.readLine();
                        System.out.println("Recebido do servidor: " + confirmacao);

                        bufferMensagem.put(numSequencia, payload);
                        numSequencia++;

                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

       
                    escritorSocket.println("END");

                    System.out.print("Mensagem reconstruída (cliente): ");
                    for (String parte : bufferMensagem.values()) {
                        System.out.print(parte);
                    }
                    System.out.println();

                } else {
                    System.out.println("\nModo GRUPO:");
                    Map<Integer, String> mapPacotesEnviados = new TreeMap<>();
                    List<Integer> listaSequencias = new ArrayList<>();

                    for (int i = 0; i < inputUsuario.length(); i += tamanhoCargaUtil) {
                        int fim = Math.min(i + tamanhoCargaUtil, inputUsuario.length());
                        String payload = inputUsuario.substring(i, fim);
                        String segmento = "SEQ:" + numSequencia + "|DATA:" + payload;

                        escritorSocket.println(segmento);
                        System.out.println("Enviado: " + segmento);

                        mapPacotesEnviados.put(numSequencia, payload);
                        listaSequencias.add(numSequencia);
                        numSequencia++;
                    }

                    System.out.println("Todos os pacotes foram enviados. Aguardando ACKs...\n");

                    Map<Integer, String> mapPacotesConfirmados = new TreeMap<>();
                    for (int i = 0; i < listaSequencias.size(); i++) {
                        String confirmacao = leitorSocket.readLine();
                        System.out.println("Recebido do servidor: " + confirmacao);

                        if (confirmacao != null && confirmacao.startsWith("ACK:")) {
                            int seqConfirmada = Integer.parseInt(confirmacao.split(":")[1]);
                            if (mapPacotesEnviados.containsKey(seqConfirmada)) {
                                mapPacotesConfirmados.put(seqConfirmada, mapPacotesEnviados.get(seqConfirmada));
                            }
                        }
                    }

          
                    escritorSocket.println("END");

                    System.out.print("Mensagem reconstruída (cliente): ");
                    for (String parte : mapPacotesConfirmados.values()) {
                        System.out.print(parte);
                    }
                    System.out.println();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        leitorConsole.close();
    }
}
