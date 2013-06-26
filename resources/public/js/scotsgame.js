function pollForChange(lastId, events){
    var xhr = new XMLHttpRequest();
    xhr.open('GET', '/poll/state-change?last-event-id=' + lastId + '&event-names=' + events, true);
    xhr.onload = function() {
        document.location.reload();
    };
    xhr.send();
}
function countdown(targetId){
    var t=10;
    var s=Date.now();
    var interval = setInterval(function(){
        console.log(--t);
        document.getElementById(targetId).textContent=t;
        if(t===0){
            clearInterval(interval);
        }
    }
                               , 60000);
}
