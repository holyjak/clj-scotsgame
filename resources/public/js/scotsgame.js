function pollForChange(){
    var xhr = new XMLHttpRequest();
    xhr.open('GET', '/poll/state-change', true);
    xhr.onload = function() {
        document.location.reload();
    };
    xhr.send();
}
