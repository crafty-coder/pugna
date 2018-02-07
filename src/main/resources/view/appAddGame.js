window.addEventListener('load', function () {

    let app = new Vue({
        el: '#app',
        data: {
            showSuccess: false,
            showError: false,
            errorText: "",
            isAddingNewGame: false,
            gameName: "",
        },
        computed: {
            isAddGameButtonDisabled: function () {
                return this.gameName.length < 4 ||
                    this.isAddingNewGame
            }
        },
        methods: {
            addGame() {
                this.isAddingNewGame = true;
                this.$http.post("/games", {
                    name: this.gameName
                })
                    .then(
                        () => {
                            this.gameName = "";
                            this.showNewPlayer = false;
                            this.isAddingNewGame = false;
                            this.showSuccess = true;
                            window.location = "/"
                        }, response => {
                            this.isAddingNewGame = false;
                            this.showError = true;
                            this.errorText = response.body;
                            console.log("Error while adding game");
                            console.log(response);
                        });
            }
        }
    })
});
