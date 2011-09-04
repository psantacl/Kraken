var Cyclops = (function () {
                  var self = {};


                  self.spp = function(payload) {
                      if ( ! self.cols ) {
                        self.cols = [];
                        for (var i = 0; i < 16; i++) {
                          self.cols.push($('.column-'+ i));
                          self.cols[i].css('opacity', 0.7);
                        }
                      }

                      var step = parseInt(payload);
                      console.log("Received spp(" + step + ")");

                      var before = (step + 16 - 1) % 16;
                      //var after  = (step + 1) % 16;
                      self.cols[before].css('opacity', 0.7);
                      self.cols[step].css('opacity', 1.0);

                      //for (var i = 0; i < 16; i++) {
                      //    if (i === step) {
                      //        self.cols[i].css('opacity',1);
                      //        // $('.column-'+ step).css('opacity', 1);
                      //    } else {
                      //        self.cols[i].css('opacity',0.7);
                      //        // $('.column-'+ step).css('opacity', 0.5);
                      //    }
                      //}
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
                     var checkBox =  $('input[type="checkbox"][value=' + targetValue + "]'");
                     checkBox.first().attr('checked', checked);
                     if (checked) {
                         checkBox.parent().css('background','url(../images/radio_down.png) no-repeat');
                     } else {
                         checkBox.parent().css('background','url(../images/radio_up.png) no-repeat');
                     }
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

                  self.bindSteps = function() {
                      $('[name="pattern"]').each( function (index,box) {
                          var step = box.value.split(',')[0];
                          var instrument = box.value.split(',')[1];
                          $(box).change( function(e) {
                              var message = JSON.stringify( { 'command' : 'patternChange',
                                                              'payload' : { 'checked' : box.checked, 'instrument' : instrument, 'step' : step } });
                              Cyclops.socket.send( message );
                              e.preventDefault();
                              return false;
                          }); //change
                      }); //each

                      $('div.button').click( function(e) {
                          var chbox = $(this).find(':checkbox');
                          if (chbox[0].checked) {
                              chbox[0].checked = false;
                              chbox.trigger('change'); 
                              $(this).css('background', 'url(../images/radio_up.png) no-repeat');
                          } else {
                              chbox[0].checked = true;
                              chbox.trigger('change'); 
                              $(this).css('background', 'url(../images/radio_down.png) no-repeat');
                          }
                      });//click
                  };

                            
                  self.bindVolumeSliders = function() {
                      var makeCallback = function(i) {
                          return function(event,ui) {
                              var message = JSON.stringify( { 'command' : 'volume', 'payload' : { 'instrument' : i, 'value' : ui.value } });
                              Cyclops.socket.send( message );
                          };
                      };

                      for (var i = 0; i < 4; i++) {
                          $('#volume-slider-' + i).slider( { max : 100, min : 0, step: 1, value: 100 });
                          $('#volume-slider-' + i).bind( 'slide', makeCallback(i));
                      }
                  };

                  self.bindTempoSliders = function () {
                      $('#tempo-slider').slider( {max : 200,
                                                  min : 20,
                                                  step: 1,
                                                  value: 120,
                                                  slide:  function(event,ui) {
                                                              var message = JSON.stringify( {'command' : 'tempo', 'payload' : { 'bpm' : ui.value } } );
                                                              Cyclops.socket.send(message);
                                                          }
                                                });
                  };

                  self.transportButtons = function() {
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

                  };

                  return self;
})();


var sync;
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
                           alert("Exception initializing web socket");
                       }

                       Cyclops.bindSteps();
                       Cyclops.transportButtons();
                       Cyclops.bindTempoSliders();
                       Cyclops.bindVolumeSliders();
                      
                       var iterations = 0;     

                       $('a#try-me').click( function(e) {
                            sync = Sink( function (buffer, channelCount ) {
                                console.log('buffer size: ' + buffer.length );
                                for (var i = 0; i < buffer.length; i++) {
                                    buffer[i] = Math.sin( 1000 *  ( (Math.PI * 2)/ buffer.length) * i ) 
                                }
                                iterations += 1;
                                console.log(iterations);
                                if (iterations > 10 ) {
                                  return false;
                                } else {
                                  return true;
                                }
                            }, 1, 4096, 44100);
                       });

                   } );//ready


