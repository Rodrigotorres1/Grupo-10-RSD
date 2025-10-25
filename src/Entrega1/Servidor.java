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
					BufferedReader leitorCliente = new BufferedReader(
							new InputStreamReader(socketCliente.getInputStream()));
					PrintWriter escritorCliente = new PrintWriter(socketCliente.getOutputStream(), true)) {

				System.out.println("Cliente conectado: " + socketCliente.getInetAddress());

				String msgHandshakeCliente = leitorCliente.readLine();
				System.out.println("Handshake recebido: " + msgHandshakeCliente);

				String[] dadosHandshake = msgHandshakeCliente.split(":");
				if (dadosHandshake.length != 2
						|| (!dadosHandshake[0].equals("individual") && !dadosHandshake[0].equals("grupo"))
						|| !dadosHandshake[1].matches("\\d+")) {
					escritorCliente.println("ERRO");
					System.out.println("Handshake inválido.");
					return;
				}

				int limiteCargaUtil = Integer.parseInt(dadosHandshake[1]);
				escritorCliente.println("OK");
				System.out.println("Modo: " + dadosHandshake[0]);
				System.out.println("Tamanho máximo: " + limiteCargaUtil);

				while (true) {
					Map<Integer, String> bufferRecepcao = new TreeMap<>();

					while (true) {
						String linhaRecebida = leitorCliente.readLine();

						if (linhaRecebida == null)
							break;

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
