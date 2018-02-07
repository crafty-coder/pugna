function resize() {
    const canvas = document.getElementById("boardCanvas");
    const canvasContainer = document.getElementById("boardContainer");
    canvas.width = canvasContainer.clientWidth * 0.5;
    canvas.height = canvasContainer.clientWidth * 0.5;
}

function getURLParameter(sParam) {
    var sPageURL = window.location.search.substring(1);
    var sURLVariables = sPageURL.split('&');
    for (var i = 0; i < sURLVariables.length; i++) {
        var sParameterName = sURLVariables[i].split('=');
        if (sParameterName[0] === sParam) {
            return sParameterName[1];
        }
    }
}


window.addEventListener('resize', function () {
    resize();
});

window.addEventListener('load', function () {

    const id = getURLParameter("id");
    resize();

    let app = new Vue({
        el: '#app',
        data: {
            gameId: id,
            gameName: "",
            players: [],
            scores: [],
            metrics: [],
            winner: "",
            state: "",
            round: 0
        },
        computed: {
            scoresComputed() {
                if (this.scores.length === 0) {
                    return this.players.map(player => {
                        return {
                            name: player.name,
                            points: 0,
                            color: player.color,
                            killingBlows: 0,
                            deaths: 0,
                            invalidMoves: 0
                        }
                    });
                } else {
                    return this.scores;
                }
            }
        },
        methods: {
            toColor(playerName) {
                return this.players.find(player => player.name === playerName).color
            },
            updateData(name, boardSize, positions, round, winner, state, metrics) {

                this.gameName = name;
                this.round = round;
                this.winner = winner;
                this.state = state;

                const c = document.getElementById("boardCanvas");
                const ctx = c.getContext("2d");

                ctx.clearRect(0, 0, c.width, c.height);
                const squareSize = c.height / boardSize;

                let scores = metrics.map(function (metric) {
                    return {
                        name: metric.name,
                        points: 0,
                        killingBlows: metric.killingBlows,
                        deaths: metric.deaths,
                        invalidMoves: metric.invalidMoves
                    };
                });

                positions.forEach(function (position) {

                    this.scores = scores.map((score) => {
                        if (score.name === position.playerName) {
                            score.points++;
                            score.color = this.toColor(score.name);
                            return score;
                        }
                        else return score;
                    });

                    ctx.fillStyle = this.toColor(position.playerName);
                    ctx.fillRect(squareSize * position.coordinate.x + (squareSize * 0.05), squareSize * (boardSize - (position.coordinate.y + 1)) + (squareSize * 0.05), squareSize * 0.9, squareSize * 0.9);
                }.bind(this));

            }
            ,
            reloadData() {
                fetch("/games/" + this.gameId + "/state")
                    .then(response => response.json())
                    .then(data => {
                        this.updateData(data.name, data.boardSize, data.positions, data.round, data.winner, data.state, data.metrics);
                    });

            }
            ,
            loadPlayers() {
                fetch("/games/" + this.gameId + "/players")
                    .then(response => response.json())
                    .then(json => {
                        this.players = json.players
                    })
            }
            ,
            startGame() {
                this.$http.put("/games/" + this.gameId + "/start").then(
                    () => {
                        console.log(("Game Started"))
                    }, response => {
                        console.error("Fail to start the game")
                    });
            }
            ,
            restartGame() {
                this.$http.put("/games/" + this.gameId + "/restart").then(
                    () => {
                        console.log(("Game Restarted"))
                    }, response => {
                        console.error("Fail to finish the game")
                    });
            }
        },
        created() {
            this.loadPlayers();
            setInterval(
                () => this.reloadData(),
                200
            );

        }
    })
});
