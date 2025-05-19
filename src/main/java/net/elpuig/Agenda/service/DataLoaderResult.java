package net.elpuig.Agenda.service; // O el paquete donde decidas poner los modelos

import net.elpuig.Agenda.model.Reserva;
import java.util.List;
import java.util.ArrayList; // Necesitas importar ArrayList

// Clase simple para encapsular el resultado del DataLoader
public class DataLoaderResult {
    private final List<Reserva> reservas; // Lista de reservas cargadas y validadas sin errores de formato/parseo
    private final List<String> errores;    // Lista de errores encontrados durante la carga/validación

    public DataLoaderResult(List<Reserva> reservas, List<String> errores) {
        this.reservas = reservas;
        this.errores = errores;
    }

    public List<Reserva> getReservas() {
        return reservas;
    }

    public List<String> getErrores() {
        return errores;
    }
}