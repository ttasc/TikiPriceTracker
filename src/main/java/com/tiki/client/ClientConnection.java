package com.tiki.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.security.PublicKey;

import javax.crypto.SecretKey;

import com.tiki.common.CryptoUtils;

public class ClientConnection {
	private static final int BPORT= 30255;

	private Socket socket;
	private DataInputStream in;
	private DataOutputStream out;
	private SecretKey sessionKey;

	protected static String discoverServerIp() {
	    System.out.println("[NETWORK] Searching for Server via UDP Broadcast...");
	    try (DatagramSocket socket = new DatagramSocket(BPORT)) {
	        socket.setSoTimeout(10000);
	        byte[] buffer = new byte[1024];
	        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

	        while (true) {
	            socket.receive(packet);
	            String message = new String(packet.getData(), 0, packet.getLength());
	            
	            if (message.startsWith("TIKI_SERVER_DISCOVERY")) {
	                String ip = packet.getAddress().getHostAddress();
	                System.out.println("[NETWORK] Server found at: " + ip);
	                return ip;
	            }
	        }
	    } catch (Exception e) {
	        System.err.println("[ERROR] Server discovery timed out.");
	        return null;
	    }
	}

	public void connect(String host, int port) throws Exception {
		System.out.println("[LOG] Attempting to connect to " + host + ":" + port);
		socket = new Socket(host, port);
		in = new DataInputStream(socket.getInputStream());
		out = new DataOutputStream(socket.getOutputStream());

		int pubKeyLen = in.readInt();
		byte[] pubKeyBytes = new byte[pubKeyLen];
		in.readFully(pubKeyBytes);
		PublicKey serverPubKey = CryptoUtils.getPublicKeyFromBytes(pubKeyBytes);

		sessionKey = CryptoUtils.generateAESKey();
		byte[] encryptedAESKey = CryptoUtils.encryptRSA(sessionKey.getEncoded(), serverPubKey);
		out.writeInt(encryptedAESKey.length);
		out.write(encryptedAESKey);
		out.flush();

		System.out.println("[INFO] Secure handshake successful. Session key exchanged.");
	}

	public String sendRequest(String jsonRequest) throws Exception {
		try {
			out.writeUTF(CryptoUtils.encryptAES(jsonRequest, sessionKey));
			out.flush();

			String encryptedResponse = in.readUTF();
			return CryptoUtils.decryptAES(encryptedResponse, sessionKey);
		} catch (Exception e) {
			System.err.println("[ERROR] Failed to process request: " + e.getMessage());
			throw e;
		}
	}
}
