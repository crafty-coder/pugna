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
import akka.typed.SupervisorStrategy.resume
import akka.typed.scaladsl.Actor
import akka.typed.scaladsl.Actor.supervise
import akka.typed.scaladsl.AskPattern._
import akka.typed.{ ActorRef, Behavior, Terminated }
import akka.util.Timeout
import org.apache.logging.log4j.scala.Logging
import org.craftycoder.pugna.Game.GameState

import scala.concurrent.{ ExecutionContextExecutor, Future }

object Universe extends Logging {

  val Name = "Universe"

  def getGameRef(gameId: String)(replyTo: ActorRef[GetGameRefReply]): GetGameRef =
    GetGameRef(gameId, replyTo)

  def getGames()(replyTo: ActorRef[GetGamesReply]): GetGames = GetGames(replyTo)

  def createGame(gameRequest: GameRequest)(replyTo: ActorRef[GameCreatedReply]): CreateGame =
    CreateGame(gameRequest, replyTo)

  def apply(playerGateway: PlayerGateway): Behavior[Command] = running(playerGateway, 0, Map.empty)

  case class GameRequest(name: String)

  private def running(playerGateway: PlayerGateway,
                      numberOfGames: Int,
                      games: Map[String, ActorRef[Game.Command]]): Behavior[Command] =
    Actor
      .immutable {
        case (ctx, CreateGame(gameRequest, replyTo)) =>
          val gameId = System.currentTimeMillis().toString + '-' + numberOfGames.toString
          val gameSupervision =
            supervise(Game(gameId, gameRequest.name, playerGateway)).onFailure[Exception](resume)
          val game: ActorRef[Game.Command] =
            ctx.spawn(gameSupervision, s"game-$gameId")

          ctx.watch(game)
          replyTo ! GameCreated(numberOfGames.toString)

          running(playerGateway, numberOfGames + 1, games + (gameId -> game))

        case (ctx, GetGames(replyTo)) =>
          implicit val i: Timeout                   = Timeout(100, TimeUnit.MILLISECONDS)
          implicit val sc: Scheduler                = ctx.system.scheduler
          implicit val ec: ExecutionContextExecutor = ctx.system.executionContext
          Future
            .sequence(games.map({ case (_, ref) => ref ? Game.getGameState() }))
            .map(_.map({ case g: GameState => g }).toList)
            .foreach({ gameStates =>
              replyTo ! Games(gameStates)
            })

          Actor.same

        case (_, GetGameRef(gameId, replyTo)) =>
          games.get(gameId) match {
            case Some(ref) => replyTo ! GameRef(ref)
            case _         => replyTo ! GameNotFound
          }
          Actor.same

      }
//      .onSignal({
//        case (_, Terminated(gameRef)) ⇒
//          running(playerGateway, numberOfGames, games.filterNot(game => game._2 == gameRef))
//      })

  sealed trait Command

  final case class CreateGame(gameRequest: GameRequest, replyTo: ActorRef[GameCreatedReply])
      extends Command

  final case class GetGames(replyTo: ActorRef[GetGamesReply]) extends Command

  final case class GetGameRef(gameId: String, replyTo: ActorRef[GetGameRefReply]) extends Command

  sealed trait GameCreatedReply

  final case class GameCreated(id: String) extends GameCreatedReply

  sealed trait GetGamesReply

  final case class Games(games: List[GameState]) extends GetGamesReply

  sealed trait GetGameRefReply

  final case class GameRef(game: ActorRef[Game.Command]) extends GetGameRefReply

  final case object GameNotFound extends GetGameRefReply

}
