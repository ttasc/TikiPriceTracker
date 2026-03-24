package com.tiki.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.security.PublicKey;

import javax.crypto.SecretKey;

import com.tiki.common.CryptoUtils;

public class ClientConnection {
	private Socket socket;
	private DataInputStream in;
	private DataOutputStream out;
	private SecretKey sessionKey;

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
