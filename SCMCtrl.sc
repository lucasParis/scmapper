SCMCtrl {
	var type, value, oscAddr, min, max, curve, bus;
	var <name;
	var <>parentGroup;
	var postFix;
	var <>proxyNodeName;
	var <>proxyCtrlName;

	// var
	// OSCFunc

	*new{
		arg ctrlName, defaultValue, postFix, parent;
		^super.new.init(ctrlName, defaultValue, postFix, parent);
		// ^this;
		// ^super.new;

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

		this.setupOscListeners();

		//add osc listerners

	}

	busMap{

	}

	pfunc{
		^Pfunc{value};
	}

	set{
		arg val;
		//set value
		value = val;

		//set bus value


		//set proxy value
		if(proxyNodeName != nil)
		{
			SCM.proxySpace[proxyNodeName].set(proxyCtrlName, value);
		}

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
				(value.size == 1).if{value = value[0]};//if it's an array of 1 element convert from array to single value
				value.postln;

				this.set(value);
		}, oscAddr);
	}

}