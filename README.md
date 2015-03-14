# sdjson-grabber
Java 7 grabber application for the Schedules Direct JSON data service.

## Development currently in `release/20141201` branch
The `master` branch represents the last release of API 20140530, which still uses maven to build and references the old SourceFoge infrastructure.  All development is currently happening in the `release/20141201` branch.  That branch will be merged into `master` at some point and the current development version will again be done from `master`.  This is a transition phase while the move from maven to gradle is being worked on.

## Back to Github
Starting with the API 20141201 release the grabber project has relocated to github.  The old SourceForge site will not be updated.  Wiki pages, etc. are in the proces of being migrated.

## Newer Versions
The newest versions of the grabber (API 20141201 and newer) will be located at bintray.  A link will be added here after the first release of API 20141201.

## Snapshots
Snapshots are no longer built and made available, however in this release the project has moved to gradle for building and so building a snapshot of the grabber couldn't be easier.  All you need on your system is a proper version of the JDK and that's it.  The build wiki page has all the details (it's literally a single command).

## Older Versions
Older versions of the grabber app (API 20140530 and older) are available at the old [SourceForge project site](https://sourceforge.net/projects/sdjson/files/?source=navbar).  That site will no longer be updated; all new builds are released from the repository hosted here to bintray.
