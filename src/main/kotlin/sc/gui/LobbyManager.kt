package sc.gui

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sc.api.plugins.IGameState
import sc.framework.plugins.Player
import sc.gui.controller.client.ClientInterface
import sc.networking.clients.*
import sc.plugin2021.GamePlugin
import sc.protocol.helpers.RequestResult
import sc.protocol.requests.ControlTimeoutRequest
import sc.protocol.requests.PrepareGameRequest
import sc.protocol.responses.GamePreparedResponse
import sc.protocol.responses.ProtocolErrorMessage
import sc.protocol.responses.ProtocolMessage
import sc.server.Configuration
import sc.shared.GameResult
import sc.shared.SlotDescriptor
import java.net.ConnectException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import kotlin.system.exitProcess

data class GameStartException(val error: ProtocolErrorMessage): Exception("Failed to start game: ${error.message}")

class LobbyManager(host: String, port: Int) {
    var game: IControllableGame? = null
    
    private val lobbyListener: LobbyListener
    
    private val lobby: LobbyClient = try {
        LobbyClient(host, port)
    } catch (e: ConnectException) {
        logger.error("Could not connect to Server: " + e.message)
        exitProcess(1)
    }
    
    init {
        lobby.start()
        lobby.authenticate(Configuration.get(Configuration.PASSWORD_KEY))
        lobbyListener = LobbyListener(logger)
        lobby.addListener(lobbyListener)
    }
    
    fun startNewGame(players: Collection<ClientInterface>, prepared: Boolean, paused: Boolean, listener: IUpdateListener, onGameStarted: (error: Throwable?) -> Unit, onGameOver: (roomId: String, result: GameResult) -> Unit): Future<String> {
        logger.debug("Starting new game (prepared: {}, paused: {}, players: {})", prepared, paused, players)
        val result = CompletableFuture<String>()
        val observeRoom = { roomId: String ->
            result.complete(roomId)
            this.lobbyListener.setGameOverHandler(roomId) { result -> onGameOver(roomId, result) }
            game = lobby.observeAndControl(roomId, paused).apply { addListener(listener) }
        }
        
        if (prepared) {
            val requestResult = lobby.prepareGameAndWait(PrepareGameRequest(
                GamePlugin.PLUGIN_UUID,
                SlotDescriptor("One", false),
                SlotDescriptor("Two", false),
                paused
            ))
            
            when (requestResult) {
                is RequestResult.Success -> {
                    val preparation = requestResult.result
                    observeRoom(preparation.roomId)
                    players.forEachIndexed { i, player -> player.joinPreparedGame(preparation.reservations[i]) }
                    onGameStarted(null)
                }
                is RequestResult.Error ->
                    onGameStarted(GameStartException(requestResult.error))
            }
        } else {
            val iter = players.iterator()
            val join = {
                if (iter.hasNext())
                    iter.next().joinAnyGame()
                else
                    onGameStarted(null)
            }
            lobbyListener.onAnyJoin { roomId ->
                logger.debug("LobbyManager started room $roomId")
                lobbyListener.onJoin(roomId, join)
                observeRoom(roomId)
                players.indices.forEach {
                    lobby.send(ControlTimeoutRequest(roomId, false, it))
                }
                join()
            }
            join()
        }
        return result
    }
    
    companion object {
        private val logger = LoggerFactory.getLogger(LobbyManager::class.java)
    }
}

typealias GameOverHandler = (GameResult) -> Unit

class LobbyListener(val logger: Logger): ILobbyClientListener {
    
    private val gameOverHandlers = HashMap<String, GameOverHandler>()
    
    private val roomsJoined = HashMap<String, Int>()
    private val waiters: MutableMap<String?, MutableCollection<(String) -> Unit>> = HashMap()
    
    override fun onNewState(roomId: String, state: IGameState) {
        logger.debug("lobby: new state for $roomId")
    }
    
    override fun onError(roomId: String, error: ProtocolErrorMessage) {
        logger.debug("lobby: new error for $roomId")
    }
    
    override fun onRoomMessage(roomId: String, data: ProtocolMessage) {
        logger.debug("lobby: new message for $roomId")
    }
    
    override fun onGamePrepared(response: GamePreparedResponse) {
        logger.debug("lobby: game was prepared")
    }
    
    override fun onGameLeft(roomId: String) {
        logger.debug("lobby: $roomId game was left")
    }
    
    override fun onGameJoined(roomId: String) {
        roomsJoined[roomId] = roomsJoined.getOrDefault(roomId, 0) + 1
        (waiters[roomId].orEmpty() + waiters.remove(null).orEmpty())
            .forEach { it(roomId) }
        logger.debug("lobby: $roomId game was joined ($roomsJoined)")
    }
    
    /** The callback is called once with a roomId as soon as a player joins. */
    fun onAnyJoin(callback: (String) -> Unit) {
        waiters.getOrPut(null) { mutableListOf() }.add(callback)
    }
    
    /** Calls the specified [callback] whenever a client joins into [roomId]. */
    fun onJoin(roomId: String, callback: () -> Unit) =
        waiters.getOrPut(roomId) { mutableListOf() }.add { callback() }
    
    /** @return number of received game joins in the specified room, or total if [roomId] is null. */
    fun getJoinsInRoom(roomId: String? = null) =
        roomId?.let {
            roomsJoined[it]
        } ?: roomsJoined.values.sum()
    
    override fun onGameOver(roomId: String, data: GameResult) {
        logger.debug("lobby: $roomId game is over")
        gameOverHandlers[roomId]?.invoke(data)
    }
    
    override fun onGamePaused(roomId: String, nextPlayer: Player) {
        logger.debug("lobby: $roomId game was paused")
    }
    
    override fun onGameObserved(roomId: String) {
        logger.debug("lobby: $roomId game was observed")
    }
    
    fun setGameOverHandler(roomId: String, handler: GameOverHandler) {
        gameOverHandlers[roomId] = handler
    }
    
}
