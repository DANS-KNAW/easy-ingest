*Note: this project is in pre-alpha state, so below instructions may not work completely yet*

easy-ingest
===========

Ingest staged digital objects in a Fedora Commons 3.x repository


SYNOPSIS
--------

    easy-ingest.sh staged-digital-object...


DESCRIPTION
-----------

### Fedora Digital Objects

Fedora defines a generic digital object model called [Fedora Digital Object Model]. To create new digital objects in a 
Fedora Commons repository you might use the [Service APIs] or the [client command-line utilities]. However, creating a
digital object and providing it with datastreams usually involves several steps. ``easy-ingest`` lets you ingest complete
digital objects that have previously been staged on the client file system.


### Staged Digital Objects

A Staged Digital Object is a directory in the filesystem of the client that contains all the necessary files to build
up the digital object in Fedora. It must contain at least a [FOXML] file, called ``fo.xml``. All other files in the directory
are assumed to be the contents of datastreams. The names of the files are used as datastream IDs.

Example:

     - staged-fo
         |
         +- fo.xml
         |
         +- DC
         |
         +- IMAGE_DATA


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
4. Add ``EASY_INGEST_HOME/bin`` to your ``PATH`` environment variable.


### Configuration






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
