# Development dependencies

The default build resolves Thaumaturge 1.1.0 for Minecraft 1.21.1 from Curse Maven, file 9452543.
A local release JAR can be selected with `-Pthaumaturge_jar=/absolute/path/to.jar`.
Optional development mods belong in `libs/compat/`.

The tested JEI combination is JEI 19.57.0.451 with ExtendedAE Plus 1.6.3.
ExtendedAE Plus 1.6.2 references older JEI input classes; JEI 19.64 removes the handlers needed by both 1.6.2 and 1.6.3.

Dependency JARs are ignored by Git and are not included in TECE artifacts.
