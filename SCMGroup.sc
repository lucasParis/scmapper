SCMGroup {
	var <name;
	var <controls;
	var <patterns;
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
		arg name;
		var ctrl;
		ctrl = SCMCtrl.new(name);
		ctrl.parentGroup = this;
		controls = controls.add(ctrl);
		^ctrl;
	}

	linkPattern{
		arg patternName, pattern;
		var pat;
		pat = SCMPattern.new(patternName, pattern, this);// , groupName)
		// pat.parentGroup = this;
		patterns = patterns.add(pat);
		^pat;
	}

	newIDOverlap{

	}

	newID{

	}

	newProxy{

	}

	printOn { | stream |
		stream << "SCMGroup (" << name << ")";
	}

	setupOscListeners{
		var playAddr;
		oscAddrPrefix = "/" ++ name;
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
			}
		}
	}

	stop{
		if(isPlaying)
		{
			isPlaying = false;
			patterns.do{
				arg pattern;
				pattern.stop;
			}
		}
	}


}