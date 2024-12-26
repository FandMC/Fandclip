Fandclip
=========
A binary patch distribution system for Fand.

Fandclip is the launcher for the Fand Minecraft server. It uses a [bsdiff](http://www.daemonology.net/bsdiff/) patch
between the vanilla Minecraft server and the modified Fand server to generate the Fand Minecraft server immediately
upon first run. Once the Fand server is generated it loads the patched jar into Fandclip's own class loader, and runs
the main class.

This avoids the legal problems of the GPL's linking clause.

The patching overhead is avoided if a valid patched jar is found in the cache directory.
It checks via sha256 so any modification to those jars (or updated launcher) will cause a repatch.

Building
--------

Building Fandclip creates a runnable jar, but the jar will not contain the Fandclip config file or patch data. This
project consists simply of the launcher itself, the [paperweight Gradle plugin](https://github.com/PaperMC/paperweight)
generates the patch and config file and inserts it into the jar provided by this project, creating a working runnable jar.
