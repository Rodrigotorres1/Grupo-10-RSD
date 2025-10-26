package entrega2;

import java.io.*;
import java.net.*;
import java.util.*;

public class Servidor {
    public static void main(String[] args) {
        int portaLocal = 1234;

        try (ServerSocket socketEscuta = new ServerSocket(portaLocal)) {
            System.out.println("Servidor aguardando conexão na porta " + portaLocal + "...");

            try (Socket socketCliente = socketEscuta.accept();
                 BufferedReader leitorCliente = new BufferedReader(new InputStreamReader(socketCliente.getInputStream()));
                 PrintWriter escritorCliente = new PrintWriter(socketCliente.getOutputStream(), true)) {

                System.out.println("Cliente conectado: " + socketCliente.getInetAddress());

                String msgHandshakeCliente = leitorCliente.readLine();
                System.out.println("Handshake recebido: " + msgHandshakeCliente);

                
                String modoOperacao = msgHandshakeCliente; 

                
                if (modoOperacao == null || (!modoOperacao.equals("individual") && !modoOperacao.equals("grupo"))) {
                    escritorCliente.println("ERRO");
                    System.out.println("Handshake inválido. Modo desconhecido: " + modoOperacao);
                    return;
                }

                
                int limiteCargaUtil = 4; 
                escritorCliente.println("OK");
                System.out.println("Modo: " + modoOperacao);
                System.out.println("Tamanho máximo (fixo): " + limiteCargaUtil);

     
                while (true) {
                    Map<Integer, String> bufferRecepcao = new TreeMap<>();

                    while (true) {
                        String linhaRecebida = leitorCliente.readLine();

                        if (linhaRecebida == null) break;

                        if ("FIM".equals(linhaRecebida)) {
                            System.out.println("Conexão encerrada pelo cliente.");
                            return;
                        }

                        if ("END".equals(linhaRecebida)) {
                       
                            StringBuilder dadosCompletos = new StringBuilder();
                            for (String parte : bufferRecepcao.values()) {
                                dadosCompletos.append(parte);
                            }
                            System.out.println("Mensagem reconstruída (servidor): " + dadosCompletos.toString());
                            break;
                        }

                        System.out.println("Pacote recebido: " + linhaRecebida);

                        if (linhaRecebida.startsWith("SEQ:") && linhaRecebida.contains("|DATA:")) {
                            try {
                                String[] partesPacote = linhaRecebida.split("\\|DATA:");
                                String strSequencia = partesPacote[0].replace("SEQ:", "");
                                String payload = partesPacote[1];
                                int idPacote = Integer.parseInt(strSequencia.trim());

                                bufferRecepcao.put(idPacote, payload);

                                String msgConfirmacao = "ACK:" + idPacote;
                                escritorCliente.println(msgConfirmacao);
                            } catch (Exception e) {
                                System.out.println("Erro ao processar pacote: " + e.getMessage());
                            }
                        } else {
                            System.out.println("Pacote inválido: " + linhaRecebida);
                        }
                    }
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
