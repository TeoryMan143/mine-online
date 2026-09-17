package com.sisas.mineonline.connection;

import com.sisas.mineonline.model.Board;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class BoardInitializer {
  private static final int DEFAULT_PORT = 8080;

  private final ExecutorService executor = Executors.newCachedThreadPool();
  private final CopyOnWriteArrayList<BoardSessionListener> listeners = new CopyOnWriteArrayList<>();
  private final CopyOnWriteArrayList<ObjectOutputStream> clientOutputs = new CopyOnWriteArrayList<>();
  private final AtomicReference<Board> boardRef;
  private final SessionRole role;

  private volatile ServerSocket serverSocket;
  private volatile Socket socket;
  private volatile ObjectInputStream inputStream;
  private volatile ObjectOutputStream outputStream;
  private volatile boolean running;

  public BoardInitializer(Board board) {
    this(SessionRole.HOST, board);
  }

  public BoardInitializer(SessionRole role, Board board) {
    if (board == null) {
      throw new IllegalArgumentException("board must not be null");
    }
    this.role = role;
    this.boardRef = new AtomicReference<>(board);
  }

  public static BoardInitializer createHost(Board board) {
    return new BoardInitializer(SessionRole.HOST, board);
  }

  public static BoardInitializer joinExisting(Board board) {
    return new BoardInitializer(SessionRole.JOINED, board);
  }

  public synchronized void startHost() throws IOException {
    startHost(DEFAULT_PORT);
  }

  public synchronized void startHost(int port) throws IOException {
    if (role != SessionRole.HOST) {
      throw new IllegalStateException("This session is not configured as host");
    }
    if (running) {
      return;
    }
    ServerSocket createdSocket = new ServerSocket(port);
    try {
      serverSocket = createdSocket;
      running = true;
      executor.submit(this::acceptLoop);
    } catch (RuntimeException e) {
      closeQuietly(createdSocket);
      running = false;
      throw e;
    }
  }

  public void init() {
    if (role != SessionRole.HOST) {
      throw new IllegalStateException("Joined sessions must call connectToExistingGame(...)");
    }
    try {
      startHost(DEFAULT_PORT);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to start host session", e);
    }
  }

  public synchronized void connectToExistingGame(String host, int port) throws IOException {
    if (role != SessionRole.JOINED) {
      throw new IllegalStateException("This session is not configured as a joined client");
    }
    if (running) {
      return;
    }
    Socket createdSocket = null;
    ObjectOutputStream createdOutput = null;
    ObjectInputStream createdInput = null;
    try {
      createdSocket = new Socket(host, port);
      createdOutput = new ObjectOutputStream(createdSocket.getOutputStream());
      createdOutput.flush();
      createdInput = new ObjectInputStream(createdSocket.getInputStream());
      socket = createdSocket;
      outputStream = createdOutput;
      inputStream = createdInput;
      running = true;
      executor.submit(this::receiveLoop);
    } catch (IOException | RuntimeException e) {
      closeQuietly(createdInput);
      closeQuietly(createdOutput);
      closeQuietly(createdSocket);
      running = false;
      throw e;
    }
  }

  public void addListener(BoardSessionListener listener) {
    listeners.add(listener);
  }

  public void removeListener(BoardSessionListener listener) {
    listeners.remove(listener);
  }

  public Board getBoard() {
    return boardRef.get();
  }

  public SessionRole getRole() {
    return role;
  }

  public synchronized boolean submitCommand(String command) {
    BoardAction action = parseCommand(command);
    if (action == null) {
      System.out.println("Invalid command. Use: (action) (row) (column)");
      return false;
    }

    if (role == SessionRole.JOINED) {
      sendActionToHost(action);
      return true;
    }

    if (applyAction(action)) {
      broadcastBoard();
      notifyListeners();
      return true;
    }

    System.out.println("Action could not be applied to the current board state.");
    return false;
  }

  public synchronized void handleRemoteAction(BoardAction action) {
    if (role != SessionRole.HOST) {
      return;
    }
    if (action == null) {
      return;
    }
    if (applyAction(action)) {
      broadcastBoard();
      notifyListeners();
    } else {
      System.out.println("Ignored remote action: " + action);
    }
  }

  public synchronized void replaceBoard(Board board) {
    if (board == null) {
      return;
    }
    boardRef.set(board);
    notifyListeners();
  }

  public synchronized void stop() {
    running = false;
    closeQuietly(outputStream);
    closeQuietly(inputStream);
    closeQuietly(socket);
    closeQuietly(serverSocket);
    for (ObjectOutputStream out : clientOutputs) {
      closeQuietly(out);
    }
    clientOutputs.clear();
    executor.shutdownNow();
  }

  public synchronized void registerClient(ObjectOutputStream out) throws IOException {
    sendBoard(out, boardRef.get());
    clientOutputs.add(out);
  }

  public synchronized void unregisterClient(ObjectOutputStream out) {
    clientOutputs.remove(out);
    closeQuietly(out);
  }

  private void acceptLoop() {
    while (running) {
      try {
        Socket clientSocket = serverSocket.accept();
        executor.submit(new ClientHandler(clientSocket, this));
      } catch (IOException e) {
        if (running) {
          e.printStackTrace();
        }
        return;
      }
    }
  }

  private void receiveLoop() {
    try {
      while (running) {
        try {
          Object message = inputStream.readObject();
          if (message instanceof BoardMessage boardMessage) {
            if (boardMessage.type() == BoardMessageType.FULL_BOARD) {
              replaceBoard(boardMessage.board());
            } else if (boardMessage.type() == BoardMessageType.ACTION) {
              // Clients should only receive full boards, but keep this for safety.
              replaceBoard(boardRef.get());
            }
          }
        } catch (EOFException eof) {
          running = false;
          return;
        } catch (IOException | ClassNotFoundException e) {
          if (running) {
            e.printStackTrace();
          }
          running = false;
          return;
        }
      }
    } finally {
      if (role == SessionRole.JOINED) {
        stop();
      }
    }
  }

  private BoardAction parseCommand(String command) {
    if (command == null || command.trim().isEmpty()) {
      return null;
    }

    String[] parts = command.trim().split("\\s+");
    if (parts.length != 3) {
      return null;
    }

    try {
      int action = Integer.parseInt(parts[0]);
      int row = Integer.parseInt(parts[1]);
      int col = Integer.parseInt(parts[2]);
      return switch (action) {
        case 1 -> new BoardAction(BoardActionType.REVEAL, row, col);
        case 2 -> new BoardAction(BoardActionType.FLAG, row, col);
        default -> null;
      };
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private void sendActionToHost(BoardAction action) {
    if (outputStream == null) {
      throw new IllegalStateException("Joined session is not connected to a host");
    }
    try {
      synchronized (outputStream) {
        outputStream.reset();
        outputStream.writeObject(BoardMessage.action(action));
        outputStream.flush();
      }
    } catch (IOException e) {
      throw new IllegalStateException("Unable to send action to host", e);
    }
  }

  private boolean applyAction(BoardAction action) {
    Board board = boardRef.get();
    if (board == null) {
      return false;
    }

    return switch (action.type()) {
      case REVEAL -> {
        if (!board.canReveal(action.row(), action.column())) {
          yield false;
        }
        board.reveal(action.row(), action.column());
        yield true;
      }
      case FLAG -> {
        if (!board.canToggleFlag(action.row(), action.column())) {
          yield false;
        }
        board.toggleFlag(action.row(), action.column());
        yield true;
      }
    };
  }

  private void broadcastBoard() {
    Board board = boardRef.get();
    for (ObjectOutputStream out : clientOutputs) {
      try {
        sendBoard(out, board);
      } catch (IOException e) {
        unregisterClient(out);
      }
    }
  }

  private void sendBoard(ObjectOutputStream out, Board board) throws IOException {
    synchronized (out) {
      out.reset();
      out.writeObject(BoardMessage.fullBoard(board));
      out.flush();
    }
  }

  private void notifyListeners() {
    Board board = boardRef.get();
    listeners.forEach(listener -> listener.boardUpdated(board));
  }

  private void closeQuietly(AutoCloseable closeable) {
    if (closeable == null) {
      return;
    }
    try {
      closeable.close();
    } catch (Exception ignored) {
      // Nothing else to do during shutdown.
    }
  }
}
