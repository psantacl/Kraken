var Drum = function(audioData) {
    var my = { position : 0 };
    var that = {};

    that.mineAudioData = function( requestedSize ) {
        if (my.position + requestedSize > audioData.length) {
            requestedSize = audioData.length - my.position;
        }
        console.log("audio data: " + audioData );
        console.log("mining " + requestedSize + " from  " + my.position + " out of " +  audioData.length);

        var minedData = audioData.slice( my.position, my.position + requestedSize);
        my.position += requestedSize;
        return minedData;
    };

    that.reset = function() {
        my.position = 0;
    }


    return that;
}