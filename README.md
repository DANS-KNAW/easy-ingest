*Note: this project is in pre-alpha state, so below instructions may not work completely yet*

easy-ingest
===========

Ingest staged digital objects in a Fedora Commons 3.x repository


SYNOPSIS
--------

    easy-ingest.sh [staged-digital-object... | staged-digital-object-set]


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
may reference each other by directory name in the relations they define. See ... for details.


### Digital Object Configuration File

The Digital Object Configuration file is a [json] file containing additional information that is used to configure the
digital object and its datastreams. Its structure is informally specified using the example below. Note that the comments
introduced with ``--`` are not part of the example and are not legal json

      {
        "namespace" : "easy-dataset",                 -- the Fedora PID namespace to create the new digital object in
        "datastreams" : [
          {
            "id" : "DATASET_LICENSE",                 --
            "file" : "licence.pdf",
            "mime" : "application/pdf",
            "control_group": "M"
          },
          {
            "id" : "REMOTE_BYTES",                 --
            "url" : "http://archive.org/remote/file/path.jpg"
            "mime" : "image/jpeg",
            "control_group": "R"
          },
        ],
        "relations" : [
          {
            "predicate": "fedora:isMemberOf",
            "objectName" : "do1"
          },
          {
            "predicate": "fedora:isSubordinateTo",
            "objectName" : "do1"
          }
        ]
      }    


ARGUMENTS
---------

-u, --user: Fedora user to connect with

-p, --password: password of the Fedora user

-f, --fcrepo-server: URL of the Fedora Commons Repository server


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

* Java 7 or higher
* Maven 3.3.3 or higher
 
Steps:

1. Clone and build the [dans-parent] project (*can be skipped if you have access to the DANS maven repository*)
      
        git clone https://github.com/DANS-KNAW/dans-parent.git
        cd dans-parent
        mvn install
2. Clone and build this project

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
