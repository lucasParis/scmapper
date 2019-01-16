SCMProxy {
	var name;
	var parentGroup;
	var proxySpaceName;
	var <> serverGroup;
	var fadeIn;

	*new{
		arg proxyName, function, parent, audioIn;
		^super.new.init(proxyName, function, parent, audioIn);
	}

	init{
		arg proxyName, function, parent, audioIn;

		name = proxyName;
		parentGroup = parent;

		proxySpaceName = parentGroup.name ++ "_" ++ name;
		proxySpaceName = proxySpaceName.asSymbol;

		serverGroup = Group.new(Server.local);

		fadeIn = 0;

		//if audio input is present, add it to the proxy and filter it, function's first input then becomes input
		(audioIn != nil).if
		({
			SCM.proxySpace[proxySpaceName][0] = audioIn;//add audio input
			SCM.proxySpace[proxySpaceName][1] = \filter -> function;//add filter function
		},
		{
			//otherwise just simple output
			SCM.proxySpace[proxySpaceName] = function;
		});

		SCM.proxySpace[proxySpaceName].pause;
		SCM.proxySpace[proxySpaceName].stop;

		this.mapNodeProxyControls();
	}


	mapNodeProxyControls{
		(SCM.proxySpace[proxySpaceName].isKindOf(NodeProxy)).if{
			var pairs;

			//get the pairs of ctrlnames and values from the node function
			(SCM.proxySpace[proxySpaceName].source.isKindOf(Function)).if{
				pairs = SCM.proxySpace[proxySpaceName].getKeysValues;
			};

			//if there a pairs to map
			pairs.isNil.not.if{
				//loop through pairs
				pairs.do{
					arg item, i;
					var name, value, postFix = "", ctrl, proxyCtrlName;
					//store name
					proxyCtrlName = item[0];
					proxyCtrlName = proxyCtrlName.asSymbol;

					name = item[0];
					value = item[1];

					//split into postfix
					name = name.asString.split($_);
					if(name.size > 1)
					{
						postFix = "/" ++ name[1];
					};

					//create a control and store reference to nodeproxy and proxy control
					ctrl = parentGroup.newCtrl(name[0].asSymbol, value, postFix.asSymbol);
					ctrl.proxyNodeName = proxySpaceName;
					ctrl.proxyCtrlName = proxyCtrlName;
				};
			};

		}
	}

	play{
		SCM.proxySpace[proxySpaceName].resume;
		SCM.proxySpace[proxySpaceName].play(group:serverGroup, addAction: 'addToTail', fadeTime: fadeIn);//if output//NodeProxy;
	}

	stop{
		SCM.proxySpace[proxySpaceName].stop;
		SCM.proxySpace[proxySpaceName].pause;
	}
}