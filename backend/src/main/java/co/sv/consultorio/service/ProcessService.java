package co.sv.consultorio.service;

import co.sv.consultorio.domain.Process;
import co.sv.consultorio.domain.ValidationException;
import co.sv.consultorio.repo.ProcessRepository;
import co.sv.consultorio.util.Fmt;
import co.sv.consultorio.util.Texts;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProcessService {

    public static final int MAX_MONTHS = 60;
    public static final String SAMPLE_NAME = "Valeria";

    private final ProcessRepository repo;
    private final TimeSource time;

    public ProcessService(ProcessRepository repo, TimeSource time) {
        this.repo = repo;
        this.time = time;
    }

    public List<Process> list() {
        return repo.findAll();
    }

    public Process get(long id) {
        return repo.findById(id);
    }

    public Map<Long, Integer> activePatientCounts() {
        return repo.activePatientCounts();
    }

    public Process create(String name, Integer months, String template) {
        String n = Texts.cleanName(name);
        validate(0, n, months, template);
        long id = repo.insert(n, months, template.trim(), time.now());
        return repo.findById(id);
    }

    public Process update(long id, String name, Integer months, String template) {
        String n = Texts.cleanName(name);
        validate(id, n, months, template);
        repo.update(id, n, months, template.trim());
        return repo.findById(id);
    }

    /** Vista previa del mensaje con un nombre de ejemplo y la fecha que daría el proceso desde hoy. */
    public String preview(String template, String processName, Integer months) {
        LocalDate date = time.today().plusMonths(months == null || months < 1 ? 1 : months);
        String proc = Texts.isBlank(processName) ? "tu proceso" : Texts.cleanName(processName);
        return MessageTemplates.render(template, SAMPLE_NAME, proc, Fmt.messageDate(date.atTime(9, 0)));
    }

    private void validate(long id, String name, Integer months, String template) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (name.isEmpty()) {
            errors.put("name", "Escribe el nombre del proceso.");
        } else {
            Process same = repo.findByName(name);
            if (same != null && same.id() != id) {
                errors.put("name", "Ya existe un proceso con ese nombre.");
            }
        }
        if (months == null || months < 1 || months > MAX_MONTHS) {
            errors.put("months", "Escribe entre 1 y " + MAX_MONTHS + " meses.");
        }
        if (Texts.isBlank(template)) {
            errors.put("template", "Escribe el mensaje de WhatsApp.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }
}
