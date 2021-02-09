# EntityCulling

Hides entities and tiles(mainly chests) that players are not able to see due to blocks in the way(occlusion culling), and then blocks packets for these entities. This results in the following:

- Massive client fps improvements in big bases with tons of chests and farms due to the client not correctly culling them clientside(and therefor leaving a lot of performance on the table). Seen up to 500%+ more fps! (from 52fps->270fps at the same location without anything else changed)
- Minimap mods/Hackclients are unable to see mobs/items/chests etc through walls
- Reduces sent packets since the client gets not sent entity movement of hidden entities
- Stuff "pops in" when walking around a corner(kinda the price to pay for this being done serverside)

Basically exchanging server cpu(async calculation)+memory(needs to keep snapshots of everything for the other thread + caching) for client fps and to some degree anticheat. Also I wouldn't recommend this on a server with a ton of players, this is more geared to ur casual+ SMP server with friends or trying to keep up fps when your base becomes too much for your pc.

This will not do anything to the world(modify blocks/entities), so the worst case that could happen is getting attacked by an invisible mob due to some issue(please report if you manage to do that :D). But keep in mind: this is not 100% perfectly tested, so don't yell if an invisible creeper blew up your base or some memory leak crashed the server^^.

## Requires

- Paper/Spigot 1.16.5!
- ProtocolLib
- Noteworthy: is reload save, but might leave visual artifacts till relog

## Known issues

-  Itemframes are visually correct after respawning, but their hitbox is at the wrong location

## Credits

[RoboTricker](https://github.com/robotricker/) created the original server side async raytracing occlusion culling implementation for [Transport-Pipes](https://github.com/RoboTricker/Transport-Pipes). I took it and optimized it to a point where it's able to do multiple thousands of traces in a second over a predefined sized area(100x100x100 currently with the player in the center of it).