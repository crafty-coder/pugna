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

    "Creating a Game and sending AddPlayer" should {

      "result in a PlayerAdded response when Player has unique name and host" in {

        val context = new EffectfulActorContext("add-player", Game(Set.empty), 42, null)

        val inbox = Inbox[AddPlayerReply]("add-player")
        context.run(AddPlayer(Player("name", "host"), inbox.ref))
        inbox.receiveMsg() shouldBe PlayerAdded(Player("name", "host"))

      }

      "result in a DuplicatePlayer response when Name is already taken" in {

        val context =
          new EffectfulActorContext("add-player",
                                    Game(Set(Player("name", "host1"))),
                                    42,
                                    null)

        val inbox = Inbox[AddPlayerReply]("add-player")
        context.run(AddPlayer(Player("name", "host2"), inbox.ref))
        inbox.receiveMsg() shouldBe DuplicatePlayer

      }

      "result in a DuplicatePlayer response when host is already taken" in {

        val context =
          new EffectfulActorContext("add-player",
                                    Game(Set(Player("name1", "host"))),
                                    42,
                                    null)

        val inbox = Inbox[AddPlayerReply]("add-player")
        context.run(AddPlayer(Player("name2", "host"), inbox.ref))
        inbox.receiveMsg() shouldBe DuplicatePlayer

      }
    }

    "Creating a Game and sending GetPlayers" should {

      "result in a Players empty when no players" in {

        val context =
          new EffectfulActorContext("get-players", Game(Set.empty), 42, null)

        val inbox = Inbox[GetPlayersReply]("get-players")
        context.run(GetPlayers(inbox.ref))
        inbox.receiveMsg() shouldBe Players(Set.empty)

      }

      "result in a Players with all the players added" in {

        val context =
          new EffectfulActorContext("get-players",
                                    Game(Set(Player("name", "host"))),
                                    42,
                                    null)

        val inbox = Inbox[GetPlayersReply]("get-players")
        context.run(GetPlayers(inbox.ref))
        inbox.receiveMsg() shouldBe Players(Set("name"))

      }

    }
  }
}
