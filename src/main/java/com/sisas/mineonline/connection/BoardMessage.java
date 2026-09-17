package com.sisas.mineonline.connection;

import com.sisas.mineonline.model.Board;

import java.io.Serializable;

public record BoardMessage(BoardMessageType type, Board board, BoardAction action) implements Serializable {

    public static BoardMessage fullBoard(Board board) {
        return new BoardMessage(BoardMessageType.FULL_BOARD, board, null);
    }

    public static BoardMessage action(BoardAction action) {
        return new BoardMessage(BoardMessageType.ACTION, null, action);
    }
}
