package com.example.btaudiobalancer.service

/**
 * Device connessi in ordine di connessione. Gli effetti audio sono globali, quindi vale
 * l'ultimo connesso; quando si disconnette torna quello precedente.
 */
class ConnectedDevices {
    private val devices = LinkedHashMap<String, String>() // MAC -> nome

    val isEmpty: Boolean get() = devices.isEmpty()
    val activeMac: String? get() = devices.keys.lastOrNull()
    val activeName: String? get() = devices.values.lastOrNull()

    /** Se il device c'era gia' torna in fondo, cioe' diventa l'attivo. */
    fun connect(mac: String, name: String) {
        devices.remove(mac)
        devices[mac] = name
    }

    /**
     * Aggiunge un device di cui non si conosce l'ordine di connessione (es. profilo appena creato
     * dalla UI). Se non c'era va in testa: non scavalca l'attivo, ma torna utile quando questo si
     * disconnette. Se c'era gia' non cambia nulla.
     */
    fun join(mac: String, name: String) {
        if (mac in devices) return
        val rest = LinkedHashMap(devices)
        devices.clear()
        devices[mac] = name
        devices.putAll(rest)
    }

    /** @return true se era l'attivo, quindi gli effetti vanno riassegnati. */
    fun disconnect(mac: String): Boolean {
        val wasActive = activeMac == mac
        devices.remove(mac)
        return wasActive
    }
}
