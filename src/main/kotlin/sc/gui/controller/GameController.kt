package sc.gui.controller

import javafx.beans.binding.BooleanBinding
import javafx.beans.binding.ObjectBinding
import javafx.beans.property.Property
import org.slf4j.LoggerFactory
import sc.gui.model.PiecesModel
import sc.gui.view.*
import sc.plugin2021.*
import sc.plugin2021.util.GameRuleLogic
import sc.shared.InvalidMoveException
import tornadofx.*
import kotlin.math.max

// The following *Binding-classes are necessary to automatically unbind and rebind to a new piece (when switched)
// in order to prevent this hassle in every other class, that uses the current selected PieceModel
class ColorBinding(piece: Property<PiecesModel>) : ObjectBinding<Color>() {
    val model = piece

    init {
        bind(model)
        bind(model.value.colorProperty())
        model.addListener { _, oldValue, newValue ->
            unbind(oldValue.colorProperty())
            bind(newValue.colorProperty())
            model.value = newValue
        }
    }

    fun set(data: Color) {
        model.value.colorProperty().set(data)
    }

    override fun computeValue(): Color {
        logger.debug("Color: ${model.value.colorProperty().get()}")
        return model.value.colorProperty().get()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PiecesFragment::class.java)
    }
}

class ShapeBinding(piece: Property<PiecesModel>) : ObjectBinding<PieceShape>() {
    val model = piece

    init {
        bind(model)
        bind(model.value.shapeProperty())
        model.addListener { _, oldValue, newValue ->
            unbind(oldValue.shapeProperty())
            bind(newValue.shapeProperty())
            model.value = newValue
        }
    }

    fun set(data: PieceShape) {
        model.value.shapeProperty().set(data)
    }

    override fun computeValue(): PieceShape {
        return model.value.shapeProperty().get()
    }
}

class RotationBinding(piece: Property<PiecesModel>) : ObjectBinding<Rotation>() {
    val model = piece

    init {
        bind(model)
        bind(model.value.rotationProperty())
        model.addListener { _, oldValue, newValue ->
            unbind(oldValue.rotationProperty())
            bind(newValue.rotationProperty())
            model.value = newValue
        }
    }

    fun set(data: Rotation) {
        model.value.rotationProperty().set(data)
    }

    override fun computeValue(): Rotation {
        return model.value.rotationProperty().get()
    }
}

class FlipBinding(piece: Property<PiecesModel>) : BooleanBinding() {
    val model = piece

    init {
        bind(model)
        bind(model.value.flipProperty())
        model.addListener { _, oldValue, newValue ->
            unbind(oldValue.flipProperty())
            bind(newValue.flipProperty())
            model.value = newValue
        }
    }

    fun set(data: Boolean) {
        model.value.flipProperty().set(data)
    }

    override fun computeValue(): Boolean {
        return model.value.flipProperty().get()
    }
}

class CalculatedShapeBinding(piece: Property<PiecesModel>) : ObjectBinding<Set<Coordinates>>() {
    val model = piece

    init {
        bind(model)
        bind(model.value.shapeProperty())
        model.addListener { _, oldValue, newValue ->
            unbind(oldValue.calculatedShapeProperty())
            bind(newValue.calculatedShapeProperty())
            model.value = newValue
        }
    }

    fun set(data: Set<Coordinates>) {
        model.value.calculatedShapeProperty().set(data)
    }

    override fun computeValue(): Set<Coordinates> {
        return model.value.calculatedShapeProperty().get()
    }
}


class GameController : Controller() {
    private val boardController: BoardController by inject()
    private var gameState: GameState = GameState()

    private var availableTurns: Int by property(0)
    private var currentTurn: Int by property(0)
    private var turnColor: Color by property(Color.RED)
    private var currentTeam: Team by property(Team.ONE)
    private var isHumanTurn: Boolean by property(false)
    private var canSkip: Boolean by property(false)
    private var gameEnded: Boolean by property(false)
    private var previousTurnColor: Color by property(Color.RED)
    private var teamOneScore: Int by property(0)
    private var teamTwoScore: Int by property(0)
    fun previousColorProperty() = getProperty(GameController::previousTurnColor)
    fun currentColorProperty() = getProperty(GameController::turnColor)
    fun currentTeamProperty() = getProperty(GameController::currentTeam)
    fun availableTurnsProperty() = getProperty(GameController::availableTurns)
    fun currentTurnProperty() = getProperty(GameController::currentTurn)
    fun isHumanTurnProperty() = getProperty(GameController::isHumanTurn)
    fun canSkipProperty() = getProperty(GameController::canSkip)
    fun gameEndedProperty() = getProperty(GameController::gameEnded)
    fun teamOneScoreProperty() = getProperty(GameController::teamOneScore)
    fun teamTwoScoreProperty() = getProperty(GameController::teamTwoScore)

    // we need to have them split separately otherwise we cannot listen to a specific color alone
    private var undeployedRedPieces: Collection<PieceShape> by property(PieceShape.shapes.values)
    private var undeployedBluePieces: Collection<PieceShape> by property(PieceShape.shapes.values)
    private var undeployedGreenPieces: Collection<PieceShape> by property(PieceShape.shapes.values)
    private var undeployedYellowPieces: Collection<PieceShape> by property(PieceShape.shapes.values)
    fun undeployedRedPiecesProperty() = getProperty(GameController::undeployedRedPieces)
    fun undeployedBluePiecesProperty() = getProperty(GameController::undeployedBluePieces)
    fun undeployedGreenPiecesProperty() = getProperty(GameController::undeployedGreenPieces)
    fun undeployedYellowPiecesProperty() = getProperty(GameController::undeployedYellowPieces)
    private var validRedPieces: ArrayList<PieceShape> by property(ArrayList())
    private var validBluePieces: ArrayList<PieceShape> by property(ArrayList())
    private var validGreenPieces: ArrayList<PieceShape> by property(ArrayList())
    private var validYellowPieces: ArrayList<PieceShape> by property(ArrayList())
    fun validRedPiecesProperty() = getProperty(GameController::validRedPieces)
    fun validBluePiecesProperty() = getProperty(GameController::validBluePieces)
    fun validGreenPiecesProperty() = getProperty(GameController::validGreenPieces)
    fun validYellowPiecesProperty() = getProperty(GameController::validYellowPieces)

    // use selected* to access the property of currentPiece in order to always correctly be automatically rebind
    private var currentPiece: PiecesModel by property(PiecesModel(Color.RED, PieceShape.MONO))
    private fun currentPieceProperty() = getProperty(GameController::currentPiece)
    var selectedColor: ColorBinding = ColorBinding(currentPieceProperty())
    var selectedShape: ShapeBinding = ShapeBinding(currentPieceProperty())
    var selectedRotation: RotationBinding = RotationBinding(currentPieceProperty())
    var selectedFlip: FlipBinding = FlipBinding(currentPieceProperty())
    var selectedCalculatedShape: CalculatedShapeBinding = CalculatedShapeBinding(currentPieceProperty())

    fun isValidColor(color: Color): Boolean = gameState.orderedColors.contains(color)

    init {
        subscribe<NewGameState> { event ->
            logger.debug("New game state")
            gameState = event.gameState
            canSkipProperty().set(false)

            /* TODO: remove */
            println("""
                ========================================================
                $gameState
                
                ${gameState.board}
                ========================================================
            """.trimIndent())
            /**/
            //throw (InvalidMoveException("the hell?"))

            // I don't know why orderedColors becomes an empty array and results in CurrentColor being inaccessible (throwing error) when the game ended,
            // but this is how we can avoid it for now TODO("fix this in the plugin")
            if (event.gameState.orderedColors.isNotEmpty()) {
                previousColorProperty().set(currentColorProperty().get())
                currentColorProperty().set(event.gameState.currentColor)
                currentTeamProperty().set(event.gameState.currentTeam)
            }
            undeployedRedPiecesProperty().set(event.gameState.undeployedPieceShapes(Color.RED))
            undeployedBluePiecesProperty().set(event.gameState.undeployedPieceShapes(Color.BLUE))
            undeployedGreenPiecesProperty().set(event.gameState.undeployedPieceShapes(Color.GREEN))
            undeployedYellowPiecesProperty().set(event.gameState.undeployedPieceShapes(Color.YELLOW))
            boardController.board.boardProperty().set(event.gameState.board)
            validRedPiecesProperty().set(ArrayList())
            validBluePiecesProperty().set(ArrayList())
            validGreenPiecesProperty().set(ArrayList())
            validYellowPiecesProperty().set(ArrayList())
            availableTurnsProperty().set(max(availableTurns, event.gameState.turn))
            currentTurnProperty().set(event.gameState.turn)
            teamOneScoreProperty().set(event.gameState.getPointsForPlayer(Team.ONE))
            teamTwoScoreProperty().set(event.gameState.getPointsForPlayer(Team.TWO))
        }
        subscribe<HumanMoveRequest> { event ->
            logger.debug("Human move request")
            isHumanTurnProperty().set(true)
            canSkipProperty().set(!gameEnded && isHumanTurn && !GameRuleLogic.isFirstMove(gameState))
            boardController.calculateIsPlaceableBoard(event.gameState.board, event.gameState.currentColor)

            when (event.gameState.currentColor) {
                Color.RED -> validRedPiecesProperty()
                Color.BLUE -> validBluePiecesProperty()
                Color.GREEN -> validGreenPiecesProperty()
                Color.YELLOW -> validYellowPiecesProperty()
            }.set(event.gameState.undeployedPieceShapes(event.gameState.currentColor).filter { shape ->
                try {
                    GameRuleLogic.validateShape(gameState, shape)
                    true
                } catch(ignored: InvalidMoveException) {
                    false
                }
            } as ArrayList<PieceShape>?)
        }
        subscribe<GameOverEvent> {
            gameEndedProperty().set(true)
        }
    }

    fun clearGame() {
        gameEndedProperty().set(false)
        boardController.board.boardProperty().set(Board())
        availableTurnsProperty().set(0)
        currentTurnProperty().set(0)
        undeployedRedPiecesProperty().set(PieceShape.values().toList())
        undeployedBluePiecesProperty().set(PieceShape.values().toList())
        undeployedGreenPiecesProperty().set(PieceShape.values().toList())
        undeployedYellowPiecesProperty().set(PieceShape.values().toList())
    }

    fun selectPiece(piece: PiecesModel) {
        currentPieceProperty().set(piece)
    }

    fun flipPiece() {
        currentPieceProperty().get().flipPiece()
    }

    fun rotatePiece(rotate: Rotation) {
        selectedRotation.set(selectedRotation.get().rotate(rotate))
    }

    fun scroll(deltaY: Double) {
        currentPieceProperty().get().scroll(deltaY)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GameController::class.java)
    }
}