
BusMapper {
	*kr {
		arg busIn, busOut, channels, min, max, curve;
		^Out.kr(busOut, In.kr(busIn, channels).lincurve(0,1,min,max,curve));
	}
}

// SCMMenuCtrl {
//
// }


SCMMetaCtrl {
	var < value;

	var oscAddr, colorAddr;
	var <name;
	// var <>parentGroup;
	var < postFix;

	//for proxy
	var proxyNodeName;
	var proxyCtrlName;

	var defaultValue;
	var isRadio;

	//bus and bus mapper players
	var bus;
	var busMapSynths;

	//for function callback
	var <> functionSet;

	// for automate
	var <> disableAutomation;

	*new{
		arg ctrlName, defaultValue, postFix;
		^super.new.init(ctrlName, defaultValue, postFix);
	}

	init{
		arg ctrlName, defaultValue_, postFix_;
		name = ctrlName.asSymbol;
		value = defaultValue_;
		postFix = postFix_.asSymbol;
		isRadio = false;
		defaultValue = defaultValue_;

		//setup control bus
		bus = Bus.control(Server.local, defaultValue_.size.max(1));


	}

	setupProxyControl
	{
		arg nodeName, ctrlName;
		proxyNodeName = nodeName;
		proxyCtrlName = ctrlName;
	}

	play{
		busMapSynths.do{ arg synth; synth.run(true);};
	}

	stop{
		busMapSynths.do{ arg synth; synth.run(false);};
	}

	// simple interfaces to SCM content
	busMap{
		arg min = 0, max = 1, curve = 0, lagUp = 0, lagDown = 0;
		var return, outBus, busMap;

		//create a control mapper synth
		outBus = Bus.control(Server.local, bus.numChannels);
		busMap = {Out.kr(outBus, In.kr(bus, bus.numChannels).lincurve(0,1,min,max,curve).lag(lagUp, lagDown) ); }.play;//bus mapper synthdef
		busMapSynths = busMapSynths.add(busMap);

		//calculate busmap array if needed
		(outBus.numChannels == 1).if{
			return = outBus.asMap; //return bus map
		}
		{
			//if multichannel bus mapping, return an array of sc bus map strings ["c1", "c2", ...]
			return = outBus.numChannels.collect{arg i; ("c" ++ (outBus.index + i).asString).asSymbol};
		};
		//return
		^return;
	}

	pfunc{
		arg index = nil;
		var return;

		if(index != nil)
		{
			return = Pfunc{value[index]};
		}
		{
			return = Pfunc{value};
		}
		^return;
	}

	hardSet{
		arg val, toFunction = true;
		//set value
		value = val;

		//set bus value
		bus.set(*value);

		//set proxy value
		if(proxyNodeName != nil)
		{
			SCM.proxySpace[proxyNodeName].set(proxyCtrlName, value);
		};

		if( functionSet != nil)
		{
			if(toFunction)
			{
				functionSet.value(value);
			};
		};

		//to td
		SCM.dataOutputs.do{
			arg tdOut;
			tdOut.chop.sendMsg(("/controls" ++ oscAddr).asSymbol, *value);//append /controls
		};

		// update osc outputs
		// this.updateFeedback(val, toCtrlrs);
	}


	//controller/datastructure model
	getValueByInteractionMethod{
		arg interactionMethod;
		var returnVal;

		returnVal = case
		{interactionMethod == \normal}
		{
			value;
		}{interactionMethod == \prepare}
		{
			// preparedValue;
		}{interactionMethod == \automate}
		{
			// automateValue;
		};

		//radio mode for controllers only
		/*isRadio.if({
			var radioValue;
			radioValue = Array.fill(32,0);
			radioValue[value] = 1;
			value = radioValue;
		}
		);*/


		^returnVal;
	}

	setValueByInteractionMethod{
		arg val, interactionMethod;
		case
		{interactionMethod == \normal}
		{
			value = val;
			this.hardSet(value);
		}{interactionMethod == \prepare}
		{
			// preparedValue = val;
		}{interactionMethod == \automate}
		{
			// automateValue = val;
		};
	}
}

SCMCtrl {
	var < value;
	var type, oscAddr, colorAddr;
	var <name;
	var <>parentGroup;
	var < postFix;

	var defaultValue;
	var isRadio;

	var <> ignoreFeedback;

	//for midi
	var midiListener;
	var midiListenerEncButton;
	var midiType;

	//for midi encoder
	var lastTime;
	var midiCount;
	var midimapped;

	//bus and bus mapper players
	var bus;
	var busMapSynths;

	//for proxy
	var <>proxyNodeName;
	var <>proxyCtrlName;

	//for prep jump

	var inPrepMode;
	var jumpedInPrepMode;
	var prePrepValue;
	var preparedValue;

	var normalColor;
	var prepareColor;
	var automateColor;


	// for automate
	var <> disableAutomation;
	var <> disablePrepjump;

	var inAutomateMode;
	var automateValue;
	var automateTime;
	var automationWasSet;

	var automationIsHappening;
	var automationEndTime;
	var automationStartTime;
	var automationRoutine;

	//for function callback
	var <> functionSet;

	//presets
	var presets;


	var <> menuFeedbackIndex;

	*new{
		arg ctrlName, defaultValue, postFix, parent;
		^super.new.init(ctrlName, defaultValue, postFix, parent);
	}

	init{
		arg ctrlName, defaultValue_, postFix_, parent;
		name = ctrlName.asSymbol;
		parentGroup = parent;
		value = defaultValue_;
		postFix = postFix_.asSymbol;
		isRadio = false;
		defaultValue = defaultValue_;

		oscAddr = "/" ++ parentGroup.name ++ "/" ++ name ++ postFix;
		oscAddr = oscAddr.asSymbol;

		colorAddr = "/" ++ parentGroup.name ++ "/" ++ name;
		colorAddr = colorAddr.asSymbol;


		proxyCtrlName = ctrlName ++ postFix.asString.replace("/", "_");
		proxyCtrlName = proxyCtrlName.asSymbol;

		//ignoreFeedback, for parameters that don't respond well to feedback
		ignoreFeedback = false;

		//for midi
		lastTime = 0;
		midimapped = -1;

		//lemur colors
		prepareColor = 8336384;
		normalColor = 4868682;
		automateColor = 2129688;

		//setup control bus
		bus = Bus.control(Server.local, defaultValue_.size.max(1));

		//prep variables
		inPrepMode = false;
		preparedValue = value;
		jumpedInPrepMode = false;
		disablePrepjump = false;

		/*
		if(name == 'volume')
		{
		disablePrepjump = true;
		};*/

		//automate variables
		inAutomateMode = false;
		automateTime = 8;
		automationWasSet = false;

		automationIsHappening = false;
		automationEndTime = 0;
		automationStartTime = 0;
		disableAutomation = false;

		menuFeedbackIndex = nil;

		presets = ();

		//add osc listerners
		this.setupOscListeners();





		this.updateFeedback(value);
	}

	busMap{
		arg min = 0, max = 1, curve = 0, lagUp = 0, lagDown = 0;
		var return, outBus, busMap;

		//create a control mapper synth
		outBus = Bus.control(Server.local, bus.numChannels);
		busMap = {Out.kr(outBus, In.kr(bus, bus.numChannels).lincurve(0,1,min,max,curve).lag(lagUp, lagDown) ); }.play;//bus mapper synthdef
		busMapSynths = busMapSynths.add(busMap);

		//calculate busmap array if needed
		(outBus.numChannels == 1).if{
			return = outBus.asMap; //return bus map
		}
		{
			//if multichannel bus mapping, return an array of sc bus map strings ["c1", "c2", ...]
			return = outBus.numChannels.collect{arg i; ("c" ++ (outBus.index + i).asString).asSymbol};
		};
		//return
		^return;
	}

	play{
		busMapSynths.do{ arg synth; synth.run(true);};
	}

	stop{
		busMapSynths.do{ arg synth; synth.run(false);};
	}

	pfunc{
		arg index = nil;
		var return;

		if(index != nil)
		{
			return = Pfunc{value[index]};
		}
		{
			return = Pfunc{value};
		}
		^return;
	}

	loadPresetToPrep{
		arg presetNumber;
		preparedValue = presets[presetNumber];
		if(inPrepMode)
		{
			this.updateFeedback(preparedValue,toTD: false, toMidi:false);
		};

	}

	loadDefaultToPrep{
		preparedValue = defaultValue;
	}

	savePreset{
		arg presetNumber;
		presets[presetNumber] = value;
	}

	currentToPrep{
		preparedValue = value;
		if(inPrepMode)
		{
			this.updateFeedback(preparedValue,toTD: false, toMidi:false);
		};
	}

	enterPrepMode{
		//store current value for exit prep mode
		prePrepValue = value;

		//send last prepared value
		this.updateFeedback(preparedValue,toTD: false, toMidi:false);


		//set prep mode condition to reroute incoming values
		inPrepMode = true;

		// jumpedInPrepMode = false;

		//set to prep mode color
		SCM.ctrlrs.do{
			arg ctrlr;
			ctrlr.sendColorMsg(colorAddr, prepareColor);
			// ctrlr.setPhysics(colorAddr, 0);
		};

	}

	exitPrepMode{
		//return UI to value pre prep
		this.updateFeedback(value, toTD: false, toMidi:false);

		//set prep condition
		inPrepMode = false;

		//return to original color
		SCM.ctrlrs.do{
			arg ctrlr;
			ctrlr.sendColorMsg(colorAddr, normalColor);
			// ctrlr.setPhysics(colorAddr, 1);
		};
	}

	jump{
		// if(inPrepMode)
		// {
		// 	jumpedInPrepMode = true;
		// };
		if(disablePrepjump.not)
		{
			if(automationIsHappening)
			{
				this.stopAutomation;
			};
			this.set(preparedValue);
		};

	}

	enterAutomateMode{
		//set to automate color
		SCM.ctrlrs.do{
			arg ctrlr;
			ctrlr.sendColorMsg(colorAddr, automateColor);
			// ctrlr.setPhysics(colorAddr, 1);
		};
		inAutomateMode = true;

		automationWasSet = false;
	}

	exitAutomateMode{
		var startValue, endValue, timeTo;

		//if automation was set
		if(disableAutomation.not){
			if(automationWasSet)
			{
				var thisAutomationTime;
				thisAutomationTime = automateTime;

				if(automationIsHappening)
				{
					this.stopAutomation;
				};

				//start automation
				automationIsHappening = true;
				startValue = value;
				endValue = automateValue;

				automationStartTime = SCM.proxySpace.clock.beats;
				automationEndTime = automationStartTime + thisAutomationTime;

				automationRoutine = Routine(
					{
						loop{
							var automationProgress, automationProgressValue, toCtrlrs;

							automationProgress = (SCM.proxySpace.clock.beats - automationStartTime)/thisAutomationTime;

							automationProgressValue = automationProgress.linlin(0,1,startValue, endValue);

							toCtrlrs = true;

							if(inPrepMode)
							{
								toCtrlrs = false;
							};

							if(inAutomateMode)
							{
								toCtrlrs = false;
							};


							this.set(automationProgressValue, true, toCtrlrs);

							if(SCM.proxySpace.clock.beats > automationEndTime)
							{
								"finishing".postln;
								automationIsHappening = false;
								nil.yield;
							}
							{
								(0.125).wait;
							};
						};
					}
				).play(SCM.proxySpace.clock);
			};
		};

		//color
		SCM.ctrlrs.do{
			arg ctrlr;
			if(inPrepMode)
			{
				//return to prep color
				ctrlr.sendColorMsg(colorAddr, prepareColor);
			}
			{
				//return to original color
				ctrlr.sendColorMsg(colorAddr, normalColor);
			}
		};

		//feedback
		if(inPrepMode)
		{
			this.updateFeedback(preparedValue, toTD: false, toMidi:false);
		}
		{
			this.updateFeedback(value, toTD: false, toMidi:false);
		};

		inAutomateMode = false;




	}

	setAutomateTime{
		arg time;
		automateTime = time;
	}

	set{
		arg val, toFunction = true, toCtrlrs = true;
		//set value
		value = val;

		//set bus value
		bus.set(*value);

		//set proxy value
		if(proxyNodeName != nil)
		{
			SCM.proxySpace[proxyNodeName].set(proxyCtrlName, value);
		};

		if( functionSet != nil)
		{
			if(toFunction)
			{
				functionSet.value(value);
			};
		};

		//update osc outputs
		this.updateFeedback(val, toCtrlrs);
	}

	updateFeedback{
		arg value, toCtrlrs = true, toTD = true, toMidi=true;
		var rawValue;

		rawValue = value;

		//radio mode
		isRadio.if({
			var radioValue;
			radioValue = Array.fill(32,0);
			radioValue[value] = 1;
			value = radioValue;
		}
		);

		//midi feedback
		if(toMidi)
		{
			if(midimapped > -1)
			{
				SCM.midiCtrlrs.do{
					arg midiCtrlr;
					midiCtrlr.midiout.control(chan:0,ctlNum:midimapped,val:(value*127).clip(0,128).round);
				};
			};
		};

		if(toCtrlrs)
		{
			if(ignoreFeedback.not)
			{
				//update osc outputs
				SCM.ctrlrs.do{
					arg ctrlr;
					ctrlr.sendMsg(oscAddr, value)//for midi if a param is mapped, store relation path->encoder/button
				};
			};
		};

		// if(menuFeedbackIndex != nil)
		// {
		// SCM.ctrlrs[menuFeedbackIndex].set("/masterMenu/" ++ name ++ postFix, value);
		// };

		if(toTD)
		{
			//update touchdesigner outputs
			SCM.dataOutputs.do{
				arg tdOut;
				tdOut.chop.sendMsg(("/controls" ++ oscAddr).asSymbol, *rawValue);//append /controls
			};
		};
	}

	printOn { | stream |
		stream << "SCMCtrl (" << name << ")";
	}



	augmentedSet{
		//set with the meta control stuff (for prepare, jump etc)
		arg value;
		//set (when not in metactrl mode)
		if(inAutomateMode)
		{
			automateValue = value;
			automationWasSet = true;
		}
		{
			if(inPrepMode)
			{
				preparedValue = value;
			}
			{
				if(automationIsHappening)
				{
					this.stopAutomation;
				};

				this.set(value);
			};
		};
	}

	setupOscListeners{
		//set
		OSCdef(
			oscAddr,
			{
				arg msg;
				var value;

				value = msg[1..];
				//if it's an array of 1 element convert from array to single value
				(value.size == 1).if{value = value[0]};

				//radio mode
				isRadio.if({
					var radioValue;
					radioValue = value.find([1]);
					(radioValue != nil).if{
						value = radioValue;
					}
				}
				);

				this.augmentedSet(value);



		}, oscAddr);
	}

	setPrepValue{
		arg val;
		preparedValue = val;
		if(inPrepMode)
		{
			this.updateFeedback(preparedValue, toTD: false, toMidi:false);
		}
	}

	stopAutomation{
		automationIsHappening = false;
		automationRoutine.stop;
	}


	setupMidiListener{
		arg index, type = \button;

		midiType = type;

		if(midiListener != nil)
		{
			midiListener.free;
		};

		MIDIFunc.cc(
			{
				arg midiValue;

				if(midiType == \encoder)
				{
					var time, speed, newVal;
					// calculate speed
					time = SystemClock.seconds;//get time
					speed = (SystemClock.seconds-lastTime);//substract from last time
					lastTime = SystemClock.seconds;//set last time
					speed = speed.reciprocal.linexp(0,300,0.001,0.02);//scale speed

					midiValue = (midiValue-64)*speed;//format midi in to -1 > 1
					newVal = value + midiValue;
					newVal = newVal.clip(0,1);
					this.set(newVal);
				};

				if(midiType == \button)
				{
					// midiValue.postln;
					this.set((midiValue>64).asInt);
				};
		},index, 0);

		/*if(midiType == \encoder)
		{
		//in midifighter twister: button on channel 2 turns parameter down
		MIDIFunc.cc(
		{
		arg value;
		if( value >1)
		{
		this.set(0);
		};

		},index,1
		);
		};*/

		//send feedback (should be only to a single controller)
		SCM.midiCtrlrs.do{
			arg midiCtrlr;
			midiCtrlr.midiout.control(chan:0,ctlNum:index,val:(value*127).clip(0,128).round);
			// midiCtrlr.midiout.control(chan:2,ctlNum:index,val:47);

		};
		midimapped = index;
	}

	// set isRadio
	isRadio_{
		arg radioValue;
		isRadio = radioValue;
		//update feedback with radio mode feedback
		this.updateFeedback(value);
	}

	//get isRadio
	isRadio{
		^isRadio;
	}


}