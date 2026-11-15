package gg.rsmod.game.model.instance

/**
 * Represents valid attributes that an [InstancedMap] can have.
 *
 * @author Tom <rspsmods@gmail.com>
 */
enum class InstancedMapAttribute {
    /** Content owns the lifetime, including an empty map awaiting party reconnect/reward collection. */
    MANAGED_LIFETIME,

    /**
     * CoX 15-11 (dossier COX.txt ENGINE-PUNT): a turned multi-tile object whose rotated south-west tile leaves its chunk is placed in the
     * NEIGHBOUR chunk (when that tile is inside the map), as the OSRS client does when it builds a template region (it only checks the
     * scene bounds). Without it such an object throws, or - with bypassObjectChunkBounds (Pest Control, POH) - is left out server-side.
     */
    OBJECTS_OVERFLOW_INTO_NEIGHBOUR_CHUNK,

    /**
     * The instanced map will de-allocate if its owner logs out in the map area.
     * Requires an owner for the instanced map.
     */
    DEALLOCATE_ON_LOGOUT,

    /**
     * The instanced map will de-allocate if its owner dies while in the map area.
     * Requires an owner for the instanced map.
     */
    DEALLOCATE_ON_DEATH,
}
