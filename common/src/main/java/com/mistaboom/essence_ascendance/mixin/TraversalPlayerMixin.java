package com.mistaboom.essence_ascendance.mixin;

// Player.travel already steers every swimming player using FluidState.isEmpty(),
// not FluidState.is(TagKey). Lava swimming uses TraversalEntityMixin for the
// swimming flag and TraversalLivingEntityMixin for native fluid travel.
// No Player travel injection is needed. This type-free source replaces the
// obsolete injector when applying an overwrite-only update; it emits no class.
