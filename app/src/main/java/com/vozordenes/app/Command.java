package com.vozordenes.app;

/** Una orden de voz y la acción que ejecuta. */
public final class Command {
    public final String id;
    public String name;
    public String type;   // ver Actions.T_*
    public String param;  // paquete, URI... según el tipo

    public Command(String id, String name, String type, String param) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.param = param == null ? "" : param;
    }
}
