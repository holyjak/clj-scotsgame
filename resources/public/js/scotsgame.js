function projector_poll(currentState){
    var xhr = new XMLHttpRequest();
    xhr.open('GET', '/poll/projector-state?current=' + currentState, true);
    xhr.onload = function() {
        console.log("projector state changed from", currentState, "to", this.responseText);
        document.location.reload();
        //projector_poll(resp); // not needed - will be retriggered from the newly loaded document
    };
    xhr.send();
}
