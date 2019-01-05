SCMCtrl {
	var type, value, oscAddr, min, max, curve, bus;
	var <name;
	var <>parentGroup;
	var postFix;

	//for proxy
	var <>proxyNodeName;
	var <>proxyCtrlName;

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

		oscAddr = "/" ++ parentGroup.name ++ "/" ++ name ++ postFix;
		oscAddr = oscAddr.asSymbol;

		proxyCtrlName = ctrlName ++ postFix.asString.replace("/", "_");
		proxyCtrlName = proxyCtrlName.asSymbol;

		//setup control bus
		bus = Bus.control(Server.local, defaultValue.size.max(1));
		min = 0;
		max = 1;

		//add osc listerners
		this.setupOscListeners();

		this.updateFeedback();
	}

	busMap{
		var return;
		(bus.numChannels == 1).if{
					return = bus.asMap; //return bus map
				}
				{
					//if multichannel bus mapping, return an array of sc bus map strings ["c1", "c2", ...]
					return = bus.numChannels.collect{arg i; ("c" ++ (bus.index + i).asString).asSymbol};
				};
		^return;
	}

	pfunc{
		^Pfunc{value};
	}

	set{
		arg val;
		//set value
		value = val;

		//set bus value
		bus.set(*(value.linlin(0,1,min,max)));

		//set proxy value
		if(proxyNodeName != nil)
		{
			SCM.proxySpace[proxyNodeName].set(proxyCtrlName, value);
		};

		//update osc outputs
		this.updateFeedback();

	}

	updateFeedback{
		//update osc outputs
		// SCM.controllers.do{
		// 	arg ctrlr;
		// 	ctrlr.set(path, value)//for midi if a param is mapped, store relation path->encoder/button
		// }

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

				//set (when not in metactrl mode)
				this.set(value);

		}, oscAddr);
	}

}