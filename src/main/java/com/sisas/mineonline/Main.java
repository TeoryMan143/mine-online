package com.sisas.mineonline;

import com.sisas.mineonline.connection.BoardInitializer;
import com.sisas.mineonline.model.Board;
import com.sisas.mineonline.ui.BoardDisplay;

import java.io.IOException;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        BoardInitializer session = null;
        BoardDisplay display = null;

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("Choose: 1) Host  2) Join");
            String choice = scanner.nextLine().trim();
            if ("1".equals(choice) || "host".equalsIgnoreCase(choice)) {
                System.out.print("Rows (default 9): ");
                int rows = parseIntOrDefault(scanner.nextLine().trim(), 9);
                System.out.print("Cols (default 9): ");
                int cols = parseIntOrDefault(scanner.nextLine().trim(), 9);
                System.out.print("Mines (default 10): ");
                int mines = parseIntOrDefault(scanner.nextLine().trim(), 10);

                Board board = new Board(rows, cols, mines);
                session = BoardInitializer.createHost(board);
                try {
                    session.init();
                } catch (IllegalStateException e) {
                    System.err.println("Unable to start host: " + e.getMessage());
                    return;
                }
                display = new BoardDisplay(session);
                System.out.println("Hosting game. Type commands like: \"1 0 0\" (reveal) or \"2 1 1\" (flag). Type exit to quit.");
                display.display("");

            } else if ("2".equals(choice) || "join".equalsIgnoreCase(choice)) {
                System.out.print("Host (ip or hostname): ");
                String host = scanner.nextLine().trim();
                if (host.isEmpty()) {
                    System.err.println("Host is required to join.");
                    return;
                }
                System.out.print("Port (default 8080): ");
                int port = parseIntOrDefault(scanner.nextLine().trim(), 8080);

                Board placeholder = new Board(9, 9, 10);
                session = BoardInitializer.joinExisting();
                try {
                    session.connectToExistingGame(host, port);
                } catch (IOException e) {
                    System.err.println("Unable to connect to host: " + e.getMessage());
                    return;
                }
                display = new BoardDisplay(session);
                System.out.println("Connected to host " + host + ":" + port + ". Type commands like: \"1 0 0\" or \"2 1 1\". Type exit to quit.");
                display.display("");

            } else {
                System.out.println("Invalid option.");
                return;
            }

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    display.display("");
                    continue;
                }
                if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
                    break;
                }
                display.display(line);
            }

        } finally {
            if (session != null) {
                session.stop();
            }
            System.out.println("Shutting down.");
        }
    }

    private static int parseIntOrDefault(String s, int def) {
        if (s == null || s.isEmpty()) return def;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
