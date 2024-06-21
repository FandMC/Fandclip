Leavesclip
=========
A binary patch distribution system for Leaves.

Leavesclip is the launcher for the Leaves Minecraft server. It uses a [bsdiff](http://www.daemonology.net/bsdiff/) patch
between the vanilla Minecraft server and the modified Leaves server to generate the Leaves Minecraft server immediately
upon first run. Once the Leaves server is generated it loads the patched jar into Leavesclip's own class loader, and runs
the main class.

This avoids the legal problems of the GPL's linking clause.

The patching overhead is avoided if a valid patched jar is found in the cache directory.
It checks via sha256 so any modification to those jars (or updated launcher) will cause a repatch.

On the basis of Paperclip, we have added the feature of using any Jar as a patch to support automatic updates.
Please note that this may bring some safety hazards.

You can completely disable this feature by adding the jvm parameter ```-Dleavesclip.disable.auto-update=true```

Building
--------

Building Leavesclip creates a runnable jar, but the jar will not contain the Leavesclip config file or patch data. This
project consists simply of the launcher itself, the [paperweight Gradle plugin](https://github.com/PaperMC/paperweight)
generates the patch and config file and inserts it into the jar provided by this project, creating a working runnable jar.
