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
            }
        },

        created() {
            this.loadGames();
        }
    })
});
