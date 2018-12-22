SCMapper - supercollider mapping
====

Snippets
---
##### Create Mapper
```
~mapper = ~mapperMaker.value;
```

##### Proxy Module
```
~mapper.proxySpace[\groupname] = {};
~mapper.newProxyGroup(\groupname, listen, targetGroup);
```

##### Proxy signal to TD
```
~mapper.sendSignal(\groupname, \name, signal);
```
##### Sending synthdef modulations to TD
```
\replyID, ~mapper.newID(\groupname, numChannels, \l1_synthName)
```

##### Pattern Module
```
~mapper.newPGroup(\groupname, ~pattern, 0); //groupname, pattern, quant
```

##### Pattern mapping
```
~mapper.pMap(\groupname, \parameterName, defaultValue, \postfix)
```

##### Bus mapping
```
~mapper.bMap(\groupname, \parameterName, defaultValue, min, max \postfix)
```

##### Chain Proxy
```
~mapper.chainProxyFX(\groupname,
	{
		var src;
		src = In.ar(~mapper.pbus(\groupname),2);
	}
);
```

##### Map encoder
```
~mapper.linkEncoder(\groupname, \parameterName, encoderIndex);
```

##### Map button
```
~mapper.linkButton(\groupname, \parameterName, buttonIndex, toggleMode);
```

##### Setup preset
```
~mapper.setupPreset(\groupname, \presetName, presetIndex);
```


##### Set tempo
```
~mapper.setTempo(60);
```





Developements
---
- [ ] split into files: synthdefs, library (?)
- [ ] modularise TD components

- [ ] pbinds: wait for fadeout/last sound before stopping Bus
- [ ] check if different postfixes from single control works
- [ ] add option to have multiple busses in one group
- [ ] sending audio between groups
- [ ] autocreate control busses for pattern sequences? -> or only wanted ones
- [ ] route everything to a master bus
- [ ] rewrite hardware mapping system
- [ ] make functions for play/stop/presets etc... for future UI and automatisation
- [ ] multiple notefx chaining
- [ ] routing from proxysynth to pbind
- [ ] generic mixing/sculpting fx: lowpass, reverb... etc

- [ ] **metactrl:** interpolating through preset list, with deviation
- [ ] **metactrl:** action button, accumulates actions for simulteanous execution
- [ ] **metactrl:** shiftall up/down, randomize all
- [ ] **metactrl:** automatic trajectory (different easings) over X time
- [ ] **metactrl:** represent all controls as XY or single multifader
- [ ] **architecture:** make a diagram of current library


Templates
---

##### New Pbind Module
```

```
