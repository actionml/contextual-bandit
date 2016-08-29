# Page Variant Recommender [Vowpal Wabbit - CSOAA] 

The PVR gets data from the PredictionIO EventServer. From events a model is trained and a PredictionServer is deployed to server queries for Recommendations. Every time you train the recommender model is updated automatically and without interruption the fresh predictions begin with the next query.

[For some discussion of the underlying algorithm and citations](algorithm.md)

#EventServer
The EventServer uses a Simple REST API for POSTing events in JSON form. Events can be an array of up to 50 events of the form described below. Insert events in JSON form like so: 

```
curl -i -X POST http://localhost:7070/events.json?accessKey=$ACCESS_KEY \
-H "Content-Type: application/json" \
-d $JSONHERE
```

#Data Input for the Page Variant Recommender

The PVR uses only one event type.
Page View / Conversion events are triggered by the action of displaying a page to a user and whether their page view results in a conversion or not.
It also contains the id of the test grouping [which groups together a set of page-variants to be considered]. 

#User Attributes

The user attributes (context) can consist of any number of features, which do not need to be specified in advance. However, for the context to have an effect, we should have the same context features whenever possible within a single test grouping. 

The important information is always of the form (userId, page-variant-id, test-group-id, conversion, context). So a simple example with only two users, two page-variants, two page views and a single context feature [gender] could look like the following: (userA, variantA, test-groupA, converted, male) and (userB, variantB, test-groupB, notConverted, female)

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
    "anotherContextFeature": ["A", "B"]
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
      "anotherContextFeature": ["A", "B"]
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
      "anotherContextFeature": ["C", "D"]
    }
  }
]
```

#Initialize test group

The system needs to  know what page-variants can be recommended for each test group in advance, so that it can sample from them to make recommendations. That means the test group must be initialized before the system is trained and queries are made against it (for a given testGroup).

```
{
  "event" : "$set",
  "entityType" : "testGroup"
  "entityId" : "A",
  "properties" : {
    "testPeriodStart": "2016-01-02T09:39:45.618-08:00",
    "testPeriodEnd": "2016-02-02T09:39:45.618-08:00", 
    "pageVariants" : ["variantA", "variantB","variantC", "variantD", "variantE"]
  },
  "eventTime" : "2016-01-02T09:39:45.618-08:00" //Optional
}
```

Note that once the testPeriod has elapsed, that the recommender will then be deterministic (best prediction always chosen, no randomness). You can continue to use the recommender after this period, but the test should be considered complete.

You can also set a test group again, to re-run the same test. You can update the testPeriodStart and testPeriodEnd properties by using another $set just like above. Please be sure however to not update the pageVariants or you may see undesirable results. In case you wish to create another test with different pageVariants, define a brand new testGroup.

#Usage Events/Indicators

All usage events can be thought of as (user-id, page-variant-id, test-group-id, conversion, context) but they must be encoded into PIO EventServer "events". 

Using the SDK of your choice (or directly using REST to the Eventserver, as seen above), define an event of the form:

```
{
   "event" : "page-view-conversion",
   "entityType" : "user",
   "entityId" : "amerritt",
   "targetEntityType" : "variant",
   "targetEntityId" : "variantA",
   "properties" : {
      "testGroupId" : "A",
      "converted" : true
    }
}
```

Here the targetEntityId can be any string.

#Page Variant Recommender Query API

Each engine has its own PredictionServer that can be queried. It is implemented in a REST API through the GET verb. A server is launched when you run `pio deploy`. The GET request will have a JSON query that is specific to a single engine. You may with to construct these with one of the PredictionIO SDKs. For the Page Variant Recommender here are some examples that work with the above data.

The PVR provides personalized queries. These are JSON + REST. A simple example using curl for personalized recommendations is:
```
   curl -H "Content-Type: application/json" -d '
   {
      "user": "psmith", 
      "testGroupId": "testGroupA"
   }' http://localhost:8000/queries.json

This will get recommendations for user: "psmith", These will be returned as a JSON object looking like this:

   {
      "Variant": "variantA",
      "testGroupId": "testGroupA"
   }
```

Note that you can also request a page variant for a new or anonymous user, in which case the recommendations will be based purely on the context features (e.g. gender, country, etc.). If there is no information about the user the most frequently converted variant will be returned

#Configuration of the Page Variant Recommender

The Page Variant Recommender has a configuration file called engine.json in the root directory. This defines parameters for all phases of the engine. The phases are data preparation, model building, and prediction server deployment. For the Page Variant Recommender all static parameters are kept here. The configurations makes liberal use of defaults so just because a parameter is not mentioned in engine.json does not mean the param is unspecified.
```
  {
  "id": "default",
  "description": "Default settings",
  "engineFactory": "org.template.pagevariant.PageVariantRecommenderEngine",
  "datasource": {
    "params": {
      "appName": "pageVariantsRecApp"
    }
  },
  "preparator": {
    "params": {
      … // TODO 
    }
  },
  "algorithms": [
    {
      … //TODO 
      }
  ]
}
```

#Training
Invoke:
```
pio train
```

As usual for a PredictionIO template. 
This template has been updated to train continuously, and does not follow the standard PIO behavior on training of simply executing on a single batch and exiting.


#Notes

This template requires Vowpal Wabbit. The included dependency in the build.sbt has been tested on Ubuntu 14.04 only. If you encounter issues, please build VW from source, per instructions below.

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

