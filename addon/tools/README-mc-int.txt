mc-int.jar = Minecraft 1.21.11 client jar in intermediary names (what the game loads at run time). Not stored in the repo (Mojang code).
Make it: take the vanilla client jar, get FabricMC/intermediary 1.21.11.tiny
(https://raw.githubusercontent.com/FabricMC/intermediary/master/mappings/1.21.11.tiny), turn the tiny file into the TSV that
Remap2.java reads (CLASS/FIELD/METHOD lines), run: java Remap2 <tsv> <vanilla.jar> /tmp/mc-int.jar  (inheritance aware).
build.sh picks /tmp/mc-int.jar (or $MC_INT) up automatically; without it the old stub build is used and only code that
touches Minecraft members the old DIH jar already used compiles.
