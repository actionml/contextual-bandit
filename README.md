# Probabilistic Classifier Template [Vowpal Wabbit - LR/SGD] 

This template requires Vowpal Wabbit. The included dependency in the build.sbt has been tested on Ubuntu 14.04 only. If you encounter issues, please build VW from source, per instructions below.

For a tutorial featuring this engine template, please look [here](https://github.com/EmergentOrder/PredictionIO/blob/develop/docs/manual/source/demo/sentiment.html.md)

## Abbreviated Vowpal Setup:

## Prerequisite software

These prerequisites are usually pre-installed on many platforms. However, you may need to consult your favorite package
manager (*yum*, *apt*, *MacPorts*, *brew*, ...) to install missing software.

- [Boost](http://www.boost.org) library, with the `Boost::Program_Options` library option enabled.
- GNU *autotools*: *autoconf*, *automake*, *libtool*, *autoheader*, et. al. This is not a strict prereq. On many systems (notably Ubuntu with `libboost-dev` installed), the provided `Makefile` works fine.

## Getting the code

```
## For HTTP-based Git interaction
$ git clone https://github.com/EmergentOrder/vowpal_wabbit.git
```

## Compiling

You should be able to build the *vowpal wabbit* on most systems with:
```
$ make
$ make test    # (optional)
```

If that fails, try:
```
$ ./autogen.sh
$ make
$ make test    # (optional)
$ make install
```

#Vowpal Java Wrapper Build

```
$ cd java
$ mvn package
$ cd target 
```

Finally, substitute the jar found in `target` for the one specified in the build.sbt if needed. [currently published artifact was generated on Ubuntu 14.04]

