package com.example.policia;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

// Datos del mundo para el modo RPG (etapa 1): estaciones, alcalde y leyes,
// gremios, subastas, casas, zonas... Se guardan junto con el mundo en
// data/policia_mundo.dat. Cada sistema usa su propia seccion (getSeccion).
public class PoliciaMundo extends SavedData {
    static final String NOMBRE = "policia_mundo";
    CompoundTag raiz = new CompoundTag();

    PoliciaMundo() {
    }

    static PoliciaMundo load(CompoundTag t) {
        PoliciaMundo m = new PoliciaMundo();
        m.raiz = t.getCompound("d");
        return m;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        t.put("d", this.raiz);
        t.putInt("version", 1);
        return t;
    }

    public static PoliciaMundo get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(PoliciaMundo::load, PoliciaMundo::new, NOMBRE);
    }

    // Devuelve (y crea si hace falta) la seccion de un sistema, por ejemplo
    // "estaciones" o "gremios". Despues de cambiarla hay que llamar a setDirty().
    public CompoundTag getSeccion(String nombre) {
        if (!this.raiz.contains(nombre)) {
            this.raiz.put(nombre, new CompoundTag());
        }
        return this.raiz.getCompound(nombre);
    }

    // Dia de juego (cuenta los amaneceres). Lo usan las diarias, el interes
    // del banco, los prestamos y las elecciones.
    public long dia(MinecraftServer s) {
        return s.overworld().getDayTime() / 24000L;
    }
}
