SCMGroup {
	var <name;
	var <controls;

	var <patterns;
	var <proxies;

	var oscAddrPrefix;
	var isPlaying;

	*new{
		arg groupName;
		^super.new.init(groupName);
		// ^this;
		// ^super.new;

	}

	init{
		arg groupName;
		name = groupName;
		controls = [];
		patterns = [];
		isPlaying = false;
		this.setupOscListeners();

	}

	newCtrl{
		arg name, defaultValue = 0, postFix = "/x";
		var ctrl;
		ctrl = SCMCtrl.new(name, defaultValue, postFix, this);
		// ctrl.parentGroup = this;
		controls = controls.add(ctrl);
		^ctrl;
	}

	//add a pattern to this group
	linkPattern{
		arg patternName, pattern;
		var pat;
		pat = SCMPattern.new(patternName, pattern, this);
		patterns = patterns.add(pat);
		^pat;
	}

	//add a proxy to this group
	linkProxy{
		arg proxyName, function;
		var proxy;
		proxy = SCMProxy.new(proxyName, function, this);
		proxies = proxies.add(proxy);
		^proxy;
	}

	newIDOverlap{

	}

	newID{

	}

	printOn { | stream |
		stream << "SCMGroup (" << name << ")";
	}

	setupOscListeners{
		var playAddr;
		oscAddrPrefix = "/" ++ name;

		//PLAY / STOP
		playAddr = (oscAddrPrefix ++ "/menu/play/x").asSymbol;
		OSCdef(
			playAddr,
			{
				arg msg;
				if(msg[1] >0.5)
				{
					this.play();
				}
				{
					this.stop();
				}
			},
			playAddr
		);
	}

	play{
		if(isPlaying.not)
		{
			isPlaying = true;
			patterns.do{
				arg pattern;
				pattern.play;
			};

			proxies.do{
				arg proxy;
				proxy.play;
			};


		}
	}

	stop{
		if(isPlaying)
		{
			isPlaying = false;
			patterns.do{
				arg pattern;
				pattern.stop;
			};

			proxies.do{
				arg proxy;
				proxy.stop;
			};
		}
	}


}