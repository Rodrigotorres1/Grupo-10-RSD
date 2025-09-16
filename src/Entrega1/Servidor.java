package Entrega1;

import java.io.*;
import java.net.*;

public class Servidor {
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
                if (partes.length == 2) {
                    String modo = partes[0];
                    String tamanhoStr = partes[1];

                    if ((modo.equals("individual") || modo.equals("grupo")) && tamanhoStr.matches("\\d+")) {
                        int tamanhoMax = Integer.parseInt(tamanhoStr);

                        if (tamanhoMax > 0 && tamanhoMax <= 100) {
                            System.out.println("Modo de operacao: " + modo);
                            System.out.println("Tamanho máximo confirmado: " + tamanhoMax + " caracteres");

                            saida.println("OK:modo=" + modo + ";tamanho=" + tamanhoMax);
                            System.out.println("Handshake confirmado com sucesso.");
                        } else {
                            saida.println("ERRO:Tamanho fora do intervalo permitido (1-100).");
                            System.out.println("Erro no handshake: tamanho inválido.");
                        }
                    } else {
                        saida.println("ERRO:modo invalido ou tamanho não numérico.");
                        System.out.println("Erro no handshake: modo inválido ou tamanho não numérico.");
                    }
                } else {
                    saida.println("ERRO:Formato invalido. Use 'modo:tamanho'.");
                    System.out.println("Formato inválido de handshake.");
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
