/*
	a midi controller like this provides functionnality when:
	- receiving midi (set feedback led, calculate speed, set parameter through callback)
	- value externally changes (presets/other controllers)
*/

~makeTwister = {

	MIDIClient.init;//initialise SC midi
	MIDIIn.connectAll;//connect

	~twisterHues =  [10, 70, 84, 24];//color choice for each 4 rows

	m = nil;//midi output for twister

	//try to init midi output
	{m = MIDIOut.newByName("Midi Fighter Twister", "Midi Fighter Twister");}.try{"failed to connect to midiout".postln;};
	//check if succefull
	(m != nil).if
	{
		m.latency = 0;//disable midi latency

		//init data structure for 16 encoders
		~twister = ( lastTime: SystemClock.seconds, lastValue:0, count:0, normValue:0, speed:1, button:0, toggle:1, knobCallback: nil, buttonCallback:nil, set:nil, setButton:nil, mapped:0)!16;

		//loop though each encoder
		~twister.do{
			arg item, i;//item: data structure, i: encoder count
			var hue, hues;

			//setup colors
			hues = ~twisterHues;
			hue = hues[(i/4).floor];//get hue for current encoder
			m.control(chan:1,ctlNum:i,val:hue); //send hue midi message to twister (channel 1)
			m.control(chan:2,ctlNum:i,val:item[\button].linlin(0,1,27,47));//send button value to twister
			m.control(chan:2,ctlNum:i,val:18);//turn off led ring / disable this encoder // 18

			// knob midi callback
			MIDIFunc.cc(
				{
					arg value;
					var time, speed;

					// calculate speed
					time = SystemClock.seconds;//get time
					speed = (SystemClock.seconds-item[\lastTime]);//substract from last time
					item[\lastTime] = SystemClock.seconds;//set last time
					speed = speed.reciprocal.linexp(0,300,0.2,3);//scale speed

					value = (value-64);//format midi in to -1 > 1
					item[\count] = item[\count] + (value*speed);// set value scaled by speed
					item[\count] = item[\count].clip(0,127);// clip to min max
					item[\normValue] = item[\count].linlin(0,127,0,1); //normalise to 0 -> 1

					//send led value if the parameter is mapped, ie: has a callback
					(item[\knobCallback] != nil).if{
						m.control(chan:0,ctlNum:i,val:item[\count].wrap(0,128).round);//send value to led ring
					};

					//call knobcallback -- set in parameter setup
					item[\knobCallback].isKindOf(Function).if{//if it exists and is a function
						item[\knobCallback].value(item[\normValue]);//callback
					};
			},i, 0);

			// callback to set this encoder from outside (for example from preset recall or OSC input or map)
			item[\set] = {
				arg value;
				item[\count] = value.linlin(0,1,0,127);//set count from normalised value/denormalise
				item[\normValue] = item[\count].linlin(0,127,0,1);//renormalise
				m.control(chan:0,ctlNum:i,val:item[\count].wrap(0,128).round);//send led value
			};

			//send inital value
			m.control(chan:0,ctlNum:i,val:item[\count].wrap(0,127));

			//button midi callback
			MIDIFunc.cc(
				{
					arg value;

					//set value, toggle or momentary mode
					(item[\toggle] == 1).if
					{
						(value>1).if{item[\button] = 1-item[\button]};//toggle
					}
					{
						item[\button] = value.min(1);//momentary
					};

					//send led value
					m.control(chan:2,ctlNum:i,val:item[\button].linlin(0,1,27,47));

					//call buttoncallback -- set in parameter setup
					item[\buttonCallback].isKindOf(Function).if{
						item[\buttonCallback].value(item[\button]);
					};
				},i,1
			);

			// callback to set this button from outside (for example from preset recall or OSC input)
			item[\setButton] = {
				arg value;
				item[\button] = value.round.clip(0,1);//set button value and round to make sure it's clipped for toggle math
				m.control(chan:2,ctlNum:i,val:item[\button].linlin(0,1,27,47));//send button value to leds
			};

		};
	};
};