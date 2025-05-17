ParagraphPattern { // release version
	var <patternName, paragraphMain;
	var tempoClock, quant, <buffers, server, <ptn, <initCondition;
	var lfoGroup, <generatorsGroup, fxInsertSynthsGroup, <patternMasterSynth;
	var fx, <lfos;
	var <patternMasterBus, patternMasterBusRec, <localFxBus, <sideChainBus, <volCtrlBus;
	var <fxSendBusses, <fxSendSynths, <fxPatterns;
	var <extMidiInstrSynths, <hwInputSynths, <vstInstr;
	var <lastNodes, <currentMethods, divEnv, fxMethods;
	classvar <excludeMethodsFromPreProc, <patternMethods;

	*new {|ptnName, aParagraphMain|
		^super.newCopyArgs(ptnName, aParagraphMain).initParagraphPattern;
	}

	*initClass {
		excludeMethodsFromPreProc = [
			\patternName, \ptn, \initCondition, \patternMasterSynth, \lfos,
			\sideChainBus, \fxSendBusses, \fxSendSynths, \fxPatterns,
			\uniqueMethods, \currentMethods, \excludeMethodsFromPreProc,
			\initParagraphPattern, \getCurrentMethods, \makeDefaultPbindef, \extMidiInstr,
			\patternMasterBus, \generatorsGroup, \localFxBus, \extMidiInstrSynths,
			\initGlobalFx, \initExtMidiInstr, \makePatternMethods,
			\initGlobalFx, \initHwInputs, \prMakePatternMethod,
			\buffers, \volCtrlBus, \hwInputSynths, \vstInstr, \lastNodes,
		];
		patternMethods = (
			globalPatternMethods: [
				\outL, \outR, \azmL, \azmR, \elevL, \elevR, \dstncL, \dstncR, \ambiMix,
				\cmpTh, \cmpRt, \cmpAt, \cmpRl,
				\hpfFrq, \hpfRes, \hpfFm, \lpfFrq, \lpfRes, \lpfFm, \xMod, \sat

				// instead?
				/*\opl, \opr, \azl, \azr, \ell, \elr, \dsl, \dsr, \amb,
				\cth, \crt, \cat, \crl,
				\hpf, \hpr, \hpm, \lpf, \lpr, \lpm, \xmd, \sat*/
			],
			patternMethods: [
				\i, \prb, \n, \d, \mul, \div, \scl, \cxp, \mxp, \a, \p, \l, \rvs, \lgc, \val, \chn, \play, \stop
			]
		);
	}

	initParagraphPattern {
		if(paragraphMain.gui, {this.addDependant(paragraphMain.editor)});
		paragraphMain.getCurrentMethods;
		tempoClock = paragraphMain.tempoClock;
		quant = paragraphMain.quant;
		fxMethods = [];
		buffers = paragraphMain.samples;
		extMidiInstrSynths = ();
		hwInputSynths = ();
		vstInstr = paragraphMain.vstInstr;
		server = paragraphMain.server;
		fx = ();
		fxSendSynths = ();
		fxSendBusses = ();
		fxPatterns = ();
		lfos = ();
		ptn = ();
		lfoGroup = ParGroup(server);
		generatorsGroup = ParGroup.after(lfoGroup);
		fxInsertSynthsGroup = Group.after(generatorsGroup);
		//fxSynthsGroup = ParGroup.after(fxSendsGroup);

		fork{
			ParagraphSynthDefs.patternMasterSynthDefs(patternName);
			patternMasterBus = Bus.audio(server, 2);
			patternMasterBusRec = Bus.audio(server, 2);
			localFxBus = Bus.audio(server, 2);
			sideChainBus = Bus.audio(server, 2);
			volCtrlBus = Bus.control(server, 1);

			server.sync;

			patternMasterSynth = Synth(('patternMaster'++patternName).asSymbol, [
				\in, patternMasterBus,
				\vol, 1,
				\scOut, sideChainBus,
				\fxOut, localFxBus,
				\outL, 0,
				\outR, 1,
				\ambisonicsBus, paragraphMain.foaBus,
				\recOut, patternMasterBusRec
			], paragraphMain.patternMastersGroup);

			server.sync;

			this.initGlobalFx;
			//this.initHwInputs;

			server.sync;

			//this.makeDefaultPbindef;
			ParagraphPbindefs.make(paragraphMain, this);

			server.sync;

			// add synth params as unique methods
			ParagraphSynthDefs.generators.keysValuesDo{|key, val|
				case {val[2].notNil} {
					val[2].do{|parName|
						this.addUniqueMethod(parName, {|receiver, type, valA, valB, valC, valD, valE, dup = 1|
							//this.perform(k, type, valA, valB, valC, valD, valE, dup = 1);
							this.prMakePatternMethod(parName, nil, nil, type, valA, valB, valC, valD, valE, dup = 1);
						});
						paragraphMain.uniqueMethods = paragraphMain.uniqueMethods.add(parName);
					};
				};
			};

			paragraphMain.initConditions[patternName].test = true;
			paragraphMain.initConditions[patternName].signal;
			paragraphMain.initConditions[patternName] = nil;
		};
		paragraphMain.getCurrentMethods;
	}

	getCurrentMethods {
		^currentMethods = ParagraphPattern.methods.collect{|m| m.name}++fxMethods;
	}

	initGlobalFx {
		paragraphMain.uniqueMethods.do{|name| // better to change name to fxMethods to avoid other unique method names
			server.bind{
				fxSendSynths[name] = Synth('fxSend', [
					\inBus, localFxBus, // the send reads from the pattern's fxBus
					\outBus, if(paragraphMain.fxSendBusses[name].isArray, // and sends it to the bus allocated in ParagraphMain
						{paragraphMain.fxSendBusses[name][1]}, // the hw output on the interface
						{paragraphMain.fxSendBusses[name]} // a Bus.audio
					)
				], paragraphMain.fxSendsGroup);
			};

			// create a pattern for the fx send
			fxPatterns[name] = Pbindef((patternName++name).asSymbol,
				\type, \set,
				\id, fxSendSynths[name].nodeID,
				\args, #[\lvl],

				// shared values with the main pattern
				\div, Pdef((patternName++name++\prdiv).asSymbol, {Pn(~pattern ? 1)}),
				\dur, Pdef((patternName++name++\prdur).asSymbol, {Psubdivide(Pfunc{|ev| ev[\div]}, ~pattern ? 0.25)}),
				\stretch, Pdef((patternName++name++\prstretch).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 1)}),

				\lvl, Pdef((patternName++name++\prlvl).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			);

			// update the shared values
			Pbindef((patternName++name).asSymbol,
				\div, Pdef((patternName++name++\prdiv).asSymbol) <> (pattern: ptn[\div]),
				\dur, Pdef((patternName++name++\prdur).asSymbol) <> (pattern: ptn[\dur]),
				\stretch, Pdef((patternName++name++\prstretch).asSymbol) <> (pattern: ptn[\stretch])
			);

			// add unique method for the send level (with tha defined name of the fx)
			this.addUniqueMethod(name, {|receiver, type, valA, valB, valC, valD, valE, dup = 1|
				fork{
					if(paragraphMain.initConditions[patternName].notNil, {
						paragraphMain.initConditions[patternName].wait;
					});
					ptn[name] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
					Pbindef(
						(patternName++name).asSymbol,
						\lvl,
						Pdef((patternName++name++\prlvl).asSymbol) <> (pattern: ptn[name])
					);
					if(Pbindef(patternName).isPlaying, {Pbindef((patternName++name).asSymbol).play(tempoClock, quant: quant)}); // this will be out of sync unless everything is restarted. perhaps ok?
				};
			});
		};
	}

	insFx {|name, defName, type = 'int', extInBus, extOutBus|
		// add these to the fxInsertSynthsGroup
		// should have a dry/wet control with a default value of 100% wet
		// what should happen if user tries to use the same extInsertFx in several places?
		if(uniqueMethods.includes(name).not, {
			uniqueMethods = uniqueMethods.add(name);
			this.getCurrentMethods;
			defName = defName ? name;
			defName = (defName++'Insert').asSymbol;
			switch(type,
				'int', {
					// create the fx
					server.bind{
						fx[name] = Synth(defName, [
							\in, patternMasterBus,
							\out, patternMasterBus
						], fxInsertSynthsGroup, \addToTail);
					};

					// create an fx pattern
					fxPatterns[name] = Pbindef((patternName++name).asSymbol,
						\type, \set,
						\id, fx[name].nodeID,
						\args, #[\wet],
						\wet, Pdef((patternName++name++\prlvl).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 1)}),

						// shared values with the main pattern.
						\dur, Pdef((patternName++\prdur).asSymbol),
						\stretch, Pdef((patternName++\prstretch).asSymbol)
					);

					this.addUniqueMethod(name, {|receiver, type, valA, valB, valC, valD, valE, dup = 1|
						this.class.uniqueMethods = this.class.uniqueMethods.add(name);
						fork{
							if(paragraphMain.initConditions[patternName].notNil, {
								paragraphMain.initConditions[patternName].wait;
							});
							ptn[name] = this.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
							Pbindef(
								(patternName++name).asSymbol,
								\wet,
								Pdef((patternName++name++\prwet).asSymbol) <> (pattern: ptn[name], dup: ptn[(name++'dup').asSymbol])
							);
							//this.play;
							paragraphMain.initConditions[patternName] = nil;
						};
					});
				},
				'ext', {
					/*fxSendBusses[name] = [extInBus, extOutBus]; // create a unique bus for the fx send.

					// create an fxSend and a unique method for each pattern
					patterns.keysValuesDo{|key, val|
					server.bind{
					val.fxSendSynths[name] = Synth('fxSend', [
					\inBus, val.localFxBus, // the send reads from the pattern's fxBus
					\outBus, fxSendBusses[name][1] // sends it to the ext bus defined
					], fxSendsGroup);
					};

					// create an fx pattern
					val.fxPatterns[name] = Pbindef((val.patternName++name).asSymbol,
					\type, \set,
					\id, val.fxSendSynths[name].nodeID,
					\args, #[\lvl],
					\lvl, Pdef((val.patternName++name++\prlvl).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),

					// shared values with the main pattern.
					\dur, Pdef((val.patternName++\prdur).asSymbol),
					\stretch, Pdef((val.patternName++\prstretch).asSymbol)
					);

					val.addUniqueMethod(name, {|receiver, type, valA, valB, valC, valD|
					val.class.uniqueMethods = val.class.uniqueMethods.add(name);
					fork{
					if(initConditions[val.patternName].notNil, {
					initConditions[val.patternName].wait;
					});
					val.ptn[name] = this.makeValuePattern(type, valA, valB, valC, valD);
					Pbindef(
					(val.patternName++name).asSymbol,
					\lvl,
					Pdef((val.patternName++name++\prlvl).asSymbol) <> (pattern: val.ptn[name], dup: val.ptn[(name++'dup').asSymbol])
					);
					val.play;
					initConditions[val.patternName] = nil;
					};
					});
					};

					// create the fx
					server.bind{
					fx[name] = Synth(defName, [
					\in, fxSendBusses[name][0] + server.options.numOutputBusChannels, // the fx return
					\outL, 0,
					\outR, 1,
					\ambisonicsBus, foaBus
					], fxSynthsGroup);
					};*/

				},

				'vst', {

				}
			);
		}, {
			"method name already in use! pick another name!".error;
		});
	}

	initExtMidiInstr {

	}

	makeDefaultPbindef {
		Pbindef(patternName,
			// subdivide the dur into n events
			// use ~dup to compensate for the subdivisions by Psubdivide to keep the param values for the whole \dur
			\div, Pdef((patternName++\prdiv).asSymbol, {Pn(~pattern ? 1)}),
			// the prob and logical values are used in the \instrument Pfunc
			\prob, Pdef((patternName++\prprob).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 1)}),
			\logical, Pdef((patternName++\prlogical).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 1)}),
			\group, generatorsGroup,
			\out, patternMasterBus,

			// this return names of syntDefs, buffers or defined midi or vst instruments, handled by the \instrument Pfunc
			\prInstr, Pdef((patternName++\prinstrument).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? \sine)}),
			\buf, Pfunc{|ev| buffers[ev[\prInstr]] ? 0},
			\instrument, Pfunc{|ev|
				var name = ev[\prInstr];
				if(buffers[name].notNil, {
					ev[\type] = [\note, \rest].wchoose([ev[\prob], 1 - ev[\prob]]);
					if(buffers[name].numChannels == 1, {
						\trigBufMono;
					}, {
						\trigBufStereo;
					});
				}, {
					if(SynthDescLib.global[name].notNil, {
						ev[\type] = [\note, \rest].wchoose([ev[\prob], 1 - ev[\prob]]);
						ev[\prInstr];
					}, {
						if(paragraphMain.extMidiInstr[name].notNil, {
							if(paragraphMain.extMidiInstr[name][\hwIn].notNil, {
								if(extMidiInstrSynths[name].isNil, {
									if(paragraphMain.extMidiInstr[name][\hwIn] != \audioMovers, {
										extMidiInstrSynths[name] = Synth(
											if(paragraphMain.extMidiInstr[name][\numChannels] == \stereo, {\hwInputStereo}, {\hwInputMono}),
											[
												\inBus, paragraphMain.extMidiInstr[name][\hwIn],
												\out, patternMasterBus,
												\amp, paragraphMain.extMidiInstr[name][\hwInGain]
											],
											generatorsGroup);
									}, {
										fork{
											extMidiInstrSynths[name] = Synth(\vsti, [
												\out, patternMasterBus,
												\amp, paragraphMain.extMidiInstr[name][\hwInGain]
											], generatorsGroup);
											server.sync;
											paragraphMain.extMidiInstr[name][\listenToReceiver] = VSTPluginController(extMidiInstrSynths[name]);
											paragraphMain.extMidiInstr[name][\listenToReceiver].open("Listento-Receiver", editor: true);
											server.sync;
											paragraphMain.extMidiInstr[name][\listenToReceiver].editor;
										};
									});
								});
							});
							ev[\type] = [\midi, \rest].wchoose([ev[\prob], 1 - ev[\prob]]);
							ev[\midicmd] = \noteOn;
							ev[\midiout] = paragraphMain.extMidiInstr[name][\midiOutPort];
							ev[\prInstr]; // dummy return
						}, {
							if(vstInstr[name].notNil, {
								vstInstr[name][\vstSynth].synth.moveToHead(generatorsGroup);
								vstInstr[name][\vstSynth].synth.set(\out, patternMasterBus, \amp, ev.use{~amp.value}, \pan, ev[\pan]);
								ev[\type] = [\midi, \rest].wchoose([ev[\prob], 1 - ev[\prob]]);
								ev[\midicmd] = \noteOn;
								ev[\midiout] = vstInstr[name][\midiOutPort][0];
								ev[\chan] = vstInstr[name][\midiOutPort][1];
							}, {
								ev[\type] = \rest;
							});
						});
					});
				});
			},
			\chan, Pdef((patternName++\prchan).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\scale, Pdef((patternName++\prscale).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? Scale.melodicMinor)}),
			\tuning, Pdef((patternName++\prtuning).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? Tuning.et12)}),
			//\prApplyTuning, Pfunc{|ev| ev[\scale].scale.tuning_(ev[\tuning])},
			\root, Pdef((patternName++\prroot).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\ctranspose, Pdef((patternName++\prctranspose).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\mtranspose, Pdef((patternName++\prmtranspose).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\degree, Pdef((patternName++\prdegree).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\dur, Pdef((patternName++\prdur).asSymbol, {Psubdivide(Pfunc{|ev| ev[\div]}, ~pattern ? 0.25)}),

			\divEnv, Pseg(
				Pseq([1, 0], inf),
				Pseq([Pfuncn{|ev| ev[\div] * ev[\dur]}, 0], inf),
				\lin
			),

			\prAmp, Pdef((patternName++\pramp).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0.1)}),
			\amp, Pfunc{|ev| ev[\prAmp] * ev[\divEnv]},
			\pan, Pdef((patternName++\prpan).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\legato, Pdef((patternName++\prlegato).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0.8)}),
			\stretch, Pdef((patternName++\prstretch).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 1)}),
			\reverse, Pdef((patternName++\prreverse).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\atk, Pdef((patternName++\pratk).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\rel, Pdef((patternName++\prrel).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0.9)}),

			// lfos
			\freqlfo, 0,
			\amplfo, 0,
			\panlfo, 0,

			\post, Pfunc{|ev|
				if(paragraphMain.posts, {
					{
						server.latency.wait;
						if(ev[\type] != \rest, {
							(
								patternName ++ ": "
								++ev[\prInstr]++" "
								++ev[\dur]++" "
								++ev[\degree]++" "
								++ev[\amp]++" "
							).post;
						}, {"".postln});
					}.fork(AppClock);
				});

				this.changed(patternName, ev);
			},
			\callback, {|ev|
				lastNodes = ev[\id];
			}
		);

		// a global pattern to set params on the master synth
		Pbindef((patternName++"global").asSymbol,
			\type, \set,
			\id, patternMasterSynth.nodeID,
			\args, #[
				\outL, \outR, \azimuthL, \elevationL, \azimuthR, \elevationR, \distanceL, \distanceR, \ambisonicsMix,
				\compressorThreshold, \compressorSlopeAbove, \compressorAttack, \compressorRelease,
				\filter, \hpfFreq, \hpfRes, \hpfFmAmt,
				\lpfFreq, \lpfRes, \lpfFmAmt,
				\crossSynthesis, \saturation,
				\amplfo, \azimuthLlfo, \azimuthRlfo, \elevationLlfo, \elevationRlfo,
				\hpfFreqlfo, \hpfReslfo, \lpfFreqlfo, \lpfReslfo,
				\del, \delTime, \delFbTime, \beatDur,
				\crossSynthesislfo, \saturationlfo,
				\hush
			],
			// div is shared with main pattern. I.e. set with the div method together with the pattern.
			\div, Pdef((patternName++'global'++\prdiv).asSymbol, {Pn(~pattern ? 1)}),
			\outL, Pdef((patternName++\proutL).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\outR, Pdef((patternName++\proutR).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 1)}),
			\azimuthL, Pdef((patternName++\prazimuthL).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0.785)}),
			\azimuthR, Pdef((patternName++\prazimuthR).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? -0.785)}),
			\elevationL, Pdef((patternName++\prelevationL).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\elevationR, Pdef((patternName++\prelevationR).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\distanceL, Pdef((patternName++\prdistanceL).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 5)}),
			\distanceR, Pdef((patternName++\prdistanceL).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 5)}),
			\ambisonicsMix, Pdef((patternName++\prambisonicsMix).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\compressorThreshold, Pdef((patternName++\prcompressorThreshold).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 1)}),
			\compressorSlopeAbove, Pdef((patternName++\prcompressorSlopeAbove).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 1)}),
			\compressorAttack, Pdef((patternName++\prcompressorAttack).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0.01)}),
			\compressorRelease, Pdef((patternName++\prcompressorRelease).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0.1)}),
			\filter, 1,
			\hpfFreq, Pdef((patternName++\prhpfFreq).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 20)}),
			\hpfRes, Pdef((patternName++\prhpfRes).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 1.0)}),
			\hpfFmAmt, Pdef((patternName++\prhpfFmAmt).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\lpfFreq, Pdef((patternName++\prlpfFreq).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 20000)}),
			\lpfRes,  Pdef((patternName++\prlpfRes).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 1.0)}),
			\lpfFmAmt, Pdef((patternName++\prlpfFmAmt).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),

			\beatDur, Pfunc{paragraphMain.tempoClock.beatDur},
			\delTime, Pdef((patternName++\prdelTime).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0.5)}),
			\delFbTime, Pdef((patternName++\prdelFbTime).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 3)}),
			\del, Pdef((patternName++\prdel).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\crossSynthesis, Pdef((patternName++\prcrossSynthesis).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\saturation, Pdef((patternName++\prsaturation).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 1)}),

			// shared values with the main pattern.
			\dur, Pdef((patternName++'global'++\prdur).asSymbol, {Psubdivide(Pfunc{|ev| ev[\div]}, ~pattern ? 0.25)}),
			\stretch, Pdef((patternName++'global'++\prstretch).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 1)}),

			//lfos
			\amplfo, 0,
			\azimuthLlfo, 0,
			\azimuthRlfo, 0,
			\elevationLlfo, 0,
			\elevationRlfo, 0,
			//\distanceLlfo, 0,
			//\distanceRlfo, 0,
			\hpfFreqlfo, 0,
			\hpfReslfo, 0,
			\lpfFreqlfo, 0,
			\lpfReslfo, 0,
			\crossSynthesislfo, 0,
			\saturationlfo, 0
		);
	}

	play {
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			if(Pbindef(patternName).isPlaying.not, {
				Pbindef(patternName).play(tempoClock, quant: quant);
				Pbindef((patternName++\global).asSymbol).play(tempoClock, quant: quant);
			});
			fxPatterns.keysValuesDo{|key, pbindef|
				if(pbindef.isPlaying.not, {pbindef.play(tempoClock, quant: quant)});
			};
			if(Pbindef(\global).isPlaying.not, {Pbindef(\global).play(tempoClock, quant: quant)});
			paragraphMain.cfDict.keysValuesDo{|key, val|
				if(val[\cfPtn].isPlaying.not, {val[\cfPtn].play(tempoClock, quant: quant)});
			};
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	stop {
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			//(type: \off, id: lastNodes).play; // to turn of nodes if \dur was inf. THIS ALSO TURNS OFF synths for extMidiInstrs = bad.
			Pbindef(patternName).stop;
			Pbindef((patternName++\global).asSymbol).stop;
			fxPatterns.keysValuesDo{|key, pbindef| pbindef.stop};
			extMidiInstrSynths.keysValuesDo{|key, val|
				val.set(\gate, 0, \rel, 0.02);
				extMidiInstrSynths[key] = nil;
			};
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	// a factory for creating unique methods to control the pattern. NOT USED ATM!
	// but this should ideally be the way to create all methods
	makePatternMethods {|methodName, setGlobalPtn = false|
		var globalPtnName;
		if(setGlobalPtn, {globalPtnName = (patternName++"global").asSymbol});

		this.addUniqueMethod(methodName, {|receiver, type, valA, valB, valC, valD, valE, dup = 1|
			fork{
				if(paragraphMain.initConditions[patternName].notNil, {
					paragraphMain.initConditions[patternName].wait;
				});
				ptn[methodName] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
				Pbindef(globalPtnName ? patternName, methodName, Pdef((patternName++\pr++methodName).asSymbol) <> (pattern: ptn[methodName]));
				//this.play;
				paragraphMain.initConditions[patternName] = nil;
			};
		});
	}

	prMakePatternMethod {|ptnKey, clipLo, clipHi, ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE,
		dup = 1, setPattern = \local|
		var p;

		if(setPattern == \local) {p = patternName} {p = (patternName++setPattern).asSymbol};

		fork{
			if(paragraphMain.initConditions[p].notNil, {
				paragraphMain.initConditions[p].wait;
			});

			ptn[ptnKey] = paragraphMain.makeValuePattern(
				ptnType,
				ptnValA,
				ptnValB,
				ptnValC,
				ptnValD,
				ptnValE,
				dup
			).clip(clipLo, clipHi);

			Pbindef(p, ptnKey, Pdef((p++\pr++ptnKey).asSymbol) <> (pattern: ptn[ptnKey]));
			paragraphMain.initConditions[p] = nil;
		};
	}

	outL {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\outL] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef((patternName++"global").asSymbol, \outL, Pdef((patternName++\proutL).asSymbol) <> (pattern: ptn[\outL]));
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	outR {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\outR] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef((patternName++"global").asSymbol, \outR, Pdef((patternName++\proutR).asSymbol) <> (pattern: ptn[\outR]));
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	azL {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\azimuthL] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef((patternName++"global").asSymbol, \azimuthL, Pdef((patternName++\prazimuthL).asSymbol) <> (pattern: ptn[\azimuthL]));
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	azR {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\azimuthR] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef((patternName++"global").asSymbol, \azimuthR, Pdef((patternName++\prazimuthR).asSymbol) <> (pattern: ptn[\azimuthR]));
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	elvL {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\elevationL] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef((patternName++"global").asSymbol, \elevationL, Pdef((patternName++\prelevationL).asSymbol) <> (pattern: ptn[\elevationL]));
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	elvR {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\elevationR] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef((patternName++"global").asSymbol, \elevationR, Pdef((patternName++\prelevationR).asSymbol) <> (pattern: ptn[\elevationR]));
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	dstncL {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\distanceL] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef((patternName++"global").asSymbol, \distanceL, Pdef((patternName++\prdistanceL).asSymbol) <> (pattern: ptn[\distanceL]));
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	dstncR {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\distanceR] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef((patternName++"global").asSymbol, \distanceR, Pdef((patternName++\prdistanceR).asSymbol) <> (pattern: ptn[\distanceR]));
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	ambiMix {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\ambisonicsMix] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef((patternName++"global").asSymbol, \ambisonicsMix, Pdef((patternName++\prambisonicsMix).asSymbol) <> (pattern: ptn[\ambisonicsMix]));
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	cmpTh {|type, valA, valB, valC, valD, valE, dup = 1|
		var t, a, b;
		if(type.isNumber, {
			t = type.dbamp;
		}, {
			if(type == \seg, {
				t = type;
				a = valA;
				b = valB.dbamp;
			}, {
				t = type;
				a = valA.dbamp;
			});
		});
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\compressorThreshold] = paragraphMain.makeValuePattern(t, a, b, valC, valD, dup);
			Pbindef((patternName++"global").asSymbol,
				\compressorThreshold, Pdef((patternName++\prcompressorThreshold).asSymbol) <> (pattern: ptn[\compressorThreshold])
			);
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	cmpRt {|type, valA, valB, valC, valD, valE, dup = 1|
		var t, a, b;
		if(type.isNumber, {
			t = type.reciprocal;
		}, {
			if(type == \seg, {
				t = type;
				a = valA;
				b = valB.reciprocal;
			}, {
				t = type;
				a = valA.reciprocal;
			});
		});
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\compressorSlopeAbove] = paragraphMain.makeValuePattern(t, a, b, valC, valD, dup);
			Pbindef((patternName++"global").asSymbol,
				\compressorSlopeAbove, Pdef((patternName++\prcompressorSlopeAbove).asSymbol) <> (pattern: ptn[\compressorSlopeAbove])
			);
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	cmpAt {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\compressorAttack] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef((patternName++"global").asSymbol, \compressorAttack, Pdef((patternName++\prcompressorAttack).asSymbol) <> (pattern: ptn[\compressorAttack]));
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	cmpRl {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\compressorRelease] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef((patternName++"global").asSymbol, \compressorRelease, Pdef((patternName++\prcompressorRelease).asSymbol) <> (pattern: ptn[\compressorRelease]));
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	hpfFrq {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\hpfFreq] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef((patternName++"global").asSymbol, \hpfFreq, Pdef((patternName++\prhpfFreq).asSymbol) <> (pattern: ptn[\hpfFreq]));
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	hpfRes {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\hpfRes] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef((patternName++"global").asSymbol, \hpfRes, Pdef((patternName++\prhpfRes).asSymbol) <> (pattern: ptn[\hpfRes]));
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	hpfFm {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\hpfFmAmt] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef((patternName++"global").asSymbol, \hpfFmAmt, Pdef((patternName++\prhpfFmAmt).asSymbol) <> (pattern: ptn[\hpfFmAmt]));
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	lpfFrq {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\lpfFreq] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef((patternName++"global").asSymbol, \lpfFreq, Pdef((patternName++\prlpfFreq).asSymbol) <> (pattern: ptn[\lpfFreq]));
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	lpfRes {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\lpfRes] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef((patternName++"global").asSymbol, \lpfRes, Pdef((patternName++\prlpfRes).asSymbol) <> (pattern: ptn[\lpfRes]));
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	lpfFm {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\lpfFmAmt] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef((patternName++"global").asSymbol, \lpfFmAmt, Pdef((patternName++\prlpfFmAmt).asSymbol) <> (pattern: ptn[\lpfFmAmt]));
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	del {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\del] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef((patternName++"global").asSymbol, \del, Pdef((patternName++\prdel).asSymbol) <> (pattern: ptn[\del]));
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	delTime {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\delTime] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef((patternName++"global").asSymbol, \delTime, Pdef((patternName++\prdelTime).asSymbol) <> (pattern: ptn[\delTime]));
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	delFbTime {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\delFbTime] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef((patternName++"global").asSymbol, \delFbTime, Pdef((patternName++\prdelFbTime).asSymbol) <> (pattern: ptn[\delFbTime]));
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	xMod {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\crossSynthesis] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef((patternName++"global").asSymbol, \crossSynthesis, Pdef((patternName++\prcrossSynthesis).asSymbol) <> (pattern: ptn[\crossSynthesis]));
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	sat {|type, valA, valB, valC, valD, valE, dup = 1|
		var t, a, b;
		if(type.isNumber, {
			t = type.linlin(0, 1, 1, 99);
		}, {
			if(type == \seg, {
				t = type;
				a = valA;
				b = valB.linlin(0, 1, 1, 99);
			}, {
				t = type;
				a = valA.linlin(0, 1, 1, 99);
			});
		});
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\saturation] = paragraphMain.makeValuePattern(t, a, b, valC, valD, dup);
			Pbindef((patternName++"global").asSymbol, \saturation, Pdef((patternName++\prsaturation).asSymbol) <> (pattern: ptn[\saturation]));
			//this.play;
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	sch {|on = false, scBus|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			if(on, {
				patternMasterSynth.set(\sc, 1, \scIn, scBus);
			}, {
				patternMasterSynth.set(\sc, 0);
			});
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	prb {|ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup = 1| // probability
		this.prMakePatternMethod(\prob, 0.0, 1.0, ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup);
	}

	val {|ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup = 1| // general values for OSC messaging and MIDI CC:s
		this.prMakePatternMethod(\val, -inf, inf, ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup);
	}

	lgc {|ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup = 1| // logical (not in use yet!)
		this.prMakePatternMethod(\logical, 0, 1, ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup);
	}

	chn {|ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup = 1| // midi channel
		this.prMakePatternMethod(\chan, 0, 15, ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup);
	}

	n {|ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup = 1| // note degrees
		paragraphMain.tri.mulAdd(1, -1); // sets the tri patterns to meaningful values
		this.prMakePatternMethod(\degree, -48, 48, ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup);
	}


	scl {|ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup = 1| // scale
		var t, a, b;
		case{ptnType.isKindOf(Symbol)} {
			if(Scale.at(ptnType).notNil, {
				t = Scale.at(ptnType);
			}, {
				t = ptnType;
			});
		};

		case{ptnType.isArray} {
			t = ptnType;
		};

		case{ptnType.class == Scale} {
			t = ptnType;
		};

		case{ptnValA.isArray} {
			ptnValA = ptnValA.collect{|item|
				if(item.isSymbol and: {Scale.at(item).notNil}, {
					Scale.at(item);
				}, {
					item;
				});
			};
		};

		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			paragraphMain.tri.mulAdd(1, -1);
			ptn[\scale] = paragraphMain.makeValuePattern(t, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup);
			Pbindef(patternName, \scale, Pdef((patternName++\prscale).asSymbol) <> (pattern: ptn[\scale]));
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	root {|ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup = 1|  // root note
		paragraphMain.tri.mulAdd(1, -1);
		this.prMakePatternMethod(\root, -12, 12, ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup);
	}

	cxp {|ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup = 1|  // chromatic transposition
		paragraphMain.tri.mulAdd(1, -1);
		this.prMakePatternMethod(\ctranspose, -48, 48, ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup);
	}

	mxp {|ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup = 1|  // modal transposition
		paragraphMain.tri.mulAdd(1, -1);
		this.prMakePatternMethod(\mtranspose, -48, 48, ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup);
	}

	a {|ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup = 1|  // amplitude
		paragraphMain.tri.mulAdd(1, 0);
		this.prMakePatternMethod(\amp, 0.0, 1.0, ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup);
	}

	p {|ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup = 1|  // stereo panning
		this.prMakePatternMethod(\pan, -1.0, 1.0, ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup);
	}

	l {|ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup = 1|  // legato
		this.prMakePatternMethod(\legato, 0.001, 1.0, ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup);
	}

	rvs {|ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup = 1|  // reverse (sample)
		this.prMakePatternMethod(\reverse, 0, 1, ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup);
	}

	atk {|ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup = 1|  // attack time
		this.prMakePatternMethod(\atk, 0.0, 60.0, ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup);
	}

	rel {|ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup = 1|  // release time
		this.prMakePatternMethod(\rel, 0.0, 60.0, ptnType, ptnValA, ptnValB, ptnValC, ptnValD, ptnValE, dup);
	}

	cc {|midiInstr, paramNameOrNumber, type, valA, valB, valC, valD, valE, dup = 1|
		// use a separate Pbindef for this?
	}

	// the methods below are a bit more complex and need to be written out explicitly!
	i {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			ptn[\instrument] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef(patternName, \prInstr, Pdef((patternName++\prinstrument).asSymbol) <> (pattern: ptn[\instrument]));
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	// \dur, \stretch and \div share values between all patterns
	d {|type, valA, valB, valC, valD, valE, dup = 1|  // previously dur
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			paragraphMain.tri.mulAdd(1, 0);
			ptn[\dur] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef(patternName, \dur, Pdef((patternName++\prdur).asSymbol) <> (pattern: ptn[\dur]));
			Pbindef((patternName++'global').asSymbol, \dur, Pdef((patternName++'global'++\prdur).asSymbol) <> (pattern: ptn[\dur]));
			fxPatterns.keysDo{|name|
				Pbindef((patternName++name).asSymbol, \dur, Pdef((patternName++name++\prdur).asSymbol) <> (pattern: ptn[\dur]));
			};
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	div {|type, valA, valB, valC, valD, valE, dup = 1|
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			paragraphMain.tri.mulAdd(1, 0);
			ptn[\div] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
			Pbindef(patternName, \div, Pdef((patternName++\prdiv).asSymbol) <> (pattern: ptn[\div]));
			Pbindef((patternName++'global').asSymbol, \div, Pdef((patternName++'global'++\prdiv).asSymbol) <> (pattern: ptn[\div]));
			fxPatterns.keysDo{|name|
				Pbindef((patternName++name).asSymbol, \div, Pdef((patternName++name++\prdiv).asSymbol) <> (pattern: ptn[\div]));
			};
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	mul {|type, valA, valB, valC, valD, valE, dup = 1|  // previously stretch
		var playing = Pbindef(patternName).isPlaying;
		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			paragraphMain.tri.mulAdd(1, 0);
			paragraphMain.tempoClock.sched(tempoClock.timeToNextBeat(quant), {
				ptn[\stretch] = paragraphMain.makeValuePattern(type, valA, valB, valC, valD, valE, dup);
				this.stop;
				Pbindef(patternName, \stretch, Pdef((patternName++\prstretch).asSymbol) <> (pattern: ptn[\stretch]));
				Pbindef((patternName++'global').asSymbol, \stretch, Pdef((patternName++'global'++\prstretch).asSymbol) <> (pattern: ptn[\stretch]));
				fxPatterns.keysDo{|name|
					Pbindef((patternName++name).asSymbol, \stretch, Pdef((patternName++name++\prstretch).asSymbol) <> (pattern: ptn[\stretch]));
				};
				if(playing, {this.play});
			});
			paragraphMain.initConditions[patternName] = nil;
		};
	}

	lfo {|applyTo, waveform, freq, amt|
		var bus, global, key, p, m = [
			\n, \a, \p, //pattern method names
			\azL, \azR, \elvL, \elvR, //master synth method names
			\hpfFrq, \hpfRes, \lpfFrq, \lpfRes, \xMod, \sat
		];

		fork{
			if(paragraphMain.initConditions[patternName].notNil, {
				paragraphMain.initConditions[patternName].wait;
			});
			bus = Bus.audio(server, 1);
			if(m.includes(applyTo), {
				// this converts (the shorter) method names to actual keys used in the Pbindef. Ugly...
				switch(applyTo,
					// pattern keys
					\n, {key = \freqlfo; global = false},
					\a, {key = \amplfo; global = false},
					\p, {key = \panlfo; global = false},

					\azL, {key = \azimuthLlfo; global = true},
					\azR, {key = \azimuthRlfo; global = true},
					\elvL, {key = \elevationLlfo; global = true},
					\elvR, {key = \elevationRlfo; global = true},
					\hpfFrq, {key = \hpfFreqlfo; global = true},
					\hpfRes, {key = \hpfReslfo; global = true},
					\lpfFrq, {key = \lpfFreqlfo; global = true},
					\lpfRes, {key = \lpfReslfo; global = true},
					\xMod, {key = \crossSynthesislfo; global = true},
					\sat, {key = \saturationlfo; global = true}
				);
				if(global, {
					p = (patternName++\global).asSymbol;
				}, {
					p = patternName;
				});
				if(waveform.notNil, {
					if(paragraphMain.ctrls[waveform].notNil, {
						if(lfos[applyTo].isNil, {
							Pbindef(p, key, paragraphMain.ctrls[waveform][\bus].asMap);
						}, {
							lfos[applyTo].do{|obj| obj.free};
							//group.map(key, bus);
							Pbindef(p, key, paragraphMain.ctrls[waveform][\bus].asMap);
						});
					}, {
						if(lfos[applyTo].isNil, {
							//group.map(key, bus);
							key.postln;
							Pbindef(p, key, bus.asMap);
							lfos[applyTo] = [Synth(('lfo_'++waveform).asSymbol, [\freq, freq, \amount, amt, \out, bus], lfoGroup), bus];
						}, {
							lfos[applyTo].do{|obj| obj.free};
							//group.map(key, bus);
							Pbindef(p, key, bus.asMap);
							lfos[applyTo] = [Synth(('lfo_'++waveform).asSymbol, [\freq, freq, \amount, amt, \out, bus], lfoGroup), bus];
						});
					});
				}, {
					Pbindef(p, key, 0);
					lfos[applyTo].do{|obj| obj.free};
					lfos[applyTo] = nil;
				});
			}, {
				//("LFO can't be applied to this method.\n Try these: "++m).error;
			});
			paragraphMain.initConditions[patternName] = nil;
		};
	}
}