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

import akka.typed.SupervisorStrategy.resume
import akka.typed.scaladsl.Actor
import akka.typed.scaladsl.Actor.supervise
import akka.typed.{ ActorRef, Behavior, Terminated }
import org.apache.logging.log4j.scala.Logging

object Universe extends Logging {

  val Name = "Universe"

  def getGame(gameId: String)(replyTo: ActorRef[GetGameReply]): GetGame = GetGame(gameId, replyTo)

  def getGames()(replyTo: ActorRef[GetGamesReply]): GetGames = GetGames(replyTo)

  def createGame()(replyTo: ActorRef[GameCreatedReply]): CreateGame = CreateGame(replyTo)

  def apply(playerGateway: PlayerGateway): Behavior[Command] = running(playerGateway, 0, Map.empty)

  private def running(playerGateway: PlayerGateway,
                      numberOfGames: Int,
                      games: Map[String, ActorRef[Game.Command]]): Behavior[Command] =
    Actor
      .immutable {
        case (ctx, CreateGame(replyTo)) =>
          val gameSupervision = supervise(Game(playerGateway)).onFailure[Exception](resume)
          val game: ActorRef[Game.Command] =
            ctx.spawn(gameSupervision, s"${Game.Name}-$numberOfGames")

          ctx.watch(game)
          replyTo ! GameCreated(numberOfGames.toString)

          running(playerGateway, numberOfGames + 1, games + (numberOfGames.toString -> game))

        case (_, GetGames(replyTo)) =>
          replyTo ! Games(games.keys.toList)
          Actor.same

        case (_, GetGame(gameId, replyTo)) =>
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

  final case class CreateGame(replyTo: ActorRef[GameCreatedReply]) extends Command

  final case class GetGames(replyTo: ActorRef[GetGamesReply]) extends Command

  final case class GetGame(gameId: String, replyTo: ActorRef[GetGameReply]) extends Command

  sealed trait GameCreatedReply

  final case class GameCreated(id: String) extends GameCreatedReply

  sealed trait GetGamesReply

  final case class Games(games: List[String]) extends GetGamesReply

  sealed trait GetGameReply

  final case class GameRef(game: ActorRef[Game.Command]) extends GetGameReply

  final case object GameNotFound extends GetGameReply

}
