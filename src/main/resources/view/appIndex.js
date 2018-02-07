window.addEventListener('load', function () {

    let app = new Vue({
        el: '#app',
        data: {
            games: []
        },
        methods: {
            loadGames() {
                fetch("/games")
                    .then(response => response.json())
                    .then(json => {
                        this.games = json.games;
                    })
            },
            addGame() {
                this.$http.post('/games')
                    .then(
                        () => {
                            this.loadGames();
                        }, response => {
                            console.log("Error while creating a game");
                            console.log(response);
                        });
            }
        },

        created() {
            this.loadGames();
        }
    })
});
