package com.sisas.mineonline.model;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;

public class Board implements Serializable {

    private static final long serialVersionUID = 1L;
    private final int rows;
    private final int cols;
    private final int totalMines;
    private final Cell[][] cells;
    private boolean gameOver = false;
    private boolean won = false;

    private static class Cell implements Serializable {
        private static final long serialVersionUID = 1L;
        boolean mine = false;
        boolean revealed = false;
        boolean flagged = false;
        int adjacent = 0;
    }

    public static class CellView {
        public final boolean revealed;
        public final boolean flagged;
        public final boolean mine; // true only when revealed or revealMines=true
        public final int adjacent;

        public CellView(boolean revealed, boolean flagged, boolean mine, int adjacent) {
            this.revealed = revealed;
            this.flagged = flagged;
            this.mine = mine;
            this.adjacent = adjacent;
        }
    }

    public Board(int rows, int cols, int mines) {
        if (rows <= 0 || cols <= 0) throw new IllegalArgumentException("rows and cols must be > 0");
        if (mines < 0 || mines >= rows * cols) throw new IllegalArgumentException("invalid mine count");
        this.rows = rows;
        this.cols = cols;
        this.totalMines = mines;
        this.cells = new Cell[rows][cols];
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) cells[r][c] = new Cell();
        placeMinesRandomly();
        computeAdjacents();
    }

    private void placeMinesRandomly() {
        Random rnd = new Random();
        int placed = 0;
        while (placed < totalMines) {
            int r = rnd.nextInt(rows);
            int c = rnd.nextInt(cols);
            if (!cells[r][c].mine) {
                cells[r][c].mine = true;
                placed++;
            }
        }
    }

    private void computeAdjacents() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (cells[r][c].mine) continue;
                int count = 0;
                for (int dr = -1; dr <= 1; dr++) for (int dc = -1; dc <= 1; dc++) {
                    if (dr == 0 && dc == 0) continue;
                    int nr = r + dr, nc = c + dc;
                    if (inBounds(nr, nc) && cells[nr][nc].mine) count++;
                }
                cells[r][c].adjacent = count;
            }
        }
    }

    private boolean inBounds(int r, int c) {
        return r >= 0 && r < rows && c >= 0 && c < cols;
    }

    // Reveal cell. Returns true if a mine was revealed (game over).
    public synchronized boolean reveal(int row, int col) {
        if (gameOver || !inBounds(row, col)) return false;
        Cell cell = cells[row][col];
        if (cell.revealed || cell.flagged) return false;
        if (cell.mine) {
            cell.revealed = true;
            revealAllMines();
            gameOver = true;
            won = false;
            return true;
        }
        floodReveal(row, col);
        checkWin();
        return false;
    }

    private void revealAllMines() {
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) if (cells[r][c].mine) cells[r][c].revealed = true;
    }

    private void floodReveal(int startR, int startC) {
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{startR, startC});
        while (!q.isEmpty()) {
            int[] p = q.remove();
            int r = p[0], c = p[1];
            Cell cur = cells[r][c];
            if (cur.revealed || cur.flagged) continue;
            cur.revealed = true;
            if (cur.adjacent == 0) {
                for (int dr = -1; dr <= 1; dr++) for (int dc = -1; dc <= 1; dc++) {
                    if (dr == 0 && dc == 0) continue;
                    int nr = r + dr, nc = c + dc;
                    if (inBounds(nr, nc) && !cells[nr][nc].revealed && !cells[nr][nc].mine) q.add(new int[]{nr, nc});
                }
            }
        }
    }

    // Toggle flag on a cell. Returns the new flagged state, or null if action not allowed.
    public synchronized Boolean toggleFlag(int row, int col) {
        if (gameOver || !inBounds(row, col)) return null;
        Cell cell = cells[row][col];
        if (cell.revealed) return null;
        cell.flagged = !cell.flagged;
        return cell.flagged;
    }

    // Returns a 2D array snapshot of the board. If revealMines is true, mines are shown even if not revealed.
    public synchronized CellView[][] getBoardData(boolean revealMines) {
        CellView[][] view = new CellView[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Cell cell = cells[r][c];
                boolean showMine = (revealMines || cell.revealed) && cell.mine;
                int adjacent = cell.revealed ? cell.adjacent : 0;
                view[r][c] = new CellView(cell.revealed, cell.flagged, showMine, adjacent);
            }
        }
        return view;
    }

    private void checkWin() {
        int revealedCount = 0;
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) if (cells[r][c].revealed) revealedCount++;
        if (revealedCount == rows * cols - totalMines) {
            won = true;
            gameOver = true;
        }
    }

    public synchronized boolean canReveal(int row, int col) {
        if (!inBounds(row, col) || gameOver) return false;
        Cell cell = cells[row][col];
        return !cell.revealed && !cell.flagged;
    }

    public synchronized boolean canToggleFlag(int row, int col) {
        if (!inBounds(row, col) || gameOver) return false;
        return !cells[row][col].revealed;
    }


    public int getRows() { return rows; }
    public int getCols() { return cols; }
    public int getTotalMines() { return totalMines; }
    public boolean isGameOver() { return gameOver; }
    public boolean isWon() { return won; }
}
