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
            showSuccess:false,
            showError:false,
            errorText:"",
            isAddingNewPlayer: false,
            playerName: "",
            playerHost: "https://pugna-player2.herokuapp.com",
            gameId: id
        },
        computed: {
            isAddPlayerButtonDisabled: function() {
                return this.playerName.length === 0 ||
                    this.playerHost.length  === 0 ||
                    this.isAddingNewPlayer
            }
        },
        methods: {
            addPlayer() {
                this.isAddingNewPlayer = true;
                this.$http.post("/games/" + this.gameId + "/players", {
                    name: this.playerName,
                    host: this.playerHost
                })
                    .then(
                        () => {
                            this.playerName = "";
                            this.playerHost = "https://pugna-player2.herokuapp.com";
                            this.showNewPlayer = false;
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
