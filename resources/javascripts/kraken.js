var Cyclops = (function () {
                   var self = {};

                   self.spp = function(payload) {
                       var step = parseInt(payload);
                       console.log("Received spp(" + step + ")");
                       if (step == 0) {
                           for (var i = 0;i < 16;i++) {
                               $('#' + String(i) + '-step-indicator').hide();
                           }
                           $('#0-step-indicator').show();
                       } else {
                           for (var i = 0;i < step + 1;i++) {
                               $('#' + String(i) + '-step-indicator').show();
                           }
                       }
                   };

                   self.play = function(payload) {
                     console.log("Server says 'PLAY'");
                   };


                   self.patternChange = function(payload) {
                       var step = payload.step;
                       var instrument = payload.instrument;
                       var checked = payload.checked;

                       console.log( "Received patternChange: step(" + step  + ") instrument(" + instrument + ") checked(" + checked + ")");
                       var targetValue = step + "," + instrument;
                       $('input[type="checkbox"][value=' + targetValue + "]'").first().attr('checked', checked);
                   };

                   self.tempo = function(payload) {
                       console.log("Received tempo: bpm(" + payload.bpm + ")");
                       $('#tempo-slider').slider("value", payload.bpm);
                   };

                   self.volume = function(payload) {
                       console.log("Received volume change: instrument(" + payload.instrument + ") value(" + payload.value );
                       $('#volume-slider-' + payload.instrument).slider("value", payload.value);
                   };

                   self.socket = new WebSocket("ws://localhost:8080/async");

                   return self;
               }
)();

function bindCheckBoxEvents() {
    $('[name="pattern"]').each( function (index,box) {
                                    var step = box.value.split(',')[0];
                                    var instrument = box.value.split(',')[1];
                                    $(box).change( function() {
                                                       var message = JSON.stringify( { 'command' : 'patternChange',
                                                                                       'payload' : { 'checked' : box.checked, 'instrument' : instrument, 'step' : step } });
                                                       Cyclops.socket.send( message );
                                                })
                                });
}

function bindVolumeSliders() {
    var makeCallback = function(i) {
        return function(event,ui) {
            var message = JSON.stringify( { 'command' : 'volume', 'payload' : { 'instrument' : i, 'value' : ui.value } });
            Cyclops.socket.send( message );
            }
    };


    for (var i = 0; i < 4; i++) {
        $('#volume-slider-' + i).slider( { max : 100,
                                           min : 0,
                                           step: 1,
                                           value: 100
                                         });
        $('#volume-slider-' + i).bind( 'slide', makeCallback(i));
    }
}

function bindTempoSlider() {
    $('#tempo-slider').slider( { max : 200,
                                 min : 20,
                                 step: 1,
                                 value: 120,
                                 slide: function(event,ui) {
                                     var message = JSON.stringify( {'command' : 'tempo', 'payload' : { 'bpm' : ui.value } } );
                                     Cyclops.socket.send(message);
                                 }
                               });
}


$(document).ready( function() {
                       try {
                           Cyclops.socket.onopen = function() {
                               console.log("web socket is open");
                               console.log("socket status: " + Cyclops.socket.readyState );
                           }

                           Cyclops.socket.onmessage = function(msg) {
                               console.log("data received: " + msg.data + " socket status: " + Cyclops.socket.readyState);
                               var parsedMsg = JSON.parse( msg.data );

                               for (var prop in Cyclops) {
                                   if (parsedMsg.command == prop) {
                                       Cyclops[prop]( parsedMsg.payload );
                                       }
                               }
                           }

                       } catch(exception) {
                           alert("got an exception");
                       }


                       $("button#play").button();
                       $("button#play").click( function() {
                                                   var message = JSON.stringify( { 'command' : 'play', 'payload' : null });
                                                   Cyclops.socket.send(message);
                                               });

                       $("button#stop").button();
                       $("button#stop").click( function() {
                                                   var message = JSON.stringify( { 'command' : 'stop', 'payload' : null });
                                                   Cyclops.socket.send(message);
                                               });
                       bindCheckBoxEvents();
                       bindTempoSlider();
                       bindVolumeSliders();
                   } );


