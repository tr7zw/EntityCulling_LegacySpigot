# TileCulling
[![Build Status](https://ci.codemc.io/buildStatus/icon?job=FearGames%2FTileCulling)](https://ci.codemc.io/job/FearGames/job/TileCulling/)

Hides tiles(mainly chests) that players are not able to see due to blocks in the way(occlusion culling), and then blocks packets for these entities. This results in the following:

- Massive client fps improvements in big bases with tons of chests and farms due to the client not correctly culling them clientside(and therefor leaving a lot of performance on the table). Seen up to 500%+ more fps! (from 52fps->270fps at the same location without anything else changed)
- Minimap mods/Hackclients are unable to see items/chests etc through walls
- Stuff "pops in" when walking around a corner(kinda the price to pay for this being done serverside)

Basically exchanging server cpu(async calculation)+memory(needs to keep snapshots of everything for the other thread + caching) for client fps and to some degree anticheat.

## Requires

- Paper/Spigot 1.16.5!
- ProtocolLib

## Known issues

-  Itemframes are visually correct after respawning, but their hitbox is at the wrong location (To be verified)

## Credits

[Tr7zw](https://github.com/tr7zw) created [EntityCulling](https://github.com/tr7zw/EntityCulling) and later on [EntityCulling-Fabric](https://github.com/tr7zw/EntityCulling-Fabric), this project is heavily based on his work. EntityCulling-Fabric and TileCulling share a common core module maintained by both tr7zw and sgdc3 called [OcclusionCulling](https://github.com/LogisticsCraft/OcclusionCulling), you can find it under the [LogisticsCraft](https://github.com/LogisticsCraft) organization.

[RoboTricker](https://github.com/robotricker/) created the original server side async raytracing occlusion culling implementation for [Transport-Pipes](https://github.com/RoboTricker/Transport-Pipes). I took it and optimized it to a point where it's able to do multiple thousands of traces in a second over a predefined sized area(100x100x100 currently with the player in the center of it).
