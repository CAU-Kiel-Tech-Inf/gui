package sc.plugin2021

import sc.api.plugins.IBoard
import sc.api.plugins.IField
import sc.plugin2021.util.Constants

class Board(
    val gameField: Array<Array<Field?>>
): IBoard {

    val fields: List<Field>
        get() = gameField.flatMap { it.filterNotNull() }

    constructor() : this(Board.fillGameField()) {}

    override fun getField(x: Int, y: Int): IField =
            gameField[x][y] ?: throw IndexOutOfBoundsException("No field at ($x,$y)")

    override fun hashCode(): Int =
            gameField.contentDeepHashCode()

    companion object {

        private fun emptyGameField() = Array(Constants.BOARD_SIZE) { arrayOfNulls<Field>(Constants.BOARD_SIZE) }

        /** Fills the given [gameField] with all missing Fields for a valid Board. */
        @JvmStatic
        fun fillGameField(gameField: Array<Array<Field?>> = emptyGameField()): Array<Array<Field?>> {
            for (x in 0 until Constants.BOARD_SIZE) {
                for (y in 0 until Constants.BOARD_SIZE) {
                    if (gameField[x][y] == null) {
                        gameField[x][y] = Field(Coordinates(x, y), FieldContent.EMPTY)
                    }
                }
            }
            return gameField
        }

    }
}
