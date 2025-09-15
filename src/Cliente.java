package Entrega1;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Cliente {

    public static void main(String[] args) {
        String enderecoServidor = "localhost";
        int porta = 1234;

        Scanner scanner = new Scanner(System.in);

        String modoOperacao = exibirMenuModo(scanner);

        System.out.print("Digite o tamanho máximo (por exemplo: 3): ");
        String tamanhoMaximo = scanner.nextLine().trim();

        String mensagemHandshake = montarHandshake(modoOperacao, tamanhoMaximo);

        try (Socket socket = new Socket(enderecoServidor, porta);
             BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter saida = new PrintWriter(socket.getOutputStream(), true)) {

            System.out.println("Conectado ao servidor!");
            System.out.println("Enviando handshake: " + mensagemHandshake);
            saida.println(mensagemHandshake);

            String resposta = entrada.readLine();
            if (resposta != null && resposta.startsWith("OK")) {
                System.out.println("Handshake confirmado pelo servidor!");
                System.out.println("Configurações aceitas: " + resposta);
            } else {
                System.out.println("Falha no handshake: " + resposta);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        scanner.close();
    }

    public static String exibirMenuModo(Scanner scanner) {
        System.out.println("escolha o modo de operacao:");
        System.out.println("1. individual");
        System.out.println("2. grupo");
        System.out.print("escolha uma opcao: ");
        String escolha = scanner.nextLine().trim();

        switch (escolha) {
            case "1":
                return "individual";
            case "2":
                return "grupo";
            default:
                System.out.println("apcao invalida. usando modo padrao: individual.");
                return "individual";
        }
    }

    public static String montarHandshake(String modo, String tamanho) {
        return modo + ":" + tamanho;
    }
}
