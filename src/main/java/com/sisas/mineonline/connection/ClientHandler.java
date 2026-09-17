package com.sisas.mineonline.connection;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable {
  private final Socket socket;
  private final BoardInitializer session;

  public ClientHandler(Socket socket, BoardInitializer session) {
    this.socket = socket;
    this.session = session;
  }

  @Override
  public void run() {
    ObjectOutputStream out = null;
    try (Socket s = this.socket) {
      out = new ObjectOutputStream(s.getOutputStream());
      out.flush();
      ObjectInputStream in = new ObjectInputStream(s.getInputStream());
      session.registerClient(out);
      while (true) {
        Object message = in.readObject();
        if (message instanceof BoardMessage boardMessage) {
          if (boardMessage.type() == BoardMessageType.ACTION) {
            session.handleRemoteAction(boardMessage.action());
          }
        }
      }
    } catch (EOFException eof) {
      // client disconnected
    } catch (IOException | ClassNotFoundException e) {
      e.printStackTrace();
    } finally {
      if (out != null) {
        session.unregisterClient(out);
      }
    }
  }
}
