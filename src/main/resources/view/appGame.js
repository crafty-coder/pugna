window.addEventListener('load', function () {

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

    const id = getURLParameter("id");


    let app = new Vue({
        el: '#app',
        data: {
            gameId: id,
            players: [],
            scores: [],
            round: 0
        },
        computed: {
            scoresComputed() {
                if (this.scores.length ===0){
                    return this.players.map(player => {
                        return {
                            name : player,
                            points : 0,
                            color : ""
                        }
                    });
                }else{
                    return this.scores;
                }
            }
        },
        methods: {
            toColor(str) {
                let hash = 0;
                for (let i = 0; i < str.length; i++) {
                    hash = str.charCodeAt(i) + ((hash << 5) - hash);
                }
                let colour = '#';
                for (let i = 0; i < 3; i++) {
                    const value = (hash >> (i * 8)) & 0xFF;
                    colour += ('00' + value.toString(16)).substr(-2);
                }
                return colour;
            }
            ,
            drawBoard(boardSize, players, positions, round) {

                this.round = round;

                const c = document.getElementById("boardCanvas");
                const ctx = c.getContext("2d");

                ctx.clearRect(0, 0, c.width, c.height);
                const squareSize = c.height / boardSize;

                let scores = players.map(function (v) {
                    return {name: v, points: 0};
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
            resize() {
                //TODO is worth?
                console.log("Resize!");
                this.$refs.pa.clientWidth = this.$refs.pa.clientWidth;
                this.$refs.pa.clientHeight = this.$refs.pa.clientWidth;
            }
            ,
            reloadData() {
                console.log("Reloading Data!");
                fetch("/games/" + this.gameId + "/board")
                    .then(response => response.json())
                    .then(data => {
                        this.drawBoard(data.boardSize, data.players, data.positions, data.round).bind(this);
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
        mounted() {
            this.resize();
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
