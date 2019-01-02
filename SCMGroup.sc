SCMGroup {
	var <name;
	var <controls;
	var <patterns;

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
		pat = SCMPattern.new(patternName, pattern);// , groupName)
		pat.parentGroup = this;
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


}