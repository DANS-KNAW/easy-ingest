easy-ingest
===========
[![Build Status](https://travis-ci.org/DANS-KNAW/easy-ingest.png?branch=master)](https://travis-ci.org/DANS-KNAW/easy-ingest)

Ingest staged digital objects in a Fedora Commons 3.x repository.


SYNOPSIS
--------

    easy-ingest [-u <user> -p <password>] [-f <fcrepo-server>] [-i] \
        [<staged-digital-object>... | <staged-digital-object-set>]


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


ARGUMENTS
---------

* ``-u``, ``--user`` -- Fedora user to connect with
* ``-p``, ``--password`` -- password of the Fedora user
* ``-f``, ``--fcrepo-server`` -- URL of the Fedora Commons Repository server
* ``-i``, ``--init`` -- initialize the directory as an SDO instead of ingesting it
* ``<staged-digital-object>...`` -- one or more [SDOs] to be ingested
* ``<staged-digital-object-set>`` -- an [SDO-set] to be ingested

``easy-ingest`` will recognize a directory as an SDO by checking for the ``fo.xml``. If it is absent, it will assume that
the directory is an SDO-set.

INSTALLATION AND CONFIGURATION
------------------------------

### Installation steps:

1. Unzip the tarball to a directory of your choice, e.g. /opt/
2. A new directory called easy-ingest-<version> will be created
3. Create an environment variabele ``EASY_INGEST_HOME`` with the directory from step 2 as its value
4. Add ``$EASY_INGEST_HOME/bin`` to your ``PATH`` environment variable.


### Configuration

General configuration settings can be set in ``$EASY_INGEST_HOME/cfg/appliation.properties`` and logging can be configured
in ``$EASY_INGEST_HOME/cfg/logback.xml``. The available settings are explained in comments in aforementioned files.


BUILDING FROM SOURCE
--------------------

Prerequisites:

* Java 8 or higher
* Maven 3.3.3 or higher
 
Steps:

        git clone https://github.com/DANS-KNAW/easy-ingest.git
        cd easy-ingest
        mvn install


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
