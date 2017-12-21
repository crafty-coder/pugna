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

import akka.typed.testkit.{ EffectfulActorContext, Inbox }
import org.scalatest.{ Matchers, WordSpec }

class GameSpec extends WordSpec with Matchers {

  import Game._

  "Game" when {

    "Preparing a Game and sending AddPlayer" should {

      "result in a PlayerAdded response when adding first player Player" in {

        val context = new EffectfulActorContext("add-player", Game(), 42, null)

        val inbox = Inbox[AddPlayerReply]("add-player")
        context.run(AddPlayer(Player("name", "host"), inbox.ref))
        inbox.receiveMsg() shouldBe PlayerAdded(Player("name", "host"))

      }

      "result in a DuplicatePlayer response when adding two players with the same name" in {

        val context = new EffectfulActorContext("add-player", Game(), 42, null)

        val inbox = Inbox[AddPlayerReply]("add-player")

        context.run(AddPlayer(Player("name", "host1"), inbox.ref))
        inbox.receiveMsg() shouldBe PlayerAdded(Player("name", "host1"))

        context.run(AddPlayer(Player("name", "host2"), inbox.ref))
        inbox.receiveMsg() shouldBe DuplicatePlayer

      }

      "result in a DuplicatePlayer response when host is already taken" in {

        val context = new EffectfulActorContext("add-player", Game(), 42, null)

        val inbox = Inbox[AddPlayerReply]("add-player")

        context.run(AddPlayer(Player("name1", "host"), inbox.ref))
        inbox.receiveMsg() shouldBe PlayerAdded(Player("name1", "host"))

        context.run(AddPlayer(Player("name2", "host"), inbox.ref))
        inbox.receiveMsg() shouldBe DuplicatePlayer

      }
    }

    "Preparing a Game and sending GetPlayers" should {

      "result in a Players empty when no players" in {

        val context = new EffectfulActorContext("get-players", Game(), 42, null)

        val inbox = Inbox[GetPlayersReply]("get-players")
        context.run(GetPlayers(inbox.ref))
        inbox.receiveMsg() shouldBe Players(Set.empty)

      }

      "result in a Players with all the players added" in {

        val context = new EffectfulActorContext("get-players", Game(), 42, null)

        val inbox1 = Inbox[AddPlayerReply]("add-player")
        context.run(AddPlayer(Player("name", "host"), inbox1.ref))
        inbox1.receiveMsg() shouldBe PlayerAdded(Player("name", "host"))

        val inbox2 = Inbox[GetPlayersReply]("get-players")
        context.run(GetPlayers(inbox2.ref))
        inbox2.receiveMsg() shouldBe Players(Set("name"))

      }

    }

    "Start" should {

      "result in GameStarted" in {
        val context = new EffectfulActorContext("starting-game", Game(), 42, null)

        val inbox = Inbox[StartGameReply]("start-game")
        context.run(StartGame(inbox.ref))
        inbox.receiveMsg() shouldBe GameStarted
      }

    }

  }
}
