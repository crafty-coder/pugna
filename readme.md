# Pugna

### What is pugna?

Pugna is a 2D game where the players will fight to conquest the board.

### Game Concepts

* The **board** is composed by 80x80 **positions**.
* Each player starts with 20 positions. All the positions are given in the same region.
* A **Game** is formed by **Turns**.
* A **Turn** is formed by **movements**
* A **movement** is an action performed for a player on a **Position**
* The possible movements are: **UP, DOWN, LEFT, RIGHT, UP_LEFT, UP_RIGHT, DOWN_LEFT,DOWN_RIGHT and STAY** 
* A **turn** is completed when all players have performed a **movement** for each one of his **positions**
* When a player(1) moves one of his positions(a) to the position(b) of an other player(2), the position(b) will be destroyed and the player(1) will get an additional position
* A player can not destroy is own positions.
* A player wins when it has conquest the positions of all the other players.


### How to play

In order to play one should create a player. A player is a REST API which will be the responsable for performing the movements.

In order to register a player, the player should be able to respond **"pong"** to a GET request to ${player_host}**/ping**. Otherwise the register will fail

Once the game starts, the game will send **NextMovementRequest** to a POST request to ${player_host}**/nextmove**. The expected response is a movement is a string format (**"UP"** or **"DOWN"** ) . If not correct movement is returned, **STAY** will be used


* Player example in Java/springboot -> https://github.com/crafty-coder/pugna-player-spring . Heroku -> https://pugna-player1.herokuapp.com/
* Player example in Scala/akka -> https://github.com/crafty-coder/pugna-player-akka . Heroku -> https://pugna-player2.herokuapp.com/

Game server hosted on heroku -> https://pugna.herokuapp.com/
