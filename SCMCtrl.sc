
BusMapper {
	*kr {
		arg busIn, busOut, channels, min, max, curve;
		^Out.kr(busOut, In.kr(busIn, channels).lincurve(0,1,min,max,curve));
	}
}

SCMCtrl {
	var type, value, oscAddr;
	var <name;
	var <>parentGroup;
	var postFix;

	//bus and bus mapper players
	var bus;
	var busMapSynths;

	//for proxy
	var <>proxyNodeName;
	var <>proxyCtrlName;

	var isRadio;

	*new{
		arg ctrlName, defaultValue, postFix, parent;
		^super.new.init(ctrlName, defaultValue, postFix, parent);
	}

	init{
		arg ctrlName, defaultValue, postFix_, parent;
		name = ctrlName;
		parentGroup = parent;
		value = defaultValue;
		postFix = postFix_;
		isRadio = false;

		oscAddr = "/" ++ parentGroup.name ++ "/" ++ name ++ postFix;
		oscAddr = oscAddr.asSymbol;

		proxyCtrlName = ctrlName ++ postFix.asString.replace("/", "_");
		proxyCtrlName = proxyCtrlName.asSymbol;

		//setup control bus
		bus = Bus.control(Server.local, defaultValue.size.max(1));

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
		^Pfunc{value};
	}

	set{
		arg val;
		//set value
		value = val;

		//set bus value
		bus.set(*value);

		//set proxy value
		if(proxyNodeName != nil)
		{
			SCM.proxySpace[proxyNodeName].set(proxyCtrlName, value);
		};

		//update osc outputs
		this.updateFeedback(val);
	}

	updateFeedback{
		arg value;
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


		//update osc outputs
		SCM.ctrlrs.do{
			arg ctrlr;
			ctrlr.set(oscAddr, value)//for midi if a param is mapped, store relation path->encoder/button
		};

		//update touchdesigner outputs
		SCM.dataOutputs.do{
			arg tdOut;
			tdOut.chop.sendMsg(("/controls" ++ oscAddr).asSymbol, *rawValue);//append /controls
		};
	}

	printOn { | stream |
		stream << "SCMCtrl (" << name << ")";
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

				//set (when not in metactrl mode)
				this.set(value);

		}, oscAddr);
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