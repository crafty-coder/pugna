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

import akka.typed.scaladsl.{ Actor, ActorContext }
import akka.typed.{ ActorRef, Behavior }
import org.apache.logging.log4j.scala.Logging
import org.craftycoder.pugna.Board._
import scala.concurrent.duration._

object Game extends Logging {

  val Name                = "game"
  val BOARD_SIZE          = 100
  val NUM_SOLDIERS_PLAYER = 40

  def addPlayer(player: Player)(replyTo: ActorRef[AddPlayerReply]): AddPlayer =
    AddPlayer(player, replyTo)

  def getPlayers()(replyTo: ActorRef[GetPlayersReply]): GetPlayers =
    GetPlayers(replyTo)

  def startGame()(replyTo: ActorRef[StartGameReply]): StartGame =
    StartGame(replyTo)

  def getPositions()(replyTo: ActorRef[GetBoardStateReply]): GetBoardPositions =
    GetBoardPositions(replyTo)

  def apply(playerGateway: PlayerGateway): Behavior[Command] = preparation(Set.empty, playerGateway)

  private def preparation(players: Set[Player], playerGateway: PlayerGateway): Behavior[Command] =
    Actor.immutable {
      case (_, AddPlayer(player, replyTo)) =>
        if (players.exists(p => p.name == player.name || p.host == player.host)) {
          replyTo ! DuplicatePlayer
          Actor.same
        } else {
          replyTo ! PlayerAdded(player)
          preparation(players + player, playerGateway)

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
            board ! Board.NewTurn
            gameStarted(board, players)
          }
        }

      case (ctx, TurnFinished) =>
        Actor.same

      case (_, GetBoardPositions(replyTo)) =>
        replyTo ! BoardStateNotAvailable
        Actor.same

    }

  protected def createBoard(players: Set[Player],
                            playerGateway: PlayerGateway,
                            ctx: ActorContext[Game.Command]): ActorRef[Board.Command] =
    ctx.spawn(Board(players, BOARD_SIZE, NUM_SOLDIERS_PLAYER, playerGateway, ctx.self), Board.Name)

  private def gameStarted(board: ActorRef[Board.Command], players: Set[Player]): Behavior[Command] =
    Actor.immutable {
      case (ctx, TurnFinished) =>
        //TODO check for winner
        ctx.schedule(500.millis, board, Board.NewTurn)
        Actor.same
      case (_, GetBoardPositions(replyTo)) =>
        board ! GetBoardState(replyTo)
        Actor.same
      case (_, GetPlayers(replyTo)) =>
        replyTo ! Players(players.map(_.name))
        Actor.same
      case (_, AddPlayer(_, replyTo)) =>
        replyTo ! GameAlreadyStarted
        Actor.same
      case (_, StartGame(replyTo)) =>
        replyTo ! GameAlreadyStarted
        Actor.same
    }

  sealed trait Command

  final case class AddPlayer(player: Player, replyTo: ActorRef[AddPlayerReply]) extends Command
  final case class GetPlayers(replyTo: ActorRef[GetPlayersReply])               extends Command
  final case class StartGame(replyTo: ActorRef[StartGameReply])                 extends Command
  final case class GetBoardPositions(replyTo: ActorRef[GetBoardStateReply])     extends Command
  final case object TurnFinished                                                extends Command

  sealed trait AddPlayerReply
  final case object DuplicatePlayer            extends AddPlayerReply
  final case object GameAlreadyStarted         extends AddPlayerReply with StartGameReply
  final case class PlayerAdded(player: Player) extends AddPlayerReply

  sealed trait GetPlayersReply
  final case class Players(players: Set[String]) extends GetPlayersReply

  sealed trait StartGameReply
  final case object GameStarted   extends StartGameReply
  final case object TooFewPlayers extends StartGameReply

}
