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
	}

	init{
		arg groupName;
		name = groupName;

		//setup array to hold controls, arrays and proxies
		controls = [];
		patterns = [];
		proxies = [];

		isPlaying = false;

		//setup OSC mappings
		this.setupOscListeners();
	}

	newCtrl{
		arg name, defaultValue = 0, postFix = "/x";
		var ctrl;

		//new ctrl
		ctrl = SCMCtrl.new(name, defaultValue, postFix, this);
		// add control to this group
		controls = controls.add(ctrl);
		^ctrl;//return
	}

	//add a pattern to this group
	linkPattern{
		arg patternName, pattern;
		var pat;
		//new pattern
		pat = SCMPattern.new(patternName, pattern, this);
		// add pattern to this group
		patterns = patterns.add(pat);
		^pat;//return
	}

	//add a proxy to this group
	linkProxy{
		arg proxyName, function;
		var proxy;
		//new proxy
		proxy = SCMProxy.new(proxyName, function, this);
		//add proxy to this group
		proxies = proxies.add(proxy);
		^proxy;//return
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