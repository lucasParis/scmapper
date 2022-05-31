
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
	var < isRadio;

	//bus and bus mapper players
	var bus;
	var busMapSynths;
	var busMapBusses;


	//for function callback
	var <> functionSet;

	var <> dataOut;

	// for automate
	var <> disableAutomation;
	var automateValue;
	var automationWasSet;
	var automateTime;
	var automationIsHappening;
	var automationRoutine;
	var automationEndTime;
	var automationStartTime;
	var <> valueInternalChangeCallback;


	//for meta
	var < valueType;
	var intSteps;
	var metaFactor;


	// for prep
	var preparedValue;
	var <> disablePrepjump;

	//for shift
	var shiftStartValue;
	var <> disableShift;

	//for randomize
	var randomValue;
	var randomStartValue;
	var <> disableRandom;
	var <> randomThresh;

	//for fade to Prep
	var fadePrepStartValue;
	var <> disableFadeToPrep;

	//for radio mode
	var < radioCount;

	//for preset morph
	var presetMorphStartValue;

	// for fast prep
	var fastPrepWasSet;
	var fastPrepValue;

	var <> sendToTD;

	*new{
		arg ctrlName, defaultValue, postFix, valueType = \float, parentGroupName = nil;// \bool \int
		^super.new.init(ctrlName, defaultValue, postFix, valueType, parentGroupName);
	}

	disableAllMeta_{
		disableFadeToPrep = true;
		disableRandom = true;
		disableShift = true;
		disablePrepjump = true;
		disableAutomation = true;
	}

	valueType_ {
		arg type, intStepsArg = 2;
		valueType = type;
		intSteps = intStepsArg;
	}

	init{
		arg ctrlName, defaultValue_, postFix_, valueType_, parentGroupName_;
		name = ctrlName.asSymbol;
		value = defaultValue_;
		postFix = postFix_.asSymbol;
		isRadio = false;
		defaultValue = defaultValue_;
		valueType = valueType_;

		oscAddr = "/" ++ parentGroupName_ ++ "/" ++ name ++ postFix;
		oscAddr = oscAddr.asSymbol;

		randomThresh = 0.98;
		sendToTD = true;

		fastPrepValue = value;
		fastPrepWasSet = false;

		//prep
		preparedValue = defaultValue;
		disablePrepjump = false;

		disableFadeToPrep = false;

		//for meta
		metaFactor = 1;


		//setup control bus
		bus = Bus.control(Server.local, defaultValue_.size.max(1));
		bus.set(defaultValue_);

		busMapBusses = [];
		busMapSynths = [];

		//automate
		automationWasSet = false;
		disableAutomation = false;
		automateTime = 8;
		automationIsHappening = false;

		//shift
		shiftStartValue = defaultValue;
		disableShift = false;

		//random
		randomValue = rrand(0.0,1.0);
		randomStartValue = defaultValue;
		disableRandom = false;

		//preset Morph
		presetMorphStartValue = defaultValue;

		//to td
		SCM.dataOutputs.do{
			arg tdOut;
			tdOut.chop.sendMsg(("/controls" ++ oscAddr).asSymbol, *value);//append /controls
		};


	}

	isRadio_{
		arg radioValue, radioCount_ = 4;
		isRadio = radioValue;
		radioCount = radioCount_;
		// this.disableAllMeta_;
		this.valueType_(\radio);
	}

	free {
		bus.free;
		busMapSynths.do{arg synth; synth.free};
		busMapBusses.do{arg bus; busMapBusses.free};
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
		arg min = 0, max = 1, curve = 0, lagUp = 0, lagDown = 0, index = nil;
		var return, outBus, busMap;

		//create a control mapper synth
		outBus = Bus.control(Server.local, bus.numChannels);
		busMapBusses = busMapBusses.add(outBus);
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

	asSignal{
		^In.kr(bus);
	}

	pfunc{
		arg index = nil;
		var return;

		if(index != nil)
		{
			return = Pfunc{value[index]};
			if(isRadio == true)
			{
				return = Pfunc{value[index] * (radioCount-1)};
			};
		}
		{
			return = Pfunc{value};
			if(isRadio == true)
			{
				return = Pfunc{value * (radioCount-1)};
			};
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
		if(sendToTD == true){
			SCM.dataOutputs.do{
				arg tdOut;
				tdOut.chop.sendMsg(("/controls" ++ oscAddr).asSymbol, *value);//append /controls
			};
		};
	}


	midiButtonMap_{
		arg index, toggleMode = false;
		MIDIFunc.cc(
			{
				arg midiValue;
				if(toggleMode)
				{
					if(midiValue>64)
					{
						this.hardSet(1 - value);
					};

				}
				{
					this.hardSet((midiValue>64).asInt);
				};
				valueInternalChangeCallback.(name, postFix, \normal);
		},index, 0);

	}

	//meta control stuff
	jump{
		if(disablePrepjump.not)
		{
			if(automationIsHappening)
			{
				this.stopAutomation;
			};
			this.hardSet(preparedValue);
		};
	}

	stopAutomation{
		automationIsHappening = false;
		automationRoutine.stop;
	}

	setAutomationTime{
		arg autoTime;
		automateTime = autoTime;
	}

	//automate
	checkForAutomationAndGo{
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
							var automationProgress, automationProgressValue;

							automationProgress = (SCM.proxySpace.clock.beats - automationStartTime)/thisAutomationTime;

							automationProgressValue = automationProgress.linlin(0,1,startValue, endValue);


							this.hardSet(automationProgressValue);

							//notify datastructure(s)

							valueInternalChangeCallback.(name, postFix, \normal);

							if(SCM.proxySpace.clock.beats > automationEndTime)
							{
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
			automationWasSet = false;
		};
		// inAutomateMode = false;
	}

	enterFastPrep{
		fastPrepWasSet = false;
	}

	exitFastPrep{
		if(fastPrepWasSet == true)
		{
			if(automationIsHappening)
			{
				this.stopAutomation;
			};
			this.hardSet(fastPrepValue);
			valueInternalChangeCallback.(name, postFix, \normal);

		};
	}


	//controller/datastructure model
	getValueByInteractionMethod{
		arg interactionMethod;
		var returnVal;

		returnVal = case
		{interactionMethod == \normal}
		{
			value;
		}{interactionMethod == \fastPrep}
		{
			value;
		}{interactionMethod == \prepare}
		{
			preparedValue;
		}{interactionMethod == \automate}
		{
			value;
		};

		//radio mode for controllers only
		isRadio.if({
			var radioValue;
			radioValue = Array.fill(32,0);
			returnVal = returnVal * (radioCount-1);
			radioValue[returnVal] = 1;
			returnVal = radioValue;
		}
		);


		^returnVal;
	}

	startShiftUpDown{
		shiftStartValue = value;
	}

	//remove?
	endShiftUpDown{

	}


	currentToPrep{
		preparedValue = value;
		valueInternalChangeCallback.(name, postFix, \prepare);
	}

	startFadeToPrep{
		fadePrepStartValue = value;
	}

	fadeToPrep{
		arg amount;

		if(disableFadeToPrep != true)
		{

			if(valueType == \float)
			{
				if(automationIsHappening)
				{
					this.stopAutomation;
				};
				value = amount.lincurve(0,1,fadePrepStartValue, preparedValue,1);
				this.hardSet(value);
			};

			if(valueType == \radio)
			{
				if(automationIsHappening)
				{
					this.stopAutomation;
				};
				value = amount.lincurve(0,1,fadePrepStartValue, preparedValue,1).round(1/(radioCount-1));
				this.hardSet(value);
			};

			if(valueType == \int)
			{
				if(automationIsHappening)
				{
					this.stopAutomation;
				};
				value = amount.lincurve(0,1,fadePrepStartValue, preparedValue,1).round(1/intSteps);
				this.hardSet(value);
			};

			if(valueType == \bool)
			{
				if(amount > 0.98)
				{
					value = preparedValue;
				}
				{
					value = fadePrepStartValue;
				};

				this.hardSet(value);
			};
		};

	}

	startPresetMorph{
		presetMorphStartValue = value;
	}

	presetMorph{
		arg amount, value;

		if(valueType == \float)
			{
				if(automationIsHappening)
				{
					this.stopAutomation;
				};
				value = amount.lincurve(0,1,presetMorphStartValue, value,1);
				this.hardSet(value);
			};

			if(valueType == \radio)
			{
				if(automationIsHappening)
				{
					this.stopAutomation;
				};
				value = amount.lincurve(0,1,presetMorphStartValue, value,1).round(1/(radioCount-1));
				this.hardSet(value);
			};

			if(valueType == \int)
			{
				if(automationIsHappening)
				{
					this.stopAutomation;
				};
				value = amount.lincurve(0,1,presetMorphStartValue, value,1).round(1/intSteps);
				this.hardSet(value);
			};

			if(valueType == \bool)
			{
				if(amount > 0.98)
				{
					value = value;
				}
				{
					value = presetMorphStartValue;
				};

				this.hardSet(value);
			};

	}


	startRandomize{
		randomStartValue = value;
		randomValue = rrand(0.0,1.0);

		if(value.size > 0)
		{
			randomValue = value.size.collect{rrand(0.0,1.0)};
		};
		//factor to reduce influence
		randomValue = metaFactor.linlin(0,1,randomStartValue, randomValue);

	}

	randomize{
		arg amount;

		if(disableRandom != true)
		{
			if(valueType == \float)
			{
				if(automationIsHappening)
				{
					this.stopAutomation;
				};
				value = amount.lincurve(0,1,randomStartValue, randomValue,2);
				this.hardSet(value);
			};
			if(valueType == \int)
			{
				if(automationIsHappening)
				{
					this.stopAutomation;
				};
				value = amount.lincurve(0,1,randomStartValue, randomValue,2).round(1/intSteps);
				this.hardSet(value);
			};

			if(valueType == \radio)
			{
				if(automationIsHappening)
				{
					this.stopAutomation;
				};
				value = amount.lincurve(0,1,randomStartValue, randomValue,2).round(1/(radioCount-1));
				this.hardSet(value);
			};

			if(valueType == \bool)
			{
				if(amount > randomThresh)
				{
					value = randomValue.round(1);
				}
				{
					value = randomStartValue;
				};
				this.hardSet(value);

			};
		};
	}


	metaFactor_{
		arg metaInfluenceFactor;
		metaFactor = metaInfluenceFactor;
	}


	shiftUpDown{
		arg shiftAmount;
		var shiftVal;

		//reduce influence
		shiftAmount = shiftAmount * metaFactor;

		if(disableShift != true)
		{
			if(valueType == \float)
			{
				shiftVal = (shiftStartValue + shiftAmount).clip(0,1);
				if(automationIsHappening)
				{
					this.stopAutomation;
				};
				this.hardSet(shiftVal);
			};

			if(valueType == \int)
			{
				shiftVal = (shiftStartValue + shiftAmount).clip(0,1);
				shiftVal = shiftVal.round(1/intSteps);
				if(automationIsHappening)
				{
					this.stopAutomation;
				};
				this.hardSet(shiftVal);
			};

			if(valueType == \radio)
			{
				shiftVal = (shiftStartValue + shiftAmount).clip(0,1);
				shiftVal = shiftVal.round(1/(radioCount-1));
				if(automationIsHappening)
				{
					this.stopAutomation;
				};
				this.hardSet(shiftVal);
			};
		};
	}

	setValueByInteractionMethod{
		arg val, interactionMethod;
		// val.postln;
		//radio mode
		if(isRadio == true)
		{
			var radioValue;

			radioValue = val.find([1]);
			(radioValue != nil).if{
				val = radioValue/(radioCount-1);
			};
		};
		// val.postln;

		case
		{interactionMethod == \normal}
		{
			value = val;
			if(automationIsHappening)
			{
				this.stopAutomation;
			};

			this.hardSet(value);
		}{interactionMethod == \prepare}
		{
			preparedValue = val;
		}{interactionMethod == \fastPrep}
		{
			fastPrepValue = val;
			fastPrepWasSet = true;
		}{interactionMethod == \automate}
		{
			// automateValue = val;
			automateValue = val;
			automationWasSet = true;
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
							//automationCallback.(name, postFix)

							if(SCM.proxySpace.clock.beats > automationEndTime)
							{
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