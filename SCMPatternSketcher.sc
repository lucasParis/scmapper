SCMPatternSketcherValue{
	var parent;
	var < view;
	var multislider;
	var ctrlText;
	var minMaxText;
	var currentValueText;
	var controlSpec;

	var < name;

	*new{
		arg parent, ctrl, controlSpec;
		^super.new.init(parent, ctrl, controlSpec);
	}

	init{
		arg parent_, ctrl_, controlSpec_;
		parent = parent_;
		controlSpec = controlSpec_;
		if(controlSpec == nil)
		{
			controlSpec = ControlSpec();
		};

		view = View(bounds:Rect(0,0,100,50)).background_(Color.grey(0,0.05));
		// view.focusGainedAction

		view.fixedHeight_(130);
		multislider = MultiSliderView(bounds:Rect(0,0,100,1000)).elasticMode_(1).isFilled_(true).thumbSize_(3000).fillColor_(Color.gray(0.8)).strokeColor_(Color.gray(1,0)).showIndex_(true);
		multislider.value_(controlSpec.unmap(ctrl_.defaultValue)!8);
		multislider.action_{
			arg val;
			currentValueText.string = controlSpec.map(val.value[val.index]).round(0.001).asString;
		};

		if(controlSpec_.step != 0 )
		{
			var step = controlSpec_.minval + controlSpec_.step;
			multislider.step_(controlSpec_.unmap(step));
		};

		name = ctrl_.name;
		ctrlText = StaticText(view, Rect(15,6,600,30)).string_(name).font_(Font.new(size:12));
		ctrlText.acceptsMouse = false;

		multislider.setContextMenuActions(
			// MenuAction("steps", {}),
			CustomViewAction(
				StaticText().string_("steps")
			),
			CustomViewAction(
				NumberBox().value_(multislider.value.size).action_{
					arg newSize;
					var newArray;
					// var
					newArray = multislider.value.wrapExtend(newSize.value.asInt);
					multislider.value = newArray;
				}
			),
			CustomViewAction(
				StaticText().string_("range")
			),
			CustomViewAction(
				View().layout_(HLayout(NumberBox().value_(controlSpec.minval).maxDecimals_(3).action_{arg val; this.setMin(val.value)}, NumberBox().maxDecimals_(3).value_(controlSpec.maxval).action_{arg val; this.setMax(val.value)}))
			),
			CustomViewAction(
				StaticText().string_("step")
			),
			CustomViewAction(
				NumberBox().value_(controlSpec.step).maxDecimals_(3).action_(multislider.value.size).action_{
					arg val;
					this.setStep(val.value);
				}
			),
		);

		minMaxText = StaticText(view, Rect(15,6+14,100,30)).string_(controlSpec.minval.asString + " " + controlSpec.maxval.asString ).font_(Font.new(size:7));
		minMaxText.acceptsMouse = false;
		minMaxText.front;
		currentValueText = StaticText(view, Rect(15,6+14 + 14,100,30)).string_("" ).font_(Font.new(size:9));
		currentValueText.acceptsMouse = false;
		currentValueText.front;
		ctrlText.front;

		view.layout = VLayout();
		view.layout.spacing = SCMPatternSketcher.layoutsSpacing;
		view.layout.margins = SCMPatternSketcher.layoutsMargins;

		view.layout.add(multislider);

		multislider.focusLostAction = {
			currentValueText.string = "";
		};

		// .endFrontAction
	}

	setMin{
		arg min;
		var preValues;
		preValues = controlSpec.map(multislider.value);
		controlSpec.minval = min;
		multislider.value_(controlSpec.unmap(preValues));
		minMaxText.string_(controlSpec.minval.asString + " " + controlSpec.maxval.asString );

		if(controlSpec.step != 0 )
		{
			var step = controlSpec.minval + controlSpec.step;
			multislider.step_(controlSpec.unmap(step));
		};

	}

	setMax{
		arg max;
		var preValues;
		preValues = controlSpec.map(multislider.value);
		controlSpec.maxval = max;
		multislider.value_(controlSpec.unmap(preValues));
		minMaxText.string_(controlSpec.minval.asString + " " + controlSpec.maxval.asString );

		if(controlSpec.step != 0 )
		{
			var step = controlSpec.minval + controlSpec.step;
			multislider.step_(controlSpec.unmap(step));
		};
	}

	setStep{
		arg step;
		var preValues;
		controlSpec.step = step;
		if(controlSpec.minval < controlSpec.step)
		{
			this.setMin(controlSpec.step);
		};
		preValues = multislider.value;
		if(controlSpec.step != 0 )
		{
			var step = controlSpec.minval + controlSpec.step;
			multislider.step_(controlSpec.unmap(step));
		}
		{
			multislider.step_(0);
		};
		multislider.value = preValues;

	}

	setSize{
		arg size;
		var preValues;
		preValues = multislider.value;
		multislider.value = preValues.wrapExtend(size.asInt);

	}

	setSequencerStep{
		arg count;
		multislider.index = count%multislider.value.size;
	}

	getPattern{
		^Pseq(controlSpec.map(multislider.value).round(0.0001),inf);
	}
}

SCMPatternSketcherPanel{
	var parent;
	var < view;
	var panelToolLayout;
	var loadButton;
	var instrText;
	var < ctrlPanels;
	var < selectedSynth;
	var menuButton;
	var menu;



	*new{
		arg parent;
		^super.new.init(parent);
	}

	init{
		arg parent_;
		parent = parent_;

		view = ScrollView().background_(Color.red);
		view.canvas = View(bounds:Rect(0,0,400,1000));
		panelToolLayout = HLayout();
		panelToolLayout.spacing = SCMPatternSketcher.layoutsSpacing;
		panelToolLayout.margins = SCMPatternSketcher.layoutsMargins;
		view.canvas.layout = VLayout([panelToolLayout, 0, \top], nil);
		view.canvas.layout.spacing = SCMPatternSketcher.layoutsSpacing;//SCMPatternSketcher.layoutsMargins
		view.canvas.layout.margins = SCMPatternSketcher.layoutsMargins;

		loadButton = Button().states_([["select"]]);
		loadButton.action_{
			Menu(
				*SynthDescLib.getLib(\lib1).synthDescs.keys.as(Array).collect{arg  key, i;
					MenuAction(key,
						{
							this.selectSynth(key);
						}
					);
				};
			).front;
		};
		selectedSynth = \empty;
		instrText = StaticText().string_("empty");
		ctrlPanels = ();

		menuButton = Button().states_([["menu"]]);

		menuButton.action_{
			Menu(
				MenuAction("steps"),
				CustomViewAction(NumberBox().value_(4).action_{
					arg val;
					ctrlPanels.keysValuesDo{
						arg key, panel;
						panel.setSize(val.value);
					};
				})
			).front;
		};

		panelToolLayout.add(loadButton);
		panelToolLayout.add(instrText);
		panelToolLayout.add(menuButton);

	}

	selectSynth{
		arg synthName;
		var controls, metadata, stdControls, stdControlsDict, stdControlsValues;
		instrText.string = synthName;
		selectedSynth = synthName;
		//removeOld panels
		ctrlPanels.keysValuesDo{
			arg key, value;
			value.view.remove;
		};

		ctrlPanels = ();

		controls = SynthDescLib.getLib(\lib1).synthDescs[synthName].controls;
		metadata = SynthDescLib.getLib(\lib1).synthDescs[synthName].metadata;

		// stdControls = [\dur, \degree, \octave, \amp, \legato,\lag, \timingOffset];
		// stdControlsValues = [1, 0, 3, 1, 1,0, 0];
		// stdControlsSpecs = [ControlSpec(0.25,4,step:0.25), \degree, \octave, \amp, \legato,\lag, \timingOffset];

		stdControlsDict = ();
		stdControlsDict[\dur] = (\specs: ControlSpec(0.25,2,step:0.25), \controlName: ControlName(\dur, defaultValue:1));
		stdControlsDict[\degree] = (\specs: ControlSpec(0,7,step:1), \controlName: ControlName(\degree, defaultValue:0));
		stdControlsDict[\octave] = (\specs: ControlSpec(2,8,step:1), \controlName: ControlName(\octave, defaultValue:4));
		stdControlsDict[\amp] = (\specs: ControlSpec(0,1,step:0), \controlName: ControlName(\amp, defaultValue:1));
		stdControlsDict[\legato] = (\specs: ControlSpec(0.1,1,step:0), \controlName: ControlName(\legato, defaultValue:1));
		stdControlsDict[\lag] = (\specs: ControlSpec(0,0.5,step:0), \controlName: ControlName(\lag, defaultValue:0));
		stdControlsDict[\timingOffset] = (\specs: ControlSpec(0,1,step:0), \controlName: ControlName(\timingOffset, defaultValue:0));


		stdControlsDict.keysValuesDo{
			arg key, value;
			var panel, meta;
			meta = value[\specs];
			ctrlPanels[value[\controlName].name] = SCMPatternSketcherValue.new(view, value[\controlName], value[\specs]);

			view.canvas.layout.add(ctrlPanels[value[\controlName].name].view,1);

		};

		controls = controls.reject{arg ctrl; [\amp, \seed, \freq, \out, \in, \gate, \replyID].includes(ctrl.name)};
		controls.do{
			arg ctrl, meta;
			var panel;
			meta = metadata[\specs][ctrl.name];
			ctrlPanels[ctrl.name] = SCMPatternSketcherValue.new(view, ctrl, meta);

			view.canvas.layout.add(ctrlPanels[ctrl.name].view);
		};



	}
}

SCMPatternSketcher{
	classvar window;
	classvar playbutton;
	classvar copyButton;
	classvar patternString;
	classvar toolbar;
	classvar window;
	classvar sketcherPanels;
	classvar sketcherPanelsLayout;
	classvar < layoutsMargins;
	classvar < layoutsSpacing;


	*init{
	}

	*newSketch{
		var pathSCM;
		TempoClock.default.tempo = 2;

		Quarks.installed.do({arg quark; (quark.name == "scmapper").if{pathSCM = quark.localPath};});
		(pathSCM ++ "/resourcesSC/synthlib.scd").load;


		layoutsSpacing = 3;
		layoutsMargins = 3!4;


		window = Window("pattern sketcher", Rect(100,100,1400,1000).center_(Window.availableBounds.center));
		playbutton = Button(window).states_([["play"]]);
		copyButton = Button(window).states_([["copy"]]);
		toolbar = HLayout();
		toolbar.add(playbutton, 0, \left);
		toolbar.add(copyButton, 0, \left);

		sketcherPanelsLayout = HLayout();
		sketcherPanelsLayout.spacing = SCMPatternSketcher.layoutsSpacing;
		sketcherPanelsLayout.margins = SCMPatternSketcher.layoutsMargins;

		window.layout = VLayout([toolbar,0, \align: \top], [sketcherPanelsLayout,4]);
		window.layout.spacing = SCMPatternSketcher.layoutsSpacing;
		window.layout.margins = SCMPatternSketcher.layoutsMargins;


		// window.layout.add([sketcherPanelsLayout, 1]);



		3.do{
			this.addSketcher();
		};


		copyButton.addAction(
			{
				var path, f, descSynth, outEnv;
				path = ('~/' ++ '_patternSketchPaste').standardizePath;
				f = File(path,"w");
				f.write(patternString);
				f.close;


				("pbcopy < "++path).asString.unixCmd;
			}
		);

		playbutton.addAction(
			{
				var patterns, rawPatterns, completePattern, completePatternRaw;
				patterns = [];
				rawPatterns = [];
				sketcherPanels.do{
					arg panel;
					var patternDict, pattern;
					if(panel.selectedSynth != \empty)
					{
						patternDict = ();
						//get keys
						panel.ctrlPanels.do{
							arg ctrl;
							patternDict[ctrl.name] = ctrl.getPattern;
						};

						pattern = Pbind(
							\instrument, panel.selectedSynth,
							*(patternDict.getPairs())
						);
						rawPatterns = rawPatterns.add(pattern);

						pattern = Pbindf(pattern, \seqcounter, Pseries());
						pattern = Pcollect({
							arg evt;
							var count;
							count = evt[\seqcounter];
							{panel.ctrlPanels.do{
								arg ctrl;
								ctrl.setSequencerStep(count);
							};}.defer(0);
							evt;
						}, pattern);

						patterns = patterns.add(pattern);
					}
				};

				completePattern = Ppar(patterns, inf);
				completePatternRaw = Ppar(rawPatterns, inf);
				patternString = completePatternRaw.asCompileString.replace(", '", ",    \n '").replace(" Pbind", "\n    Pbind").replace(")) ],", ")\n) ],");
				completePattern.play;
			}
		);



		window.front;

	}

	*addSketcher{
		sketcherPanels = sketcherPanels.add(SCMPatternSketcherPanel.new(window));
		sketcherPanelsLayout.add(sketcherPanels.last.view);

	}


}