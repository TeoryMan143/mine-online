package com.sisas.mineonline.connection;

import java.io.Serializable;

public record BoardAction(BoardActionType type, int row, int column) implements Serializable {
}
