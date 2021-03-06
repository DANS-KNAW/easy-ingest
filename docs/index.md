MANUAL
======
[![Build Status](https://travis-ci.org/DANS-KNAW/easy-ingest.png?branch=master)](https://travis-ci.org/DANS-KNAW/easy-ingest)

Ingest Staged Digital Objects (SDO's) into a Fedora Commons 3.x repository.


SYNOPSIS
--------

    easy-ingest [-i] [<staged-digital-object> | <staged-digital-object-set>]
    easy-ingest [-p <extraPids>] [<staged-digital-object> | <staged-digital-object-set>]


DESCRIPTION
-----------

### Fedora Digital Objects

Fedora defines a generic digital object model called [Fedora Digital Object Model]. To create new digital objects in a 
Fedora Commons repository you might use the [Service APIs] or the [client command-line utilities]. However, creating a
digital object and providing it with datastreams usually involves several steps. ``easy-ingest`` lets you ingest complete
digital objects that have previously been staged on the client file system.


### Staged Digital Objects

A Staged Digital Object (SDO) is a directory in the filesystem of the client that contains all the necessary files to build
up the digital object in Fedora. It must contain at least: 

* a [FOXML] file, called ``fo.xml``. 
* a [Digital Object Configuration] file, called ``cfg.json``

All other files in the directory are assumed to be the contents of datastreams. The names of the files are used as datastream 
IDs by default.

Example:

     - my-staged-do
         |
         +- fo.xml
         |
         +- cfg.json
         |
         +- DC
         |
         +- IMAGE_DATA
         |
         +- my.xml

In this example the SDO is called``my-staged-do``. The mandatory files ``fo.xml`` and ``cfg.json`` are present, as are three
files with datastream data: ``DC``, ``IMAGE_DATA`` and ``my.xml``. Unless specified otherwise in the [Digital Object 
Configuration] the three datastreams will have these file names as IDs.


### Staged Digital Object Set

A Staged Digital Object Set (SDO-set) is a directory containing as its direct subdirectories SDOs. The SDOs in an SDO-set
may reference each other by directory name in the relations they define. See [Digital Object Configuration] for details.


### Digital Object Configuration File

The Digital Object Configuration file is a [json] file containing additional information that is used to configure the
digital object and its datastreams. It is a thin layer over the following Fedora Commons APIs:
* [ingest]
* [addDatastream]
* [addRelationship]

An example will make things clear: 

      {
        "namespace" : "easy-dataset",                 
        "datastreams" : [
          {
            "contentFile" : "licence.pdf",                
            "dsID" : "DATASET_LICENSE",                
            "mimeType" : "application/pdf",              
            "controlGroup": "M",  
            "checksumType": "SHA-1",
            "checksum": "910ad1aa48cccdce6cac012ab7107e0707b2edfb"
          },
          {
            "dsLocation" : "http://archive.org/remote/file/path.jpg",
            "dsID" : "REMOTE_BYTES",                    
            "mimeType" : "image/jpeg",                    
            "controlGroup": "R"                      
          },
          { 
            "contentFile": "EASY_FILE",                      
            "mimeType": "text/csv"                       
          }
        ],
        "relations" : [
          {
            "predicate": "fedora:isMemberOf",        
            "objectSDO" : "do1"
          },
          {
            "predicate": "fedora:isSubordinateTo", 
            "object" : "easy-collection:123"     
          }
        ]
      }    

For the names of the properties see the Fedora Commons documentation mentioned above. Please note, that the
properties "ownerId" and "label" cannot be used in ``cfg.json``. The reason is that the Fedora API seems to ignore them.
To specify these properties you will have to add them directory to the ``fo.xml`` file.

A few extra properties and shortcuts are added by ``easy-ingest``:

* For datastreams:
    - "contentFile" is a file in de SDO that contains the content for the datastream
    - the default value for "contentFile" is the value of "dsID"
* For relations:
    - "objectSDO" is the name of an SDO in the same SDO-set. ``easy-ingest`` will fill in the resulting Fedora PID here.


### Extra PIDs

When an extra file needs to be ingested and linked to an existing dataset, the `--extraPids` option can be used. For example:

Given a dataset `easy-dataset:1` that is already ingested and a staged file object `MY_FILE` with a `cfg.json` containing a
`relation` with `objectSDO` referring to the dataset.

```json
{
  "namespace":"easy-file",
  "datastreams":[{
    "contentFile":"EASY_FILE",
    "dsID":"EASY_FILE",
    "controlGroup":"M",
    "mimeType":"text/plain",
    "checksumType":"SHA-1",
    "checksum":"4f5ea58e1c7e617f2190f7d9cf33637bc14aae02"
  },{
    "contentFile":"EASY_FILE_METADATA",
    "dsID":"EASY_FILE_METADATA",
    "controlGroup":"X",
    "mimeType":"text/xml"
  }],
  "relations":[{
    "predicate":"http://dans.knaw.nl/ontologies/relations#isMemberOf",
    "objectSDO":"DATASET_SDO~~RESERVED"
  },{
    "predicate":"http://dans.knaw.nl/ontologies/relations#isSubordinateTo",
    "objectSDO":"DATASET_SDO~~RESERVED"
  },{
    "predicate":"info:fedora/fedora-system:def/model#hasModel",
    "object":"info:fedora/easy-model:EDM1FILE"
  },{
    "predicate":"info:fedora/fedora-system:def/model#hasModel",
    "object":"info:fedora/dans-container-item-v1"
  }]
}
```

The goal is for `MY_FILE` to be ingested into Fedora, and linked to `easy-dataset:1`.
Create a `extra-pids.properties` file with content:

```properties
DATASET_SDO~~RESERVED = easy-dataset:1
```

Now run `easy-ingest --extraPids extra-pids.properties MY_FILE` and `MY_FILE` will be ingested into Fedora and the
`DATASET_SDO~~RESERVED` key will be replaced by `easy-dataset:1` in the relation(s).


ARGUMENTS
---------

     -p, --extraPids  <arg>   Properties file containing extra key-value pairs for the PidDictionary that is
                              being put together while running this application.
     -i, --init               Initialize template SDO instead of ingesting
     -h, --help               Show help message
     -v, --version            Show version of this program

    trailing arguments:
     <staged-digital-object-(set)> (required)   Either a single Staged Digital Object or a set of SDO's

``easy-ingest`` will recognize a directory as an SDO by checking for the ``fo.xml``. If it is absent, it will assume that
the directory is an SDO-set.


INSTALLATION AND CONFIGURATION
------------------------------
The preferred way of install this module is using the RPM package. This will install the binaries to
`/opt/dans.knaw.nl/easy-ingest` and the configuration files to `/etc/opt/dans.knaw.nl/easy-ingest`.

To install the module on systems that do not support RPM, you can copy and unarchive the tarball to the target host.
You will have to take care of placing the files in the correct locations for your system yourself. For instructions
on building the tarball, see next section.


BUILDING FROM SOURCE
--------------------

Prerequisites:

* Java 8 or higher
* Maven 3.3.3 or higher
* RPM 

Steps:

    git clone https://github.com/DANS-KNAW/easy-deposit-api.git
    cd easy-deposit-api
    mvn install

If the `rpm` executable is found at `/usr/local/bin/rpm`, the build profile that includes the RPM 
packaging will be activated. If `rpm` is available, but at a different path, then activate it by using
Maven's `-P` switch: `mvn -Pprm install`.

Alternatively, to build the tarball execute:

    mvn clean install assembly:single


[Fedora Digital Object Model]: https://wiki.duraspace.org/display/FEDORA38/Fedora+Digital+Object+Model
[Service APIs]: https://wiki.duraspace.org/display/FEDORA38/Service+APIs
[client command-line utilities]: https://wiki.duraspace.org/display/FEDORA38/Client+Command-line+Utilities
[FOXML]: https://wiki.duraspace.org/pages/viewpage.action?pageId=66585857
[dans-parent]: https://github.com/DANS-KNAW/dans-parent
[Digital Object Configuration]: #digital-object-configuration-file
[json]: http://json.org/
[ingest]: https://wiki.duraspace.org/display/FEDORA38/REST+API#RESTAPI-ingest
[addDatastream]: https://wiki.duraspace.org/display/FEDORA38/REST+API#RESTAPI-addDatastream
[addRelationship]: https://wiki.duraspace.org/display/FEDORA38/REST+API#RESTAPI-addRelationship 
[SDOs]: #staged-digital-objects
[SDO-set]: #staged-digital-object-set
