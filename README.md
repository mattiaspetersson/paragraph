# Paragraph - a live coding frontend to create SuperCollider patterns with condensed syntax
Paragraph is a system for live electronic performance, rooted in my personal composer-performer practice. It is a custom made live coding interface for the creation of and interaction with musical patterns. It includes modular signal routing facilities, discrete as well as ambisonics panning, and is easy to integrate with other human and non-human agents. It was made as a tool to formalise and simplify things I tend to return to, while at the same time offer surprises and flexibility. The name comes from the idea of writing short paragraphs that are representations of musical ideas, with the notion that everything written within the same paragraph technically refers to the same receiver (a SuperCollider pattern), and artistically to the same musical idea.
This system was developed as a part of my [PhD thesis](https://ltu.diva-portal.org/smash/record.jsf?dswid=4265&pid=diva2%3A1952531&c=11&searchType=LIST_COMING&language=en&query=&af=%5B%5D&aq=%5B%5B%5D%5D&aq2=%5B%5B%5D%5D&aqe=%5B%5D&noOfRows=50&sortOrder=author_sort_asc&sortOrder2=title_sort_asc&onlyFullText=false&sf=all) entitled **The Act of Patching: Musicking with modular systems**, and thus, there's documentation of both the system, the syntax, and of use case scnearios to be found in [the book](https://ltu.diva-portal.org/smash/record.jsf?dswid=4265&pid=diva2%3A1952531&c=11&searchType=LIST_COMING&language=en&query=&af=%5B%5D&aq=%5B%5B%5D%5D&aq2=%5B%5B%5D%5D&aqe=%5B%5D&noOfRows=50&sortOrder=author_sort_asc&sortOrder2=title_sort_asc&onlyFullText=false&sf=all), on the adherent [Research Catalogue Exposition]((https://www.researchcatalogue.net/view/3411062/3418320), and in [an article for Organised Sound](https://www.cambridge.org/core/journals/organised-sound/article/live-coding-the-global-hyperorgan-the-paragraph-environment-in-the-indeterminate-place/F0C50E9CED30AB6F0770507F565051B5#article).

## Installation
First, you need to install [SuperCollider](https://supercollider.github.io/) and [SC3-Plugins](https://supercollider.github.io/sc3-plugins/).
Then, open up a SuperCollider document and run the following line (by placing the cursor on that line and pressing cmd+rtn (Mac) or ctrl+rtn (Win/Linux):
```Quarks.install("https://github.com/mattiaspetersson/paragraph)```
Wait for the installation to finish (watch the post window) and re-compile the class library (Language --> Recompile Class Library). The posts might tell you that you need to install kernels and matrices for the Ambisonics Toolkit (Atk). In this case, follow the instructions and run the following lines one by one and wait for the download to finish:
```
Atk.downloadKernels;
Atk.downloadMatrices;
```
Recompile the class libaray again, and unless you see some errors in the post window, you should be good to go.

## Basic Usage
Enter the Paragraph environment by typing and running
```§ start```

The server should boot up, indicated by the display turning green in the lower right corner.
Test code:
```
§1 i \sine
n seq [0 1 2 3 4]
d 0.25
a wht 0.1 0.25
p wht -1.0 1.0
play
```
Select the code and run the whole block or run it line by line.
Stop the running pattern by typing stop just below the block and run it.

## About the Syntax
The **Paragraph** syntax could be thought of as a dialect, derived from the standard SuperCollider syntax. It was constructed using the built-in preprocessor to filter the input code and return valid syntax. A basic objective was to keep the code interface minimal with as few delimiters as possible, both in order to fit on small screens, and to be fast and responsive. Thus, spaces and line breaks are the only separators used, and the most common keywords have been abbreviated to either one or three letters. For example, the regular SuperCollider Event keys ```\instrument```, ```\degree```, ```\dur``` and ```\legato``` have been replaced by ```i```, ```n```, ```d``` and ```l``` respectively. The value patterns that set those keys are expressed in a similar abbreviated way, e.g. ```wht 0.1 0.5``` instead of the regular syntax of SuperCollider ```Pwhite(0.1, 0.5);```. Many useful default values for patterns and devices to generate or process sound are predefined in the backend of the **Paragraph** system. In SuperCollider lingo, this, for example, includes a default chromatic scale, a number of SynthDefs for playing back samples, and simple synths for routing signals in the system, but the library is easily extendable as new needs and ideas arise.
