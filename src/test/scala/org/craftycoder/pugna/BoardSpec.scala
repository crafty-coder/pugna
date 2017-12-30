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
import org.craftycoder.pugna.Board._
import org.scalatest._

class BoardSpec extends WordSpec with Matchers {

  "Board" when {

    val player1 = Player("name1", "host1")
    val player2 = Player("name2", "host2")
    val players = Seq(player1, player2)

    "Is created" should {
      "fill itself with all the players" in {

        val context =
          new EffectfulActorContext("create-board", Board(players, 10, 5, null, null), 42, null)

        val inbox = Inbox[GetBoardStateReply]("add-player")
        context.run(GetBoardState(inbox.ref))
        val response = inbox.receiveMsg()
        response shouldBe a[BoardState]
        response.asInstanceOf[BoardState].boardSize shouldBe 10
        response.asInstanceOf[BoardState].players shouldBe players.map(_.name)
        response.asInstanceOf[BoardState].positions.size shouldBe 5 * players.size

      }
    }
  }

}
