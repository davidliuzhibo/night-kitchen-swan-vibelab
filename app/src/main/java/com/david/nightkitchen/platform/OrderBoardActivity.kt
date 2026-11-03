package com.david.nightkitchen.platform


/**
 * Host for the order-board window. Declared with no
 * `pico.spatial.windowcontainer` metadata so the gate package does not
 * look like a spatial client. The order window points here instead of
 * [com.pico.spatial.ui.platform.stub.SpatialStubActivity].
 */
class OrderBoardActivity : StallWindowActivity()
