package com.residencial.service;

import com.residencial.exception.RecursoNoEncontradoException;
import com.residencial.model.*;
import com.residencial.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Personal del conjunto (P6 bloque 7): trabajadores, las empresas que los prestan, sus turnos, la
 * nómina mensual y el presupuesto por cargo. Lo maneja el administrador; el contador solo lee el resumen.
 */
@Service
@Transactional
public class PersonalConjuntoService {

    private static final Pattern PERIODO = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    public record Empresa(Long id, String nombre, String nit, String contacto, String telefono, Boolean activa) {}

    public record Trabajador(Long id, String nombreCompleto, String documento, String telefono, String cargo, String vinculo,
                             Long empresaId, String empresa, BigDecimal salarioBase, LocalDate fechaIngreso, Boolean activo) {}

    public record Turno(Long id, Long trabajadorId, String trabajador, String cargo, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {}

    public record Nomina(Long id, Long trabajadorId, String trabajador, String cargo, String periodo, BigDecimal salarioBase,
                         BigDecimal bonificaciones, BigDecimal descuentos, BigDecimal neto, String estado) {}

    public record CambioNomina(BigDecimal bonificaciones, BigDecimal descuentos, String estado) {}

    public record LineaPresupuesto(String cargo, BigDecimal monto) {}

    public record LineaResumen(String cargo, BigDecimal presupuestado, BigDecimal nomina, BigDecimal diferencia) {}

    public record Resumen(String periodo, List<LineaResumen> lineas, BigDecimal totalPresupuestado, BigDecimal totalNomina, BigDecimal diferencia) {}

    private final EmpresaPrestadoraRepository empresas;
    private final TrabajadorConjuntoRepository trabajadores;
    private final TurnoTrabajadorRepository turnos;
    private final NominaTrabajadorRepository nominas;
    private final PresupuestoPersonalRepository presupuestos;

    public PersonalConjuntoService(EmpresaPrestadoraRepository empresas, TrabajadorConjuntoRepository trabajadores,
                                   TurnoTrabajadorRepository turnos, NominaTrabajadorRepository nominas,
                                   PresupuestoPersonalRepository presupuestos) {
        this.empresas = empresas;
        this.trabajadores = trabajadores;
        this.turnos = turnos;
        this.nominas = nominas;
        this.presupuestos = presupuestos;
    }

    // ---------- Empresas ----------

    @Transactional(readOnly = true)
    public List<Empresa> empresas() {
        return empresas.findAllByOrderByNombreAsc().stream().map(PersonalConjuntoService::aDTO).toList();
    }

    public Empresa crearEmpresa(Empresa e) {
        EmpresaPrestadora empresa = new EmpresaPrestadora();
        return aDTO(empresas.save(llenar(empresa, e, true)));
    }

    public Empresa actualizarEmpresa(Long id, Empresa e) {
        return aDTO(empresas.save(llenar(buscarEmpresa(id), e, false)));
    }

    public void eliminarEmpresa(Long id) {
        EmpresaPrestadora empresa = buscarEmpresa(id);
        if (trabajadores.existsByEmpresaId(id)) throw invalido("Esa empresa tiene trabajadores: pásalos a contratación directa o desactívala");
        empresas.delete(empresa);
    }

    private EmpresaPrestadora llenar(EmpresaPrestadora empresa, Empresa e, boolean crear) {
        String nombre = limpio(e.nombre());
        if (crear && nombre == null) throw invalido("El nombre de la empresa es obligatorio");
        if (nombre != null) {
            empresas.findByNombreIgnoreCase(nombre).filter(otra -> !otra.getId().equals(empresa.getId()))
                    .ifPresent(otra -> { throw invalido("Ya existe una empresa con ese nombre"); });
            empresa.setNombre(nombre);
        }
        if (e.nit() != null) empresa.setNit(limpio(e.nit()));
        if (e.contacto() != null) empresa.setContacto(limpio(e.contacto()));
        if (e.telefono() != null) empresa.setTelefono(limpio(e.telefono()));
        if (e.activa() != null) empresa.setActiva(e.activa());
        return empresa;
    }

    // ---------- Trabajadores ----------

    @Transactional(readOnly = true)
    public List<Trabajador> trabajadores() {
        return trabajadores.todos().stream().map(PersonalConjuntoService::aDTO).toList();
    }

    public Trabajador crearTrabajador(Trabajador t) {
        return aDTO(trabajadores.save(llenar(new TrabajadorConjunto(), t, true)));
    }

    public Trabajador actualizarTrabajador(Long id, Trabajador t) {
        return aDTO(trabajadores.save(llenar(buscarTrabajador(id), t, false)));
    }

    private TrabajadorConjunto llenar(TrabajadorConjunto w, Trabajador t, boolean crear) {
        String nombre = limpio(t.nombreCompleto());
        String documento = limpio(t.documento());
        if (crear && (nombre == null || documento == null)) throw invalido("El nombre y el documento son obligatorios");
        if (nombre != null) w.setNombreCompleto(nombre);
        if (documento != null) {
            trabajadores.findByDocumento(documento).filter(otro -> !otro.getId().equals(w.getId()))
                    .ifPresent(otro -> { throw invalido("Ya hay un trabajador con ese documento"); });
            w.setDocumento(documento);
        }
        if (t.telefono() != null) w.setTelefono(limpio(t.telefono()));
        if (t.cargo() != null || crear) {
            String cargo = t.cargo() == null ? "" : t.cargo().trim().toUpperCase(Locale.ROOT);
            if (!TrabajadorConjunto.CARGOS.contains(cargo)) throw invalido("El cargo debe ser uno de: " + String.join(", ", TrabajadorConjunto.CARGOS));
            w.setCargo(cargo);
        }
        if (t.vinculo() != null) {
            String vinculo = t.vinculo().trim().toUpperCase(Locale.ROOT);
            if (!vinculo.equals(TrabajadorConjunto.DIRECTO) && !vinculo.equals(TrabajadorConjunto.EMPRESA)) throw invalido("El vínculo debe ser DIRECTO o EMPRESA");
            w.setVinculo(vinculo);
        }
        if (t.empresaId() != null) w.setEmpresa(buscarEmpresa(t.empresaId()));
        if (TrabajadorConjunto.DIRECTO.equals(w.getVinculo())) w.setEmpresa(null);
        else if (w.getEmpresa() == null) throw invalido("Indica la empresa que presta a este trabajador");
        if (t.salarioBase() != null) {
            if (t.salarioBase().signum() < 0) throw invalido("El salario no puede ser negativo");
            w.setSalarioBase(t.salarioBase());
        }
        if (t.fechaIngreso() != null) w.setFechaIngreso(t.fechaIngreso());
        if (t.activo() != null) w.setActivo(t.activo());
        return w;
    }

    // ---------- Turnos ----------

    @Transactional(readOnly = true)
    public List<Turno> turnos(LocalDate desde, LocalDate hasta) {
        LocalDate d = desde == null ? LocalDate.now().minusDays(7) : desde;
        LocalDate h = hasta == null ? d.plusDays(30) : hasta;
        return turnos.entre(d, h).stream().map(PersonalConjuntoService::aDTO).toList();
    }

    public Turno crearTurno(Turno t) {
        if (t.trabajadorId() == null || t.fecha() == null || t.horaInicio() == null || t.horaFin() == null) {
            throw invalido("Indica el trabajador, la fecha y las horas del turno");
        }
        if (!t.horaInicio().isBefore(t.horaFin())) throw invalido("La hora de inicio debe ser anterior a la de fin");
        TrabajadorConjunto w = buscarTrabajador(t.trabajadorId());
        if (!w.isActivo()) throw invalido("El trabajador está inactivo");
        if (!turnos.cruzados(w.getId(), t.fecha(), t.horaInicio(), t.horaFin(), -1L).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese trabajador ya tiene un turno que se cruza con ese horario");
        }
        TurnoTrabajador turno = new TurnoTrabajador();
        turno.setTrabajador(w);
        turno.setFecha(t.fecha());
        turno.setHoraInicio(t.horaInicio());
        turno.setHoraFin(t.horaFin());
        return aDTO(turnos.save(turno));
    }

    public void eliminarTurno(Long id) {
        if (!turnos.existsById(id)) throw new RecursoNoEncontradoException("Turno no encontrado: " + id);
        turnos.deleteById(id);
    }

    // ---------- Nómina ----------

    @Transactional(readOnly = true)
    public List<Nomina> nomina(String periodo) {
        return nominas.delPeriodo(periodo(periodo)).stream().map(PersonalConjuntoService::aDTO).toList();
    }

    /** Una fila por trabajador activo que aún no la tenga (con su salario base); repetirlo no duplica. */
    public List<Nomina> generarNomina(String periodo) {
        String p = periodo(periodo);
        for (TrabajadorConjunto w : trabajadores.activos()) {
            if (nominas.existsByTrabajadorIdAndPeriodo(w.getId(), p)) continue;
            NominaTrabajador n = new NominaTrabajador();
            n.setTrabajador(w);
            n.setPeriodo(p);
            n.setSalarioBase(w.getSalarioBase());
            n.setNeto(w.getSalarioBase());
            nominas.save(n);
        }
        return nomina(p);
    }

    public Nomina ajustarNomina(Long id, CambioNomina c) {
        NominaTrabajador n = nominas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Nómina no encontrada: " + id));
        if (NominaTrabajador.PAGADA.equals(n.getEstado())) throw invalido("Una nómina pagada no se cambia");
        if (c.bonificaciones() != null) {
            if (c.bonificaciones().signum() < 0) throw invalido("La bonificación no puede ser negativa");
            n.setBonificaciones(c.bonificaciones());
        }
        if (c.descuentos() != null) {
            if (c.descuentos().signum() < 0) throw invalido("El descuento no puede ser negativo");
            n.setDescuentos(c.descuentos());
        }
        BigDecimal neto = n.getSalarioBase().add(n.getBonificaciones()).subtract(n.getDescuentos());
        if (neto.signum() < 0) throw invalido("Los descuentos no pueden pasar del salario");
        n.setNeto(neto);
        if (c.estado() != null) {
            String estado = c.estado().trim().toUpperCase(Locale.ROOT);
            if (!estado.equals(NominaTrabajador.GENERADA) && !estado.equals(NominaTrabajador.PAGADA)) throw invalido("El estado debe ser GENERADA o PAGADA");
            n.setEstado(estado);
        }
        return aDTO(nominas.save(n));
    }

    // ---------- Presupuesto ----------

    @Transactional(readOnly = true)
    public List<LineaPresupuesto> presupuesto(String periodo) {
        return presupuestos.findByPeriodo(periodo(periodo)).stream()
                .map(l -> new LineaPresupuesto(l.getCargo(), l.getMontoPresupuestado())).toList();
    }

    /** Crea o cambia el presupuesto de cada cargo enviado en ese mes. */
    public List<LineaPresupuesto> fijarPresupuesto(String periodo, List<LineaPresupuesto> lineas) {
        String p = periodo(periodo);
        for (LineaPresupuesto l : lineas == null ? List.<LineaPresupuesto>of() : lineas) {
            String cargo = l.cargo() == null ? "" : l.cargo().trim().toUpperCase(Locale.ROOT);
            if (!TrabajadorConjunto.CARGOS.contains(cargo)) throw invalido("Cargo no válido: " + l.cargo());
            if (l.monto() == null || l.monto().signum() < 0) throw invalido("El monto presupuestado no puede ser negativo");
            PresupuestoPersonal fila = presupuestos.findByPeriodoAndCargo(p, cargo).orElseGet(PresupuestoPersonal::new);
            fila.setPeriodo(p);
            fila.setCargo(cargo);
            fila.setMontoPresupuestado(l.monto());
            presupuestos.save(fila);
        }
        return presupuesto(p);
    }

    /** Lo presupuestado contra lo que suma la nómina del mes, por cargo. */
    @Transactional(readOnly = true)
    public Resumen resumen(String periodo) {
        String p = periodo(periodo);
        Map<String, BigDecimal> presup = presupuestos.findByPeriodo(p).stream()
                .collect(Collectors.toMap(PresupuestoPersonal::getCargo, PresupuestoPersonal::getMontoPresupuestado));
        Map<String, BigDecimal> real = new HashMap<>();
        for (NominaTrabajador n : nominas.delPeriodo(p)) real.merge(n.getTrabajador().getCargo(), n.getNeto(), BigDecimal::add);
        List<LineaResumen> lineas = new ArrayList<>();
        BigDecimal totalP = BigDecimal.ZERO;
        BigDecimal totalN = BigDecimal.ZERO;
        for (String cargo : TrabajadorConjunto.CARGOS) {
            BigDecimal a = presup.getOrDefault(cargo, BigDecimal.ZERO);
            BigDecimal b = real.getOrDefault(cargo, BigDecimal.ZERO);
            if (a.signum() == 0 && b.signum() == 0) continue;
            lineas.add(new LineaResumen(cargo, a, b, a.subtract(b)));
            totalP = totalP.add(a);
            totalN = totalN.add(b);
        }
        return new Resumen(p, lineas, totalP, totalN, totalP.subtract(totalN));
    }

    // ---------- Utilidades ----------

    private EmpresaPrestadora buscarEmpresa(Long id) {
        return empresas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada: " + id));
    }

    private TrabajadorConjunto buscarTrabajador(Long id) {
        return trabajadores.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Trabajador no encontrado: " + id));
    }

    private static String periodo(String p) {
        if (p == null || !PERIODO.matcher(p.trim()).matches()) throw invalido("El periodo debe ser AAAA-MM, por ejemplo 2026-11");
        return p.trim();
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }

    private static Empresa aDTO(EmpresaPrestadora e) {
        return new Empresa(e.getId(), e.getNombre(), e.getNit(), e.getContacto(), e.getTelefono(), e.isActiva());
    }

    private static Trabajador aDTO(TrabajadorConjunto t) {
        return new Trabajador(t.getId(), t.getNombreCompleto(), t.getDocumento(), t.getTelefono(), t.getCargo(), t.getVinculo(),
                t.getEmpresa() == null ? null : t.getEmpresa().getId(), t.getEmpresa() == null ? null : t.getEmpresa().getNombre(),
                t.getSalarioBase(), t.getFechaIngreso(), t.isActivo());
    }

    private static Turno aDTO(TurnoTrabajador t) {
        return new Turno(t.getId(), t.getTrabajador().getId(), t.getTrabajador().getNombreCompleto(), t.getTrabajador().getCargo(),
                t.getFecha(), t.getHoraInicio(), t.getHoraFin());
    }

    private static Nomina aDTO(NominaTrabajador n) {
        return new Nomina(n.getId(), n.getTrabajador().getId(), n.getTrabajador().getNombreCompleto(), n.getTrabajador().getCargo(),
                n.getPeriodo(), n.getSalarioBase(), n.getBonificaciones(), n.getDescuentos(), n.getNeto(), n.getEstado());
    }
}
