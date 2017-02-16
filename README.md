# Contextual Bandit [Vowpal Wabbit - CSOAA] 

The Contextual Bandit is a PIO Kappa Template and so is an online learner. It keeps model up-to-date in real time.

As such is gets data from the PIO Kappa Server. From events a model is updated and a predictions are served. Every time there is an input events the model is updated and fresh predictions begin with the next query.

[For some discussion of the underlying algorithm and citations](algorithm.md)

# Events

The PIO Kappa Server has an endpoint for input to a dataset for single of multiple Events and uses a Simple REST API for POSTing them in JSON form. For Example: 

```
curl -i -X POST http://localhost:7070/datasets/dataset-id/events/ \
-H "Content-Type: application/json" \
-d { event in JSON }
```

Since PIO Kappa may use SSL and Authentication, the above example only works with security disabled. See the PIO Kappa design docs for a description of the Java and Python SDKs, which should be used since they provide SSL and Authentication support.

# Data Input for the Contextual Bandit

The Contextual Bandit uses only one event type. Events are triggered by some user action such as displaying a page to a user (the Page Variant Recommender) or clicking a link (link recommender) each event is flagged with whether it was a conversion or not. It also contains the id of the test grouping, which groups together a set of items to be considered together. Put another way, the CB will recommend one of the grouped items based on previous recorded events associated with the group. Think if the group like the catalog of an ecommerce site. 

# User Attributes

The user attributes (context) can consist of any number of features, which do not need to be specified in advance. However, for the context to have an effect, we should have the same context features whenever possible within a single test grouping. 

Adding new attributes is allowed but will take time to affect recommendations as they come in from some starting point. 

The important information is always of the form (user-id, item-id, group-id, conversion, context). So a simple example with only two users, two items, two events and a single context feature [gender] could look like the following: (user-1, item-1, group-1, converted, male) and (user-2, item-2, group-2, notConverted, female)

The user attributes (context) must be set independently of usage events. These must be specified at least once, in order for user context to be used, but it may also be called later to update.

```
{
  "event" : "$set",
  "entityType" : "user"
  "entityId" : "amerritt",
  "eventTime" : "2014-11-02T09:39:45.618-08:00", // Optional
  "properties" : {
    "gender": ["male"],
    "country" : ["Canada"],
    "otherContextFeatures": ["A", "B"]
  }
}
```

Batch insert of events is supported, up to 50 events per request. For batch insert use the following URL:
http://localhost:7070/batch/events.json
```
[
  {
    "event" : "$set",
    "entityType" : "user"
    "entityId" : "amerritt",
    "eventTime" : "2014-11-02T09:39:45.618-08:00", // Optional
    "properties" : {
      "gender": ["male"],
      "country" : ["Canada"],
      "otherContextFeatures": ["A", "B"]
   }
  },
  {
    "event" : "$set",
    "entityType" : "user"
    "entityId" : "Joe",
    "eventTime" : "2014-11-02T09:39:45.618-08:00", // Optional
    "properties” : {
      "gender": ["male"],
      "country" : ["United States"],
      "otherContextFeatures": ["C", "D"]
    }
  }
]
```

#Initialize a group

The system needs to  know what items can be recommended for each group in advance. The algorithm cold-starts by recommending at random from these items until conversions are seen, then it will converge of the best recommendations based on these and the user + context that seems to prefer an item. That means the group must be initialized before the system is ready to respond to queries.

```
{
  "event" : "$set",
  "entityType" : "group"
  "entityId" : "group-1",
  "properties" : {
    "testPeriodStart": "2016-01-02T09:39:45.618-08:00",
    "testPeriodEnd": "2016-02-02T09:39:45.618-08:00", 
    "items" : ["item-1", "item-2","item-3", "item-4", "item-5"]
  },
  "eventTime" : "2016-01-02T09:39:45.618-08:00" //Optional
}
```

Note that once the testPeriod has elapsed, that the recommender will then be deterministic (best prediction always chosen, no randomness). You can continue to use the recommender after this period, but the test should be considered complete.

You can also reset a group to start training over. You can update the testPeriodStart and testPeriodEnd properties by using another $set just like above. However do not update the items or you may see undesirable results. 

In you wish to create another test with different items, define a new group-id.

# Usage Events/Indicators

All usage events can be thought of as (user-id, item-id, group-id, conversion, context) but they must be encoded into PIO Kappa "events". 

Using the PIO Kappa SDK of your choice define an event of the form:

```
{
   "event" : "conversion",
   "entityType" : "user",
   "entityId" : "amerritt",
   "targetEntityType" : "item",
   "targetEntityId" : "item-1",
   "properties" : {
      "groupId" : "group-1",
      "converted" : true
    }
}
```

Here the targetEntityId can be any string.

# Contextual Bandit Query API

Each engine has its own query endpoint to make recommendations for any number of groups. See the PIO Kappa Server docs for creating an Engine. Once the Engine is created it will have an id and can then have groups initialized. The engine-id will reference information about how the Engine is initialized, configuraton parameters and the like. 

The Contextual Bandit provides personalized queries. Using the SDK create a query of the form (this can be a POST using curl if no SSL or Auth is being used):

```
{
  "user": "psmith", 
  "group-id": "group-1"
}
```

This will get recommendations for user: "psmith", These will be returned as a JSON object looking like this:

```
{
  "item": "item-1",
  "group-id": "group-1"
}
```

Note that you can also request a group item for a new or anonymous user, in which case the recommendations will be based purely on the context features (e.g. gender, country, etc.). If there is no information about the user the most frequently converted variant will be returned

# Configuration of the Contextual Bandit

The Contextual Bandit has a configuration file called engine.json in the root directory. This defines parameters for all phases of the engine. The phases are data preparation, model building, and prediction server deployment. For the Contextual Bandit all static parameters are kept here. The configurations makes liberal use of defaults so just because a parameter is not mentioned in engine.json does not mean the param is unspecified.

```
{
    "engineId": "engine-1",
    "description": "Default settings",
    "engineFactory": "org.template.pagevariant.ContextualBanditEngine",
    "dataset": {
        "params": {
            "datasetId": "dataset-1"
        }
    },
    "engine": {
        "params": {
            "appName": "PageVariant",
            "comment": "Max training iterations",
            "maxIter": 100,
            "comment": "Regularization",
            "regParam": 0.0,
            "comment": "Learning rate",
            "stepSize": 0.1,
            "comment": "Feature hashing target size in bits",
            "bitPrecision": 24,
            "modelName": "model.vw",
            "namespace": "n",
            "maxClasses": 3,
            "comment": "not needed: true unless retraining from a watermarked model"
            "initialize": true
        }
    }
}
```

# Training

When the CB engine is deployed it read the params, connects to the model, and initializes. Once the CB is deployed (see the pio-kappa docs), merely sending events to its model will cause it to retrain incrementally and update the model. 

**Note**: This is significantly different that other implementation of the CB using PIO 0.10.0, where the online learning happened through initialization and polling, please note the differences in this section of the docs.

# Notes

This template requires Vowpal Wabbit. The included dependency in the build.sbt has been tested on Ubuntu 14.04 only. If you encounter issues, please build VW from source, per instructions below.

## Abbreviated Vowpal Setup:

### Prerequisite software

These prerequisites are usually pre-installed on many platforms. However, you may need to consult your favorite package
manager (*yum*, *apt*, *MacPorts*, *brew*, ...) to install missing software.

- [Boost](http://www.boost.org) library, with the `Boost::Program_Options` library option enabled.
- GNU *autotools*: *autoconf*, *automake*, *libtool*, *autoheader*, et. al. This is not a strict prereq. On many systems (notably Ubuntu with `libboost-dev` installed), the provided `Makefile` works fine.

### Getting the code

```
$ git clone https://github.com/EmergentOrder/vowpal_wabbit.git
```

### Compiling

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

### Vowpal Java Wrapper Build

```
$ cd java
$ mvn package
$ cd target 
```

Finally, substitute the jar found in `target` for the one specified in the build.sbt if needed. [currently published artifact was generated on Ubuntu 14.04]

