//convert events to python format, for touchdesigner, filters types that can be problematic in python
~evtToPythonDictString =
{
	arg evt;
	var result;

	evt.removeAt(\scale);//remove scale

	result = "{";//open python dict


	evt.keysValuesDo{//loop through every key/value to format data
		arg key, value;
		result = result++"" ++'"' ++ key.asString ++ '"' ++ ": ";//append key name

		// loop through array keys to format data
		value.isKindOf(Array).if{
			value = value.collect{
				arg val;
				// if not Symbol or String or Boolean or Float or Integer, pass valid type (empty string)
				(val.isKindOf(Symbol).not).if{
					(val.isKindOf(String).not).if{
						(val.isKindOf(Boolean).not).if{
							(val.isKindOf(Float).not).if{
								(val.isKindOf(Integer).not).if{
									val =  "empty";
								};
							};
						};
					};
				};
				//format string as python string
				val.isKindOf(String).if
				{
					val =  '"'++ val.asString ++  '"';
				};
				//format symbol as python string
				val.isKindOf(Symbol).if
				{
					val =  '"'++ val.asString ++  '"';
				};
				//format boolean as integer and string
				val.isKindOf(Boolean).if
				{
					val =  val.asInteger;
				};
				//round floats, for floating point imprecision
				val.isFloat.if{
					val.round(0.001);
				};
				//return array value
				val;
			};
		};

		// __ Single value formating

		//round floats, for floating point imprecision
		value.isFloat.if{
			value = value.round(0.001);
		};
		//format boolean as integer and string
		value.isKindOf(Boolean).if
		{
			value =  value.asInteger;
		};
		//format string as python string
		value.isKindOf(String).if
		{
			value =  '"'++ value.asString ++  '"';
		};
		//format symbol as python string
		value.isKindOf(Symbol).if
		{
			value =  '"'++ value.asString ++  '"';
		};
		//filter out non valid types
		(value.isKindOf(Symbol).not).if{
			(value.isKindOf(String).not).if{
				(value.isKindOf(Boolean).not).if{
					(value.isKindOf(Float).not).if{
						(value.isKindOf(Integer).not).if{
							(value.isKindOf(Array).not).if{
								value = '"'++ "empty" ++  '"';
							};
						};
					};
				};
			};
		};

		//add value to result string
		result = result ++ value.asString ++ ", ";
	};


	//return result string
	result = result ++ "}";
};