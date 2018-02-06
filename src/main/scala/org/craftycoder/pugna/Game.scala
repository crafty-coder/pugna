/*
 * Copyright 2017 Carlos Peña
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.craftycoder.pugna

import java.util.concurrent.TimeUnit

import akka.actor.Scheduler
import akka.typed.scaladsl.{ Actor, ActorContext }
import akka.typed.{ ActorRef, Behavior }
import akka.util.Timeout
import org.apache.logging.log4j.scala.Logging
import org.craftycoder.pugna.Board._
import akka.typed.scaladsl.AskPattern._

import scala.concurrent.ExecutionContextExecutor
import scala.util.Success

object Game extends Logging {

  val Name                = "game"
  val BOARD_SIZE          = 50
  val NUM_SOLDIERS_PLAYER = 20
  val MAX_ROUNDS          = 1000

  def addPlayer(player: Player)(replyTo: ActorRef[AddPlayerReply]): AddPlayer =
    AddPlayer(player, replyTo)

  def getPlayers()(replyTo: ActorRef[GetPlayersReply]): GetPlayers =
    GetPlayers(replyTo)

  def startGame()(replyTo: ActorRef[StartGameReply]): StartGame =
    StartGame(replyTo)

  def restartGame()(replyTo: ActorRef[GameRestartedReply]): RestartGame =
    RestartGame(replyTo)

  def getPositions()(replyTo: ActorRef[GetBoardStateReply]): GetBoardPositions =
    GetBoardPositions(replyTo)

  def getGameState()(replyTo: ActorRef[GetGameStateReply]): GetGameState =
    GetGameState(replyTo)

  def apply(id: String, name: String, playerGateway: PlayerGateway): Behavior[Command] =
    preparation(id, name, Seq.empty, playerGateway)

  private def preparation(id: String,
                          name: String,
                          players: Seq[Player],
                          playerGateway: PlayerGateway): Behavior[Command] =
    Actor.immutable {

      case (_, AddReachablePlayer(player, replyTo)) =>
        if (players.size > 3) {
          replyTo ! TooManyPlayers
          Actor.same
        } else if (players.exists(p => p.name == player.name)) {
          replyTo ! DuplicatePlayer
          Actor.same
        } else {
          replyTo ! PlayerAdded(player)
          preparation(id, name, players :+ player, playerGateway)
        }

      case (ctx, AddPlayer(player, replyTo)) =>
        if (players.exists(p => p.name == player.name)) {
          replyTo ! DuplicatePlayer
          Actor.same
        } else {
          implicit val ec = ctx.executionContext
          playerGateway
            .ping(player)
            .andThen({
              case Success(true) => ctx.self ! AddReachablePlayer(player, replyTo)
              case _             => replyTo ! UnReachablePlayer
            })

          Actor.same

        }

      case (_, GetPlayers(replyTo)) =>
        replyTo ! Players(players.map(_.name))
        Actor.same

      case (ctx, StartGame(replyTo)) =>
        if (players.size < 2) {
          replyTo ! TooFewPlayers
          Actor.same
        } else {
          val board: ActorRef[Board.Command] = createBoard(players, playerGateway, ctx)
          replyTo ! GameStarted
          Actor.deferred { ctx =>
            board ! Board.NewRound
            gameStarted(id, name, board, players, playerGateway)
          }
        }
      case (_, RestartGame(replyTo)) =>
        replyTo ! GameRestarted
        preparation(id, name, players, playerGateway)

      case (_, RoundFinished(_, _)) =>
        Actor.same

      case (_, GetBoardPositions(replyTo)) =>
        replyTo ! BoardStateNotAvailable
        Actor.same

      case (_, GetGameState(replyTo)) =>
        val playerNames = players.map(_.name)
        replyTo ! GameState(id,
                            name,
                            players = playerNames,
                            state = Preparation.toString,
                            winner = None,
                            round = None)
        Actor.same

    }

  protected def createBoard(players: Seq[Player],
                            playerGateway: PlayerGateway,
                            ctx: ActorContext[Game.Command]): ActorRef[Board.Command] =
    ctx.spawn(Board(players, BOARD_SIZE, NUM_SOLDIERS_PLAYER, playerGateway, ctx.self), Board.Name)

  private def gameStarted(id: String,
                          name: String,
                          board: ActorRef[Board.Command],
                          players: Seq[Player],
                          playerGateway: PlayerGateway): Behavior[Command] =
    Actor.immutable {
      case (_, RoundFinished(round, positions)) =>
        if (isGameFinish(round, positions, players.size)) {
          gameFinished(id = id,
                       name = name,
                       players = players,
                       round = round,
                       positions = positions,
                       winner = winner(positions),
                       playerGateway = playerGateway)
        } else {
          board ! Board.NewRound
          Actor.same
        }

      case (_, GetBoardPositions(replyTo)) =>
        board ! GetBoardState(replyTo)
        Actor.same
      case (ctx, GetGameState(replyTo)) =>
        val playerNames = players.map(_.name)

        implicit val i: Timeout                   = Timeout(100, TimeUnit.MILLISECONDS)
        implicit val sc: Scheduler                = ctx.system.scheduler
        implicit val ec: ExecutionContextExecutor = ctx.system.executionContext

        (board ? Board.getBoardState).foreach({
          case BoardStateReply(BoardState(positions, _, round, _)) =>
            replyTo ! GameState(id,
                                name,
                                players = playerNames,
                                state = Running.toString,
                                round = Some(round),
                                positions = positions)
          case _ =>
            replyTo ! GameState(id = id,
                                name = name,
                                players = playerNames,
                                state = Running.toString)
        })

        Actor.same
      case (_, GetPlayers(replyTo)) =>
        replyTo ! Players(players.map(_.name))
        Actor.same
      case (_, AddPlayer(_, replyTo)) =>
        replyTo ! GameAlreadyStarted
        Actor.same
      case (_, AddReachablePlayer(_, replyTo)) =>
        replyTo ! GameAlreadyStarted
        Actor.same
      case (_, StartGame(replyTo)) =>
        replyTo ! GameAlreadyStarted
        Actor.same
      case (ctx, RestartGame(replyTo)) =>
        ctx.stop(board)
        replyTo ! GameRestarted
        preparation(id, name, players, playerGateway)
    }

  private def isGameFinish(round: Int, positions: Seq[Position], numOfPlayers: Int): Boolean = {
    val playerWithAllSoldiers = positions
      .groupBy(_.playerName)
      .map({ case (_, pos) => pos.size })
      .exists(_ == NUM_SOLDIERS_PLAYER * numOfPlayers)
    round > MAX_ROUNDS || playerWithAllSoldiers
  }

  private def winner(positions: Seq[Position]): String =
    positions
      .groupBy(_.playerName)
      .map({ case (name, pos) => (name, pos.size) })
      .toList
      .sortBy(_._2)(Ordering[Int].reverse)
      .headOption
      .map(_._1)
      .getOrElse("")

  private def gameFinished(id: String,
                           name: String,
                           players: Seq[Player],
                           winner: String,
                           round: Int,
                           positions: Seq[Position],
                           playerGateway: PlayerGateway): Behavior[Command] = Actor.immutable {
    case (_, GetGameState(replyTo)) =>
      val playerNames = players.map(_.name)
      replyTo ! GameState(id = id,
                          name = name,
                          round = Some(round),
                          positions = positions,
                          players = playerNames,
                          state = Finished.toString,
                          winner = Some(winner))
      Actor.same
    case (_, RoundFinished(_, _)) =>
      Actor.same
    case (_, GetBoardPositions(replyTo)) =>
      replyTo ! BoardStateNotAvailable
      Actor.same
    case (_, GetPlayers(replyTo)) =>
      replyTo ! Players(players.map(_.name))
      Actor.same
    case (_, AddPlayer(_, replyTo)) =>
      replyTo ! GameAlreadyStarted
      Actor.same
    case (_, AddReachablePlayer(_, replyTo)) =>
      replyTo ! GameAlreadyStarted
      Actor.same
    case (_, StartGame(replyTo)) =>
      replyTo ! GameAlreadyStarted
      Actor.same
    case (_, RestartGame(replyTo)) =>
      replyTo ! GameRestarted
      preparation(id, name, players, playerGateway)
  }

  sealed trait State
  final object Preparation extends State {
    override def toString: String = "Preparation"
  }
  final object Running extends State {
    override def toString: String = "Running"
  }
  final object Finished extends State {
    override def toString: String = "Finished"
  }

  sealed trait Command

  final case class AddPlayer(player: Player, replyTo: ActorRef[AddPlayerReply]) extends Command
  private final case class AddReachablePlayer(player: Player, replyTo: ActorRef[AddPlayerReply])
      extends Command
  final case class GetPlayers(replyTo: ActorRef[GetPlayersReply])           extends Command
  final case class StartGame(replyTo: ActorRef[StartGameReply])             extends Command
  final case class RestartGame(replyTo: ActorRef[GameRestartedReply])       extends Command
  final case class GetBoardPositions(replyTo: ActorRef[GetBoardStateReply]) extends Command
  final case class GetGameState(replyTo: ActorRef[GetGameStateReply])       extends Command
  final case class RoundFinished(round: Int, positions: Seq[Position])      extends Command

  sealed trait AddPlayerReply
  final case object UnReachablePlayer          extends AddPlayerReply
  final case object DuplicatePlayer            extends AddPlayerReply
  final case object TooManyPlayers             extends AddPlayerReply
  final case object GameAlreadyStarted         extends AddPlayerReply with StartGameReply
  final case class PlayerAdded(player: Player) extends AddPlayerReply

  sealed trait GetPlayersReply
  final case class Players(players: Seq[String]) extends GetPlayersReply

  sealed trait StartGameReply
  final case object GameStarted   extends StartGameReply
  final case object TooFewPlayers extends StartGameReply

  sealed trait GameRestartedReply
  final case object GameRestarted extends GameRestartedReply

  sealed trait GetGameStateReply

  final case class GameState(id: String,
                             name: String,
                             players: Seq[String],
                             boardSize: Int = BOARD_SIZE,
                             state: String,
                             winner: Option[String] = None,
                             round: Option[Int] = None,
                             positions: Seq[Position] = Seq.empty)
      extends GetGameStateReply

}
