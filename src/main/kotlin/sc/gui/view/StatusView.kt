package sc.gui.view

import javafx.beans.binding.StringBinding
import javafx.geometry.Pos
import javafx.scene.control.Label
import sc.gui.AppStyle
import sc.gui.model.GameModel
import sc.shared.GameResult
import sc.shared.ScoreCause
import tornadofx.*

class StatusBinding(private val game: GameModel): StringBinding() {
    init {
        bind(game.gameStarted, game.currentTeam, game.gameResult, game.playerNames)
    }
    
    fun winner(gameResult: GameResult): String =
            gameResult.winner?.let { player -> "$player hat gewonnen!" }
            ?: "Unentschieden!"
    
    fun irregularities(gameResult: GameResult): String? =
            gameResult.scores.values.firstNotNullOfOrNull { score ->
                when (score.cause) {
                    ScoreCause.LEFT -> "Grund: Vorzeitiges Verlassen des Spiels"
                    ScoreCause.RULE_VIOLATION -> "Grund: Regelverletzung"
                    ScoreCause.SOFT_TIMEOUT -> "Grund: Überschreitung des Zeitlimits"
                    ScoreCause.HARD_TIMEOUT -> "Grund: Keine Antwort auf Zuganfrage"
                    ScoreCause.UNKNOWN -> "Grund: Kommunikationsfehler"
                    else -> null
                }
            }
    
    override fun computeValue(): String =
            if (game.gameStarted.value)
                game.gameResult.get()?.let { gameResult ->
                    """
                    Spiel ist beendet
                    ${winner(gameResult)}
                    ${irregularities(gameResult).orEmpty()}
                    """.trimIndent()
                } ?: "${game.currentTeam.value.index.let { game.playerNames[it] ?: "Spieler ${it + 1}" }} ist dran"
            else game.playerNames.joinToString(" vs ")
}

class ScoreBinding(private val game: GameModel): StringBinding() {
    init {
        bind(game.gameStarted, game.teamScores)
    }
    
    override fun computeValue(): String =
            if (game.gameStarted.value)
                "Runde ${game.currentRound.get()} - " +
                game.teamScores.value?.joinToString(" : ")
            else "Drücke auf Start"
}

class StatusView: View() {
    private val game: GameModel by inject()
    private val scoreLabel = Label()
    private val statusLabel = Label()
    
    override val root = vbox(alignment = Pos.CENTER) {
        addClass(AppStyle.statusLabel)
        prefHeight = AppStyle.fontSizeBig.value * 6
        add(scoreLabel)
        add(statusLabel)
    }
    
    init {
        statusLabel.bind(StatusBinding(game))
        scoreLabel.bind(ScoreBinding(game))
    }
}