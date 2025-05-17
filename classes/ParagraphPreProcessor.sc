ParagraphPreProcessor { // release version
	classvar <>currentMethods; // a regexp-formatted string
	classvar <hotKeyMatches, patternTypes;

	*run {|aParagraphMain, bool = true|
		var string;
		var paragraph, ptnTypeString;

		paragraph = ParagraphMain.paragraph;
		paragraph.getCurrentMethods;
		patternTypes = paragraph.patternTypes;

		ptnTypeString = patternTypes.copy;
		ptnTypeString = ptnTypeString.asString.replace(", ", "|").replace("[ ", "|").replace(" ]");
		ptnTypeString = ("^"++ptnTypeString).replace("|", " |^");

		if(bool, {
			// store all occurencies of the hotkey (§ == xA7 as hex) in a classvar
			Document.globalKeyUpAction_({|doc, key|
				hotKeyMatches = Document.current.string.findRegexp(
					"\\xA7[\\n\\r\\s]|\\xA7\\d+[\\n\\r\\s]"
				).collect{|match|
					var m = match[1].replaceRegex("\\xA7", "§").replace(" ");
					[match[0], m];
				};

				// create patterns as you type them
				fork{
					paragraph.server.bootSync; // don't create any patterns until the server's running!
					2.wait;
					hotKeyMatches.do{|match|
						var r = match[1].replaceRegex("[\\n\\r\\s]", "");
						case {"^§\\d+".matchRegexp(r)} {
							// if the pattern doesn't exist; create it!
							if(paragraph.patterns[r.asSymbol].isNil, {
								paragraph.newPattern(r.asSymbol);
								"created pattern %".format(r).postln;
							});
						};
					};
				};

				// post arguments and default values for methods and patterns
				/*Document.current.currentLine.findRegexp(currentMethods++ptnTypeString).do{|match|
					var m = match[1].replace(" ", "").asSymbol;
					("%: ".format(m)++paragraph.argumentStrings[m]).postln;
				};*/
			});

			thisProcess.interpreter.preProcessor = {|c|
				var codeBlock;
				// if a block is wrapped in parentheses, remove the newlines
				if(c.beginsWith("(\n") and: {c.endsWith("\n)")}, {
					codeBlock = c.replace("(\n").replace("\n)");
				}, {
					codeBlock = c;
				});

				if(codeBlock.beginsWith("§") or: {currentMethods.matchRegexp(codeBlock)}, {
					codeBlock.split($\n).collect{|line|
						var r, receiver, method, args, ctrls, newLine, firstMethod;
						ctrls = line.split($|)[1];
						if(ctrls.notNil, {ctrls = ctrls.stripWhiteSpace});
						line = line.split($|)[0];

						// receiver
						r = ParagraphPreProcessor.findReceiver; // r is set to § or §n
						case {r == "§"} {receiver = "ParagraphMain.paragraph"};

						// newline, space or cr is necessary after §n to create a pattern OLD: case {"^§\\d+|^§[a-z]+".matchRegexp(r)}
						case {"^§\\d+".matchRegexp(r)} {
							// remove newline, space or cr for the pattern dictionary
							r = r.replaceRegex("[\\n\\r\\s]", "");
							receiver = "ParagraphMain.paragraph.patterns"++"['"++r++"']";

							// if this pattern doesn't exist; create it!
							if(paragraph.patterns[r.asSymbol].isNil, {
								paragraph.newPattern(r.asSymbol);
								"created pattern %".format(r).postln;
							});
						};

						newLine = line.replace(r++" ").replace(r); // replaces all occurencies of "§n" and "§n .."

						//method
						newLine.findRegexp(currentMethods).do{|match|
							// semafor to only let the first method on the line through
							if(firstMethod.isNil, {firstMethod = match[1]});
							method = firstMethod;

							//args
							newLine = newLine.replaceRegex("^"++method++"\\s", "").replaceRegex("^"++method, ""); // remove method from the line.
							args = ParagraphPreProcessor.parseArgs(newLine);
						};

						firstMethod = nil;
						string = ParagraphPreProcessor.formatString(
							receiver, method ? "i", args, ctrls
						);
						string.postln;
					}.join;
				}, {
					codeBlock;
				});
			};
		}, {
			thisProcess.interpreter.preProcessor = nil;
		});
	}

	*findReceiver {
		var receiver, i = Document.current.selectionStart, o = 0, f;
		f = hotKeyMatches.flatten;
		f = f.collect{|item, x| if(x.even, {o = o - 1; item + o}, {item})}; // the §:s occupy 2 chars, hence we need this offset. (should be sorted in the Document methods really...)
		f = f.add(f[f.maxIndex] + 123456789); // add an extra large number to avoid nil at the end of the code string
		receiver = f[f.indexOfGreaterThan(i)-1];
		^receiver ?? {"no receiver in this paragraph!".warn};
	}

	// parseArgs should handle args and keyword replacements only!
	// formatString should only format the string into sc syntax.
	*parseArgs {|code|
		var c, matches;
		c = code
		.replace("\n")
		//clean up with something like: patternTypes.do{|type| c = code.replace(type.asString, "'"++type++"'")};
		.replace("seq", "'seq'")
		.replace("rnd", "'rnd'")
		.replace("seg", "'seg'")
		.replace("wht", "'wht'")
		.replace("gau", "'gau'")
		.replace("brw", "'brw'")
		.replace("euc", "'euc'")
		.replace("tri", "'tri'")
		.replace("ngd", "'ngd'")
		.replace("lsy", "'lsy'")
		.replace("snd", "'snd'")
		.replace("sfx", "'sfx'")
		.replace("mid", "'mid'")
		.replace("osc", "'osc'")
		.replace("cf", "'cf'")
		.replace("] [", "] [");

		// replace all spaces with commas except spaces within strings (for filepaths with spaces etc)
		matches = c.findRegexp("\\\"[^\\\"]*\\\"");
		c = c.replace(" ", ",");
		matches.do{|match, n|
			c = c.replaceAt(match[1], match[0], match[1].size);
		};
		c = c.replace(",", ", "); // for readability. Might be uneccesary...
		^c;
	}

	*formatString {|receiver, method, args, ctrls|
		var str;
		case {receiver.notNil}  {str = receiver};
		case {method.notNil} {str = (str++".%").format(method)};

		// this should probably be moved to parseArgs!
		case {args.notNil} {
			// replace ! with dup
			args = args.replace(" !", "dup:");

			// nested patterns within ();
			// change to a replaceRegex to match ( and patternTypes should be safer
			args = args.replace("(", "ParagraphMain.paragraph.makeValuePattern(");
			str = (str++"(%)").format(args)
		};
		if(ctrls.notNil,
			{ctrls = receiver++".lfo("++"'"++method.replace(" ")++"', "++ctrls.replace(" ", ", ")++");"},
			{ctrls = receiver++".lfo("++"'"++method.replace(" ")++"', "++"nil"++");"}
		);
		str = str++";"++ctrls;
		^str;
	}
}