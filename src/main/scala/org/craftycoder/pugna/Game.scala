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

import akka.stream.Materializer
import akka.typed.{ ActorRef, Behavior }
import akka.typed.scaladsl.Actor
import org.apache.logging.log4j.scala.Logging

object Game extends Logging {

  final val Name = "game"

  def addPlayer(player: Player)(replyTo: ActorRef[AddPlayerReply]): AddPlayer =
    AddPlayer(player, replyTo)

  def getPlayers()(replyTo: ActorRef[GetPlayersReply]): GetPlayers =
    GetPlayers(replyTo)

  def apply(players: Set[Player])(implicit mat: Materializer): Behavior[Command] =
    Actor.immutable {
      case (_, AddPlayer(player, replyTo)) =>
        if (players.exists(p => p.name == player.name || p.host == player.host)) {
          replyTo ! DuplicatePlayer
          Actor.same
        } else {
          replyTo ! PlayerAdded(player)
          Game(players + player)
        }

      case (_, GetPlayers(replyTo)) =>
        replyTo ! Players(players.map(_.name))
        Actor.same
    }

  sealed trait Command
  sealed trait Event

  final case class AddPlayer(player: Player, replyTo: ActorRef[AddPlayerReply]) extends Command
  final case class GetPlayers(replyTo: ActorRef[GetPlayersReply])               extends Command

  sealed trait AddPlayerReply
  final case object DuplicatePlayer            extends AddPlayerReply
  final case class PlayerAdded(player: Player) extends AddPlayerReply with Event

  sealed trait GetPlayersReply
  final case class Players(players: Set[String]) extends GetPlayersReply with Event

}
