package sc.gui.view

import javafx.beans.binding.Bindings
import javafx.beans.value.ObservableValue
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseButton
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import org.slf4j.LoggerFactory
import sc.gui.AppStyle
import sc.gui.controller.GameController
import sc.plugin2022.Coordinates
import sc.plugin2022.GameState
import sc.plugin2022.PieceType
import sc.plugin2022.util.Constants
import tornadofx.*

// this custom class is required to be able to shrink upsized images back to smaller sizes
// see: https://stackoverflow.com/a/35202191/9127322
class ResizableImageView(sizeProperty: ObservableValue<Number>, resource: String): ImageView() {
    init {
        imageProperty().bind(sizeProperty.objectBinding {
            val size = it!!.toDouble()
            Image(resource, size, size, true, false)
        })
        fitWidthProperty().bind(sizeProperty)
        fitHeightProperty().bind(sizeProperty)
    }
    
    override fun prefHeight(width: Double): Double = image.height
    override fun minHeight(width: Double): Double = 16.0
    override fun prefWidth(height: Double): Double = image.width
    override fun minWidth(width: Double): Double = 16.0
    override fun isResizable(): Boolean = true
}

class PieceImage(private val sizeProperty: ObservableValue<Number>, private val content: PieceType): StackPane() {
    init {
        addChild(content.toString().toLowerCase())
    }
    
    fun setHeight(height: Int) {
        while (children.size < height) {
            addChild("blank")
        }
        if(height < children.size) {
            children.remove(0, children.size - height)
        }
    }
    
    fun addChild(graphic: String) {
        val childIndex = children.size + 1
        children.add(0, ResizableImageView(sizeProperty, ResourceLookup(this)["/graphics/$graphic.png"]).apply {
            translateYProperty().bind(sizeProperty.doubleBinding(children) { - (it!!.toDouble() / 10) * (children.size - childIndex) })
        })
    }
    
    override fun toString(): String = "PieceImage@${Integer.toHexString(hashCode())}(content = $content)"
}

class BoardView: View() {
    private val logger = LoggerFactory.getLogger(BoardView::class.java)
    
    private val gameController: GameController by inject()
    val pieces = HashMap<Coordinates, PieceImage>()
    
    val size = doubleProperty(16.0)
    val calculatedBlockSize = size.doubleBinding { (it!!.toDouble() / Constants.BOARD_SIZE) * 0.9 }
    
    val grid = gridpane {
        isGridLinesVisible = true
        paddingAll = AppStyle.spacing
        maxHeightProperty().bind(size)
        maxWidthProperty().bind(size)
        val listener = ChangeListener<GameState?> { _, oldState, state ->
            if (state == null)
                return@ChangeListener
            oldState?.board?.diff(state.board)?.forEach {
                pieces[it.start]?.gridpaneConstraints {
                    if (it.destination.isValid) {
                        removePiece(it.destination)
                        pieces[it.destination] = pieces.remove(it.start)!!
                        // TODO animate
                        //  children.remove(piece)
                        //  piece.move(Duration.seconds(1.0), move)
                        columnRowIndex(it.destination.x, it.destination.y)
                    } else {
                        removePiece(it.start)
                    }
                }
            }
            state.board.forEach { (coords, piece) ->
                pieces.computeIfAbsent(coords) {
                    createPiece(coords, piece.type).also {
                        add(it, coords.x, coords.y)
                    }
                }
            }
            val iter = pieces.iterator()
            while (iter.hasNext()) {
                val (c, image) = iter.next()
                val piece = state.board[c]
                if (piece == null) {
                    children.remove(image)
                    iter.remove()
                } else {
                    image.setHeight(piece.count)
                    //image.disableProperty().set(piece.team != state.currentTeam)
                    image.opacity = if (piece.team == state.currentTeam) 0.9 else 0.5
                }
            }
        }
        gameController.gameState.addListener(listener)
        listener.changed(null, null, gameController.gameState.value)
        for (x in 0 until Constants.BOARD_SIZE) {
            constraintsForRow(x).percentHeight = 100.0 / Constants.BOARD_SIZE
            constraintsForColumn(x).percentWidth = 100.0 / Constants.BOARD_SIZE
        }
    }
    override val root = vbox(alignment = Pos.CENTER) {
        size.bind(Bindings.min(widthProperty(), heightProperty()))
        grid.vgrow = Priority.ALWAYS
        add(grid)
    }
    
    private fun removePiece(coords: Coordinates): Boolean =
            pieces.remove(coords)?.let { grid.children.remove(it) } ?: false
    
    private fun getPane(x: Int, y: Int): Node =
            grid.children.find { node ->
                GridPane.getColumnIndex(node) == x && GridPane.getRowIndex(node) == y
            } ?: throw Exception("Pane of ($x, $y) is not part of the BoardView")
    
    private fun createPiece(coordinates: Coordinates, type: PieceType): PieceImage =
            PieceImage(calculatedBlockSize, type).apply {
                setOnMouseEntered {
                    addClass(AppStyle.hoverColor)
                    it.consume()
                }
                setOnMouseExited {
                    removeClass(AppStyle.hoverColor)
                    it.consume()
                }
                setOnMouseClicked {
                    if (it.button == MouseButton.PRIMARY) {
                        logger.debug("Clicked on pane $coordinates")
                        handleClick(coordinates)
                        it.consume()
                    }
                }
            }
    
    fun handleClick(coordinates: Coordinates) {
        /* TODO human move actions
        if(!game.atLatestTurn.value)
            return
        if (isHoverable(x, y, game.selectedCalculatedShape.get()) && isPlaceable(x, y, game.selectedCalculatedShape.get())) {
            logger.debug("Set-Move from GUI at [$x,$y] seems valid")
            val color = game.selectedColor.get()
            
            val move = SetMove(Piece(color, game.selectedShape.get(), game.selectedRotation.get(), game.selectedFlip.get(), Coordinates(x, y)))
            GameRuleLogic.validateSetMove(board.get(), move)
            fire(HumanMoveAction(move))
        } else {
            logger.debug("Set-Move from GUI at [$x,$y] seems invalid")
        }*/
    }
}
