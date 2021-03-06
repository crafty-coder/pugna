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
            showSuccess: false,
            showError: false,
            errorText: "",
            isAddingNewPlayer: false,
            playerName: "",
            playerHost: "https://pugna-player2.herokuapp.com",
            gameId: id
        },
        computed: {
            isAddPlayerButtonDisabled: function () {
                return this.playerName.length === 0 ||
                    this.playerHost.length === 0 ||
                    this.isAddingNewPlayer
            },
            playerColor: function () {
                return this.toColor(this.playerName);
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
            },
            addPlayer() {
                this.isAddingNewPlayer = true;
                this.$http.post("/games/" + this.gameId + "/players", {
                    name: this.playerName,
                    host: this.playerHost,
                    color: this.playerColor,
                })
                    .then(
                        () => {
                            this.playerName = "";
                            this.playerHost = "https://pugna-player2.herokuapp.com";
                            this.isAddingNewPlayer = false;
                            this.showSuccess = true;
                        }, response => {
                            this.isAddingNewPlayer = false;
                            this.showError = true;
                            this.errorText = response.body;
                            console.log("Error while adding a player");
                            console.log(response);
                        });
            }
        }
    })
});
