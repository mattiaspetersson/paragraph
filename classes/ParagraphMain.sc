ParagraphMain { // release version
	var numSpeakers, preProcessor, <gui, <posts;
	var <server, <tempoClock, <quant, tempoNotifications, previousTempo;
	var <patterns, <ptn;
	var <patternMastersGroup, <fxSendsGroup, <fxSynthsGroup, <masterSynth;
	var midiDummyBus, <globalFxBus, <foaBus, <fxSendBusses;
	var numSpeakers, preProcessor;
	var <buffers, <samples, <patternTypes;
	var <fx, <extMidiInstr, <oscAddr, <ctrls, <hwInputs, <vstInstr;
	var <tri, <cfDict, <initConditions, <>uniqueMethods, currentMethods, <editor;
	var <argumentStrings;
	classvar <excludeMethodsFromPreProc;
	classvar <>paragraph;

	*new {|numSpeakers = 2, preProcessor = true, editor = false, posts = false|
		^super.newCopyArgs(numSpeakers, preProcessor, editor, posts).initParagraph;
	}

	*initClass {
		excludeMethodsFromPreProc = [ // started out as a good idea, but might be better to do the opposite now, i.e. list the included methods.
			\server, \tempoClock, \quant, \patterns, \ptn, \patternMastersGroup,\uniqueMethods,
			\fxSendsGroup, \fxSynthsGroup, \globalFxBus, \foaBus, \fxSendBusses,
			\buffers, \samples, \patternTypes, \extMidiInstr, \hwInputs, \vstInstr, \initConditions,
			\initParagraph, \makeValuePattern, \newPattern, \getCurrentMethods, \cf,
			\masterSynth, \editor, \posts, \prNorgaard, \prLsystem,
			\oscAddr, \gui, \ctrls, \uniqueMethods, \argumentStrings, \actionRecorder
		];

		// makes it possible to start Paragraph within the syntax.
		// TODO: possibility to add arguments
		thisProcess.interpreter.preProcessor = {|code|
			code.replace("§ start", "ParagraphMain()");
		};
	}

	initParagraph {
		paragraph = this;
		patternTypes = [\seq, \shf, \rnd, \seg, \wht, \gau, \brw, \euc, \cf, \tri, \ngd, \lsy];
		thisProcess.interpreter.preProcessor = nil;
		if(gui, {editor = ParagraphEditor(this)});
		previousTempo = 2.1;
		tri = EMPTriangle();
		quant = 1;
		uniqueMethods = [];
		ptn = ();
		buffers = ();
		samples = ();
		patterns = ();
		extMidiInstr = ();
		oscAddr = ();
		ctrls = ();
		hwInputs = ();
		vstInstr = ();
		fx = ();
		fxSendBusses = ();
		initConditions = ();
		argumentStrings = ();
		cfDict = ();

		// adding unique methods to be able to perform the same method call on all patterns
		ParagraphPattern.patternMethods[\patternMethods].do{|methodName|
			this.addUniqueMethod(methodName, {|receiver, type, valA, valB, valC, valD, valE, dup = 1|
				patterns.keysValuesDo{|ptnName, aParagraphPattern|
					aParagraphPattern.perform(methodName, type, valA, valB, valC, valD, valE, dup = 1);
				};
			});
		};

		if(MIDIClient.initialized.not, {MIDIClient.init});
		MIDIIn.connectAll;
		//Server.supernova;
		server = Server.default;
		server.options.sampleRate = 48000;
		server.latency = 0.2;
		//tempoClock = TempoClock.default;
		tempoClock = LinkClock().latency_(server.latency);
		tempoNotifications = SimpleController(tempoClock)
		.put(\tempo, {
			defer {"tempo changed to % bpm".format(tempoClock.tempo * 60).postln};
		})
		.put(\numPeers, {
			defer {"number of link peers is now %".format(tempoClock.numPeers).postln};
		});

		server.notify = true;
		server.waitForBoot{
			ParagraphMain.paragraphSynthDefs(numSpeakers, server);

			server.sync;

			patternMastersGroup = ParGroup.new(server);
			fxSendsGroup = ParGroup.after(patternMastersGroup);
			fxSynthsGroup = ParGroup.after(fxSendsGroup);
			midiDummyBus = Bus.audio(server, 2);
			globalFxBus = Bus.audio(server, 2);
			foaBus = Bus.audio(server, 4);

			server.sync;

			masterSynth = Synth.after(fxSynthsGroup, 'master', [\foaIn, foaBus, \out, 0, \recOut, 0]);

			server.sync;

			Pbindef(\global,
				\type, \set,
				\id, fxSynthsGroup.nodeID,
				\args, #[\t_trig, \freq, \beatDurInSecs],
				\t_trig, Pdef(\globalfxTrig, {Pdup(~dup ? 1, ~pattern ? 1)}),
				\freq, Pdef(\globalfxFreq, {Pdup(~dup ? 1, ~pattern ? 900)}),
				\dur, Pdef(\globaldur, {Psubdivide(~subdivide ? 1, Pdup(~dup ? 1, ~pattern ? 0.25))}),
				\beatDurInSecs, Pfunc{|ev| tempoClock.beatDur * ev.use{~dur.value.reciprocal}},
				//\callback, {|ev| this.changed(\main, ev)}
			);
		};
		if(preProcessor, {ParagraphPreProcessor.run(this)});
	}

	bpm {|bpm|
		tempoClock.tempo = bpm/60;
	}

	fxTrig {|type, valA, valB, valC, valD|
		ptn[\fxTrig] = this.makeValuePattern(type, valA, valB, valC, valD);
		Pbindef(\global, \t_trig, Pdef(\globalfxTrig) <> (pattern: ptn[\fxTrig], dup: ptn[\fxTrigdup]));
	}

	fxFreq {|type, valA, valB, valC, valD|
		ptn[\fxFreq] = this.makeValuePattern(type, valA, valB, valC, valD);
		Pbindef(\global, \freq, Pdef(\globalfxFreq) <> (pattern: ptn[\fxFreq], dup: ptn[\fxFreqdup]));
	}

	dur {|type, valA, valB, valC, valD|
		ptn[\dur] = this.makeValuePattern(type, valA, valB, valC, valD);
		Pbindef(\global, \dur, Pdef(\globaldur) <> (pattern: ptn[\dur], dup: ptn[\durdup]));
	}

	play {
		patterns.keysValuesDo{|key, val| val.play};
	}

	stop {
		patterns.keysValuesDo{|key, val| val.stop};
		Pbindef(\global).stop;
		//Pbindef(\cf).stop;
		cfDict.keysValuesDo{|key, val|
			val[\cfPtn].stop;
		};
	}

	makeValuePattern {|type, valA, valB, valC, valD, valE, dup = 1|
		var valPtn, st;

		if(patternTypes.includes(type), {
			switch(type,
				\seq, {valPtn = Pseq(valA, valB ? inf)},
				\shf, {valPtn = Pn(Pshuf(valA, valB ? inf), valC ? inf)},
				\rnd, {
					valPtn = Pwrand(// for weighted random, just supply a second array
						valA,
						if(valB.isArray, {valB.normalizeSum}, {1.dup(valA.size).normalizeSum}),
						if(valB.isNumber, {valB}, {valC ? inf})
					)
				},
				\seg, {valPtn = Pseg(
					Pseq(valA, inf),
					Pseq(valB, inf),
					Pseq(
						if(valC.notNil, {
							if(valC.isArray, {
								valC;
							}, {
								valC!valB.size;
							});
						}, {
							\lin!valB.size;
					}), inf),
					valD ? inf)
				},
				\wht, {valPtn = Pwhite(valA ? 0.0, valB ? 1.0, valC ? inf)},
				\gau, {valPtn = Pgauss(valA ? 0.0, valB ? 1.0, valC ? inf)},
				\brw, {valPtn = Pbrown(valA ? 0.0, valB ? 1.0, valC ? 0.125, valD ? inf)},
				\euc, {valPtn = Pbjorklund2(valA, valB, valC ? inf) / 4},
				\cf, {valPtn = Pfunc{cfDict[valA][\cfVal]}},
				\tri, {
					switch(valA ? \o,
						\o, {valPtn = Pseq(tri.triangle.flatten, valB ? inf)},
						\r, {valPtn = Pseq(tri.triangle.flatten.reverse, valB ? inf)},
						\i, {valPtn = Pseq(tri.triangle.flatten.invert(valC ? 0), valB ? inf)},
						\ri, {valPtn = Pseq(tri.triangle.flatten.reverse.invert(valC ? 0), valB ? inf)},
						\rows, {valPtn = Pseq(tri.triangle, valB ? inf)},
						\row, {valPtn = Pseq(tri.row(valB%8 ? 0), valC ? inf)},
						\rowR, {valPtn = Pseq(tri.reversedRow(valB%8 ? 0), valC ? inf)},
						\ptnOf, {valPtn = Pseq(tri.patternOf(valB%8 ? 1), valC ? inf)},
						\rowAsDur, {valPtn = Pseq(tri.rowAsDurations(valB%8 ? 0, valD ? 1), valC ? inf)},
						\rowAsDurR, {valPtn = Pseq(tri.reversedRowAsDurations(valB%8 ? 0, valD ? 1), valC ? inf)},
						\rowAsTrem, {valPtn = Pseq(tri.rowAsTremolo(valB%8 ? 0, valC ? 0, valD ? 4), valE ? inf)},
						\rowAsTremR, {valPtn = Pseq(tri.reversedRowAsTremolo(valB%8 ? 0, valC ? 0, valD ? 4), valE ? inf)},
						\allAsDur, {valPtn = Pseq(8.collect{|i| tri.rowAsDurations(i,  valB ? 1)}.flatten, valC ? inf)},
						\allAsDurR, {valPtn = Pseq(8.collect{|i| tri.reversedRowAsDurations(i,  valB ? 1)}.flatten, valC ? inf)},
						\allAsTrem, {valPtn = Pseq(8.collect{|i| tri.rowAsTremolo(i, valB ? 0, valC ? 4)}.flatten, valD ? inf)},
						\allAsTremR, {valPtn = Pseq(8.collect{|i| tri.reversedRowAsTremolo(i, valB ? 0, valC ? 4)}.flatten, valD ? inf)},
						\allRasTrem, {valPtn = Pseq(8.collect{|i| tri.rowAsTremolo(i, valB ? 0, valC ? 4)}.reverse.flatten, valD ? inf)}
					);
				},
				\ngd, {valPtn = Pseq(this.prNorgaard(valA ? 0, valB ? 1, valC ? 64, valD ? 0), valE ? inf)},
				\lsy, {valPtn = this.prLsystem(valA ? 3, valB ? 4, valC ? 9)}
			);
		}, {
			valPtn = Pn(type); // if type is just a value
		});
		valPtn = Pdup(dup, valPtn);
		^valPtn;
	}

	newPattern {|ptnName|
		if(patterns[ptnName].isNil, {
			initConditions[ptnName] = Condition(false);
			patterns[ptnName] = ParagraphPattern(ptnName, this);
		});
	}

	defCf {|nameOfCf, valPtn, durPtn| // both can be single values or patterns
		// create a new cf pattern and store it in the dictionary
		cfDict[nameOfCf] = (
			cfVal: [0], // the pattern reads this val. BUG: when a cf is updated on the fly this value is used until next quant.
			cfPtn: Pbindef(nameOfCf,
				\type, \rest,
				\cfVal, Pdef((nameOfCf++\prCfVal).asSymbol, {~pattern ? 0}),
				\setCfVal, Pfunc{|ev| cfDict[nameOfCf][\cfVal] = ev[\cfVal]},
				\dur, Pdef((nameOfCf++\cfDur).asSymbol, {~pattern ? 1}),
				\sustain, 1
			);
		);
		tri.mulAdd(1, -1);
		valPtn = valPtn.asArray;
		tri.mulAdd(1, 0);
		durPtn = durPtn.asArray;
		ptn[(nameOfCf++\prCfVal).asSymbol] = this.makeValuePattern(valPtn[0], valPtn[1], valPtn[2], valPtn[3], valPtn[4], valPtn[5] ? 1);
		ptn[(nameOfCf++\cfDur).asSymbol] = this.makeValuePattern(durPtn[0], durPtn[1], durPtn[2], durPtn[3], durPtn[4], durPtn[5] ? 1);

		Pbindef(nameOfCf,
			\cfVal, Pdef((nameOfCf++\prCfVal).asSymbol) <> (pattern: ptn[(nameOfCf++\prCfVal).asSymbol]),
			\dur, Pdef((nameOfCf++\cfDur).asSymbol) <> (pattern: ptn[(nameOfCf++\cfDur).asSymbol])
		);
	}

	// midi instruments are defined globally, but hwInput synths are run when the instrument is used in the respective pattern.
	// (because i want it to pass through the patternMasterBus)
	defExtMidiInstr {|name, midiOutDevice, midiOutPort, hwIn, hwInGain = 1, numChannels = \stereo|
		//var m = MIDIOut.newByNameLinux(midiOutDevice, midiOutPort).latency_(server.latency); // linux ports are handled differently
		var m = MIDIOut.newByName(midiOutDevice, midiOutPort).latency_(server.latency);
		extMidiInstr[name] = (
			midiOutPort: m,
			hwIn: hwIn,
			hwInGain: hwInGain,
			numChannels: numChannels,
			controllers: () // needs to be implemented
		);
	}

	defOscCtrl {|name, path, index, spec|
		if(ctrls[name].notNil, {
			ctrls[name][\oscFunc].free;
			ctrls[name][\bus].free;
		});
		ctrls[name] = (
			spec: spec.asSpec,
			bus: Bus.control(server, 1),
			oscFunc: OSCFunc({|msg|
				ctrls[name][\bus].set(ctrls[name][\spec].unmap(msg[index]));
			}, path)
		);
		// once defined, this can be used as a modulator in the lfo method of ParagraphPattern
	}

	defOscAddr {|name, host, port, path, type|
		oscAddr[name] = ();
		oscAddr[name][\netAddr] = NetAddr(host, port);
		oscAddr[name][\path] = path;
		oscAddr[name][\type] = type;
	}

	/*defMidiCtrl {|name, device, port, chan, num, val, spec|
	ctrls[name] = (
	MIDIClient.init;
	MIDIIn.connectAll;
	midiIn: MIDIFunc.cc({|msg|

	});
	);
	}*/

	defMidiSysex {|name, instrName, array|

	}

	defHwInput {|name, hwIn, hwInGain = 1|
		hwInputs[name] = (
			hwIn: hwIn,
			hwInGain: hwInGain,
		);
	}

	defVstInstr {|name, pathToPlugin, programNumber|
		var m, vstSynth;
		vstInstr[name] = ();
		if(vstInstr[name][\vstSynth].notNil, {vstInstr[name][\vstSynth].synth.free});
		vstSynth = VSTPluginController(Synth(\vsti));
		vstSynth.open(pathToPlugin, false, true, {|plug|
			plug.program = programNumber ? 1;
			m = plug.midi;
			vstInstr[name][\vstSynth] = vstSynth;
			vstInstr[name][\midiOutPort] = [m, 0];
			vstInstr[name][\controllers] = ();
		});
	}

	defVstFx {|name, pathToPlugin, programNumber|
		var m, vstSynth;
		vstInstr[name] = ();
		if(vstInstr[name][\vstSynth].notNil, {vstInstr[name][\vstSynth].synth.free});
		vstSynth = VSTPluginController(Synth(\vst));
		vstSynth.open(pathToPlugin, false, true, {|plug|
			plug.program = programNumber ? 1;
			m = plug.midi;
			vstInstr[name][\vstSynth] = vstSynth;
			vstInstr[name][\midiOutPort] = [m, 0];
			vstInstr[name][\controllers] = ();
		});
	}

	getCurrentMethods {
		// the goal here is to create everything we need for the preProcessor in getCurrentMethods to minimize things inside the pp.
		var patternMethods = [], formattedString;
		ParagraphMain.methods.do{|m| argumentStrings[m.name] = m.argumentString.collect{|n| n}};
		ParagraphPattern.methods.do{|m| argumentStrings[m.name] = m.argumentString.collect{|n| n}};

		// post hints for the different pattern types (see preProcessor)
		patternTypes.do{|type|
			switch(type,
				\seq, {argumentStrings[type] = "list numRepeats = inf offset = 0"},
				\shf, {argumentStrings[type] = "list numRepeats = inf reShuffleAndRepeat = inf"},
				\rnd, {argumentStrings[type] = "list numRepeats = inf weights = nil"},
				\seg, {argumentStrings[type] = "listOfLevels listOfDurations listOfCurves = [\lin] numRepeats = inf "},
				\wht, {argumentStrings[type] = "lo hi length = inf"},
				\gau, {argumentStrings[type] = "mean deviation length = inf"},
				\brw, {argumentStrings[type] = "lo hi step length = inf"},
				\euc, {argumentStrings[type] = "numHits ptnLength repeats = inf offset"},
				\cf, {argumentStrings[type] = "nameOfCf"},
				\tri, {argumentStrings[type] = "trianglePtn
\n\t[ o: repeats = inf ]
\n\t[ r: repeats = inf ]
\n\t[ i: axis repeats = inf ]
\n\t[ ri: axis repeats = inf ]
\n\t[ rows: repeats = inf ]
\n\t[ row: rowNum repeats = inf ]
\n\t[ rowR: rowNum repeats = inf ]
\n\t[ ptnOf: num repeats = inf ]
\n\t[ rowAsDur: rowNum repeats = inf mul = 1]
\n\t[ rowAsDurR: rowNum repeats = inf mul = 1]
\n\t[ rowAsTrem: rowNum root = 0 numTrems = 4 repeats = inf]
\n\t[ rowAsTremR: rowNum root = 0 numTrems = 4 repeats = inf]
\n\t[ allAsDur: mul = 1 repeats = inf]
\n\t[ allAsDurR: mul = 1 repeats = inf]
\n\t[ allAsTrem: root = 0 numTrems = 4, repeats = inf]
\n\t[ allAsTremR: root = 0 numTrems = 4, repeats = inf]
\n\t[ allRAsTrem: root = 0 numTrems = 4, repeats = inf]"
				},
				\ngd, {},
				\lsy, {}
			)
		};

		currentMethods = ParagraphMain.methods.collect{|m| m.name};
		currentMethods = uniqueMethods++currentMethods; // for some reason the unique methods doesn't work if concatenated the other way around
		patterns.keysValuesDo{|key, val|
			patternMethods.add(val.getCurrentMethods);
		};
		if(patternMethods.isEmpty,
			{patternMethods = ParagraphPattern.methods.collect{|m| m.name}},
			{patternMethods = patternMethods.flatten}
		);
		patternMethods.removeEvery(ParagraphPattern.excludeMethodsFromPreProc);
		currentMethods.removeEvery(excludeMethodsFromPreProc);
		currentMethods = patternMethods++currentMethods;

		// format a regexp string for the preProcessor
		formattedString = currentMethods.copy;
		formattedString = formattedString.asString.replace(", ", "|").replace("[ ").replace(" ]");
		formattedString = (formattedString).replace("|", " |(^|^§[a-z0-9]* )");
		formattedString = "(^|§[a-z0-9]* )"++formattedString;
		//formattedString = ("^"++formattedString).replace("|", " |^");

		// remove spaces from methods that doesn't take args
		formattedString = formattedString.replace("play ", "play").replace("stop ", "stop");

		// CHECK!!! for some reason some methods doesn't seem to be included...
		formattedString = formattedString++"|^def "; // hack to forcefully include def

		ParagraphPreProcessor.currentMethods = formattedString;
		^currentMethods;
	}

	// A tag based sample system.
	loadSamples {|tags, path|
		var b, f, n;
		if(path.isNil, { // to be able to leave out the tags and only write the path
			path = tags;
		});
		if(path.isFolder, {
			f = PathName(path).folderName.replace(" ", "");
			(path++"*").pathMatch.do{|filePath, i|
				if(filePath.isSoundFile, {
					var k = filePath.asSymbol;
					if(buffers[k].notNil, {buffers[k].free});
					buffers[k] = Buffer.read(server, filePath);
					n = (PathName(filePath).fileNameWithoutExtension.replace(" ", "")).asSymbol;
					samples[n] = buffers[k];
					samples[(f++i).asSymbol] = buffers[k];
					case{tags.class == Symbol} {tags = [tags]};
					if(tags.isString.not, {
						tags.do{|tag|
							samples[(tag++i).asSymbol] = buffers[k];
							samples[(tag++n).asSymbol] = buffers[k];
						};
					});
				});
			};
		}, {
			if(path.isSoundFile, {
				var k = path.asSymbol;
				if(buffers[k].notNil, {buffers[k].free});
				buffers[k] = Buffer.read(server, path);
				n = (PathName(path).fileNameWithoutExtension.replace(" ", "")).asSymbol;
				samples[n] = buffers[k];
				case{tags.class == Symbol} {tags = [tags]};
				if(tags.isString.not, {
					tags.do{|tag|
						samples[(tag).asSymbol] = buffers[k];
					};
				});
			});
		});
	}

	loadFx {|name, defName, extInBus = 0, extOutBus = 0|
		var b;
		if(currentMethods.includes(name).not, {
			uniqueMethods = uniqueMethods.add(name);
			this.getCurrentMethods;

			// här: if defName.isVst then VSTsynth bla bla else defName
			defName = defName ? name;

			// create a unique bus for the fx send.
			if(defName == 'extStereoFx' or: {defName == 'extMonoFx'}, {
				fxSendBusses[name] = [extInBus, extOutBus];
				b = fxSendBusses[name][0] + server.options.numOutputBusChannels; // the fx return (hw input on the interface)
			}, {
				fxSendBusses[name] = Bus.audio(server, 2);
				b = fxSendBusses[name];
			});

			// create the fx synth
			fork{
				server.bind{
					fx[name] = Synth(defName, [
						\in, b,
						\outL, 0,
						\outR, 1,
						\ambisonicsBus, foaBus
					], fxSynthsGroup);
				};

				server.sync;

				patterns.keysValuesDo{|key, val|
					val.initGlobalFx;
				};
			};
		}, {
			"method name already in use! pick another name!".error;
		});
	}

	freeFx {|name|
		fx[name].free;
		uniqueMethods.remove(name);
		try{fxSendBusses[name].free};
		fxSendBusses[name] = nil;
		patterns.keysValuesDo{|key, val|
			val.removeUniqueMethod(name);
			Pbindef((val.patternName++name).asSymbol).clear;
			val.fxSendSynths[name].free;
			val.fxSendSynths[name] = nil;
		};
		this.getCurrentMethods;
	}

	setParam {|name, par, ptn|
		// to do!
	}

	def {|type, name ...args|
		case {type == \snd} {
			this.loadSamples(name, args[0]);
		};

		case {type == \sfx} {
			this.loadFx(name, args[0], args[1], args[2]);
		};

		case {type == \syn} {
			ParagraphSynthDefs.buildGenerator(name, args[0], args[1], args[2]);
		};

		case {type == \mid} {
			this.defExtMidiInstr(name, args[0], args[1], args[2], args[3], args[4]);
		};

		case {type == \osc} {
			this.defOscAddr(name, *args);
		};

		case {type == \cf} {
			this.defCf(name, *args);
		};
	}

	lfo { }

	listParams {|name|
		// post the parameters and units of 'name'
		// syntax:
		// #vrb
	}

	*paragraphSynthDefs {|numSpeakers = 2, server|
		var decoder;

		if(numSpeakers == 2, {
			decoder = FoaDecoderKernel.newUHJ(512, server);
		}, {
			decoder = FoaDecoderMatrix.newPanto(numSpeakers);
		});

		server.sync;

		ParagraphSynthDefs.generators.keysValuesDo{|key, val|
			ParagraphSynthDefs.buildGenerator(key, val[0], val[1]);
		};

		server.sync;

		// build as send fx
		ParagraphSynthDefs.fx.keysValuesDo{|key, val|
			ParagraphSynthDefs.buildSendFx(key, val[0])
		};

		server.sync;

		// build as insertFx
		ParagraphSynthDefs.fx.keysValuesDo{|key, val|
			ParagraphSynthDefs.buildInsertFx(key, val[0])
		};

		server.sync;

		ParagraphSynthDefs.lfos.keysValuesDo{|key, val|
			ParagraphSynthDefs.buildLfo(key, val)
		};

		server.sync;

		ParagraphSynthDefs.masterSynthDef(decoder);
	}

	// Lindenmayer algae system
	prLsystem {|a = 4, b = 3, iterations = 9/*, numSteps = 32, rot = 0*/|
		var ptn, dict, newArray;
		dict = Dictionary.new;
		dict.put(a, [a, b]);
		dict.put(b, [a]);
		^ptn = Prewrite(a, dict, iterations);
	}

	// Per Nørgård's infinity series
	prNorgaard {|a = 0, b = 1, len = 64, rot = 0|
		var newArray;
		newArray = [a, b];
		len.do{|i|
			newArray = newArray.add(
				newArray[(2*(i+1))-2] - (newArray[i+1] - newArray[(i+1)-1]);
			);
			newArray = newArray.add(
				newArray[(2*(i+1))-1] + (newArray[i+1] - newArray[(i+1)-1]);
			);
		};
		^newArray.rotate(rot);
	}

	record {
		server.record(numChannels: numSpeakers);
	}

	stopRecording {
		server.stopRecording;
	}
}