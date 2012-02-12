var Kraken = (function () {
                  var self = {};

                  self.drums = [];
                  self.audioData = function(payload) {
                      self.drums.push( Drum(payload.data) );
                  };

                  self.drumList = function(payload) {
                      for (var i = 0; i < 4; i++) {
                          $.each( payload.value, function(idx, drum) {
                                      var nascentOption = $('<option>').attr('value', drum.name).html( drum.name );
                                      if (drum.clip == i) {
                                          nascentOption.attr('selected', true);
                                      }
                                      $('#drum-list-' + i).append( nascentOption );
                                  });
                      }
                  };

                  self.setUpSin = function() {
                      try {
                          var iterations = 0;
                          var sampleNumber = 0;
		          var context = new (window.AudioContext || webkitAudioContext);
                          var node = context.createJavaScriptNode(4096,0,1);
                          node.onaudioprocess = function(e) {
                              var outputBuffer	= e.outputBuffer;
			      var channelCount	= outputBuffer.numberOfChannels;
                              var sampleRate       = context.sampleRate;


                              console.log('numberOfChannels:' + channelCount);
                              console.log('sampleRate:' + sampleRate);

                              for( var j = 0; j < 1; j++){
                                  var data = outputBuffer.getChannelData(j);
                                  //console.log('data.length:' + data.length);
                                  for( var i = 0; i < data.length; i++) {
                                      var radiansPerSample = (Math.PI * 2.0 * 440.0)/sampleRate;
                                      data[i] = Math.sin(sampleNumber++ * radiansPerSample);
                                      //data[i] = Math.sin(sampleNumber++ /  (sampleRate / (Math.PI * 2.0 * 220.0)));
                                      //                                    console.log(sampleNumber);
                                  }
                              }

                              iterations++;
                              if (iterations === 30) {
                                  iterations = 0;
                                  node.disconnect();
                              }

                          };

                          $('a#try-me').click( function(e) {
                                                   node.connect(context.destination);
                                               });

                      } catch (x) {
                          console.log("Not on Chrome. Can't use Web Audio");
                      }
                  };

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

                      //console.log( "Received patternChange: step(" + step  + ") instrument(" + instrument + ") checked(" + checked + ")");
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

                  self.socket = new WebSocket("ws://localhost:8081/commands");

                  self.bindSteps = function() {
                      $('[name="pattern"]').each( function (index,box) {
                                                      var step = box.value.split(',')[0];
                                                      var instrument = box.value.split(',')[1];
                                                      $(box).change( function(e) {
                                                                         var message = JSON.stringify( { 'command' : 'patternChange',
                                                                                                         'payload' : { 'checked' : box.checked, 'instrument' : instrument, 'step' : step } });
                                                                         Kraken.socket.send( message );
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
                              Kraken.socket.send( message );
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
                                                      Kraken.socket.send(message);
                                                  }
                                                 });
                  };

                  self.bindDrumList = function () {
                      console.log("Sending drum selection");
                      var drumChangeCb = function (i) {
                          return function( ) {
                              var newDrum = $('#drum-list-' + i + ' option:selected').attr('value');
                              var message = JSON.stringify( {'command' : 'drumChange', 'payload' : { 'instrument' : i, 'drum' : newDrum } } );
                              Kraken.socket.send(message);
                          }
                      };

                      for (var i=0; i < 4; i++) {
                          $('#drum-list-' + i).change( drumChangeCb(i) );
                      }
                  };

                  self.transportButtons = function() {
                      $("button#play").button();
                      $("button#play").click( function() {
                                                  var message = JSON.stringify( { 'command' : 'play', 'payload' : null });
                                                  Kraken.socket.send(message);
                                              });

                      $("button#stop").button();
                      $("button#stop").click( function() {
                                                  var message = JSON.stringify( { 'command' : 'stop', 'payload' : null });
                                                  Kraken.socket.send(message);
                                              });

                  };

                  self.buildAndBindBlueSliders = function() {
                      console.log('building blue sliders');
                      var makeCallback = function(i) {
                          return function(event,ui) {
                              var message = JSON.stringify( { 'command' : 'blue', 'payload' : { 'instrument' : i, 'value' : ui.value } });
                              Kraken.socket.send( message );
                          };
                      };

                      for (var i = 0; i < 4; i++) {
                          var newDiv = $('<div>').attr('id', 'blue-slider-' + i).css('width', '200px').css('margin-left', '2em');
                          $('#algorithm-control-' + i).append( newDiv );
                          $('#blue-slider-' + i).slider( { max : 65535, min : 0, step: 1, value: 0 });
                          $('#blue-slider-' + i).bind( 'slide', makeCallback(i));
                      }
                  };

                  self.bindAlgorithms = function() {
                      $('#algorithm').change( function () {
                                                  var algo = $('#algorithm option:selected').attr('value');
                                                  console.log('algorithm changed: ' + algo);
                                                  switch (algo)
                                                  {
                                                  case 'blue':
                                                      self.buildAndBindBlueSliders();
                                                      break;

                                                  case 'vanilla':
                                                      for (var i=0; i<4;i++) {
                                                          $('#algorithm-control-' + i).html('');
                                                      }
                                                      break;
                                                  }
                                              } );

                  };


                  return self;
              })();


var sync;
$(document).ready( function() {
                       try {
                           Kraken.socket.onopen = function() {
                               console.log("web socket is open");
                               console.log("socket status: " + Kraken.socket.readyState );
                           }

                           Kraken.socket.onmessage = function(msg) {
                               //console.log("data received: " + msg.data + " socket status: " + Kraken.socket.readyState);
			       console.log( msg.data );
                               var parsedMsg = JSON.parse( msg.data );

                               for (var prop in Kraken) {
                                   if (parsedMsg.command == prop) {
                                       Kraken[prop]( parsedMsg.payload );
                                   }
                               }
                           }

                       } catch(exception) {
                           alert("Exception initializing web socket");
                       }

                       Kraken.bindSteps();
                       Kraken.transportButtons();
                       Kraken.bindTempoSliders();
                       Kraken.bindVolumeSliders();
                       Kraken.bindAlgorithms();
                       Kraken.bindDrumList();
                       Kraken.setUpSin();

                   } );//ready


