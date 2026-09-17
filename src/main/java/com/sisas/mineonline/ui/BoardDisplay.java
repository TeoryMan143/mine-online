package com.sisas.mineonline.ui;

import com.sisas.mineonline.connection.BoardInitializer;
import com.sisas.mineonline.connection.BoardSessionListener;
import com.sisas.mineonline.model.Board;

import java.util.concurrent.atomic.AtomicReference;

public class BoardDisplay implements BoardSessionListener {

    private final BoardInitializer session;
    private final AtomicReference<Board> boardRef;

    public BoardDisplay(Board board) {
        this(new BoardInitializer(board));
    }

    public BoardDisplay(BoardInitializer session) {
        this.session = session;
        this.boardRef = new AtomicReference<>(session.getBoard());
        session.addListener(this);
    }

    /**
     * Display takes a command string in the form: "(action) (row) (column)"
     * action: 1 = reveal, 2 = flag
     */
    public void display(String command) {
        if (boardRef.get() == null) {
            System.out.println("Board is null");
            return;
        }

        if (command == null || command.trim().isEmpty()) {
            printBoard(boardRef.get(), boardRef.get().isGameOver());
            return;
        }

        boolean accepted;
        try {
            accepted = session.submitCommand(command);
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());
            accepted = false;
        }
        if (!accepted) {
            printBoard(boardRef.get(), boardRef.get().isGameOver());
        }
    }

    private void printBoard(Board board, boolean revealMines) {
        if (board == null) {
            System.out.println("Board is null");
            return;
        }

        Board.CellView[][] view = board.getBoardData(revealMines);
        int rows = board.getRows();
        int cols = board.getCols();

        System.out.print("   ");
        for (int c = 0; c < cols; c++) {
            System.out.print(c + " ");
        }
        System.out.println();

        for (int r = 0; r < rows; r++) {
            System.out.printf("%2d ", r);
            for (int c = 0; c < cols; c++) {
                Board.CellView cv = view[r][c];
                String out;
                if (cv.revealed) {
                    if (cv.mine) out = "*"; else if (cv.adjacent > 0) out = Integer.toString(cv.adjacent); else out = " ";
                } else if (cv.flagged) {
                    out = "F";
                } else {
                    out = "#";
                }
                System.out.print(out + " ");
            }
            System.out.println();
        }
    }

    @Override
    public void boardUpdated(Board board) {
        boardRef.set(board);

        if (board.isGameOver() && board.isWon()) {
            System.out.println("Congratulations! You won!");
            printBoard(board, true);
            return;
        }

        if (board.isGameOver()) {
            System.out.println("BOOM! You revealed a mine. Game over.");
            printBoard(board, true);
            return;
        }

        printBoard(board, false);
    }
}
