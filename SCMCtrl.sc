SCMCtrl {
	var type, value, oscAddr, min, max, curve, bus;
	var <name;
	var <>parentGroup;

	// var
	// OSCFunc

	*new{
		arg ctrlName;
		^super.new.init(ctrlName);
		// ^this;
		// ^super.new;

	}

	init{
		arg ctrlName;
		name = ctrlName;
		//add osc listerners

	}

	busMap{

	}

	pfunc{

	}

	printOn { | stream |
		stream << "SCMCtrl (" << name << ")";
    }

}