package co.sv.consultorio.service;

import co.sv.consultorio.db.Database;
import co.sv.consultorio.domain.Patient;
import co.sv.consultorio.domain.Process;
import co.sv.consultorio.domain.ValidationException;
import co.sv.consultorio.repo.PatientRepository;
import co.sv.consultorio.util.Texts;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Importación de pacientes desde Excel con revisión previa antes de guardar. */
public class ImportService {

    public static final String[] TEMPLATE_COLUMNS = {"Nombre", "Documento", "Indicativo", "Teléfono", "Procedimiento",
            "Fecha de nacimiento"};

    enum Col { NAME, LAST, DOCUMENT, PHONE, COUNTRY, PROCESS, BIRTH, REGISTERED }

    public record ImportRow(int rowNumber, PatientInput input, String processName, LocalDate registered,
                            List<String> errors) {
        public boolean valid() {
            return errors.isEmpty();
        }

        public String fullName() {
            return Texts.cleanName(input.fullName());
        }
    }

    public record ImportPreview(String fileName, List<ImportRow> rows) {
        public long validCount() {
            return rows.stream().filter(ImportRow::valid).count();
        }

        public long errorCount() {
            return rows.size() - validCount();
        }
    }

    private static final DateTimeFormatter[] DATE_FORMATS = {
            strict("uuuu-M-d"), strict("uuuu/M/d"), strict("uuuu.M.d"),
            strict("d/M/uuuu"), strict("d-M-uuuu"), strict("d.M.uuuu"), strict("d/M/uu")};

    private final Database db;
    private final PatientService patients;
    private final PatientRepository patientRepo;
    private final ProcessService processes;
    private final TimeSource time;

    public ImportService(Database db, PatientService patients, PatientRepository patientRepo,
                         ProcessService processes, TimeSource time) {
        this.db = db;
        this.patients = patients;
        this.patientRepo = patientRepo;
        this.processes = processes;
        this.time = time;
    }

    /** Plantilla con los encabezados esperados y una hoja de instrucciones. */
    public void writeTemplate(Path file) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(file)) {
            Sheet sh = wb.createSheet("Pacientes");
            CellStyle head = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font bold = wb.createFont();
            bold.setBold(true);
            head.setFont(bold);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle text = wb.createCellStyle();
            text.setDataFormat(wb.createDataFormat().getFormat("@"));
            CellStyle date = wb.createCellStyle();
            date.setDataFormat(wb.createDataFormat().getFormat("dd/mm/yyyy"));

            Row h = sh.createRow(0);
            for (int i = 0; i < TEMPLATE_COLUMNS.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(TEMPLATE_COLUMNS[i]);
                c.setCellStyle(head);
                sh.setColumnWidth(i, 22 * 256);
                sh.setDefaultColumnStyle(i, TEMPLATE_COLUMNS[i].contains("nacimiento") ? date : text);
            }
            sh.createFreezePane(0, 1);

            Sheet help = wb.createSheet("Instrucciones");
            String[] lines = {
                    "Cómo llenar la plantilla",
                    "",
                    "• Una fila por paciente, desde la fila 2. La columna del nombre puede llamarse Nombre o Paciente.",
                    "• Documento: solo números (los puntos y espacios se quitan solos). No se puede repetir.",
                    "• Indicativo: obligatorio. Código del país, sin +. Colombia es 57.",
                    "• Teléfono: en Colombia, 10 dígitos. En otro país, entre 6 y 15.",
                    "• Procedimiento: opcional. Si se deja vacío, el paciente se guarda y se asigna después, al editarlo.",
                    "• Si se escribe un procedimiento, tiene que existir en el programa con el mismo nombre (ver lista abajo).",
                    "• Fecha de nacimiento: obligatoria. Vale dd/mm/aaaa o aaaa-mm-dd. La edad se calcula sola.",
                    "• Opcional: columna \"Fecha de registro\".",
                    "",
                    "Procedimientos existentes:"};
            int r = 0;
            for (String line : lines) {
                help.createRow(r++).createCell(0).setCellValue(line);
            }
            for (Process p : processes.list()) {
                help.createRow(r++).createCell(0).setCellValue("   " + p.name() + " (" + p.monthsLabel() + ")");
            }
            help.setColumnWidth(0, 100 * 256);
            wb.write(out);
        }
    }

    public ImportPreview parse(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file); Workbook wb = WorkbookFactory.create(in)) {
            return parse(file.getFileName().toString(), wb);
        }
    }

    ImportPreview parse(String fileName, Workbook wb) {
        Sheet sh = wb.getSheetAt(0);
        DataFormatter fmt = new DataFormatter();
        fmt.setUseCachedValuesForFormulaCells(true);
        int headerRow = -1;
        Map<Col, Integer> cols = new EnumMap<>(Col.class);
        for (int r = sh.getFirstRowNum(); r <= Math.min(sh.getLastRowNum(), sh.getFirstRowNum() + 10) && headerRow < 0; r++) {
            Row row = sh.getRow(r);
            if (row == null) {
                continue;
            }
            Map<Col, Integer> found = new EnumMap<>(Col.class);
            for (Cell c : row) {
                Col col = matchHeader(fmt.formatCellValue(c));
                if (col != null && !found.containsKey(col)) {
                    found.put(col, c.getColumnIndex());
                }
            }
            if (found.size() >= 3) {
                headerRow = r;
                cols = found;
            }
        }
        if (headerRow < 0) {
            throw new ValidationException(Map.of("file",
                    "No encontré los encabezados. Usa la plantilla: nombre, documento, indicativo, teléfono y fecha de nacimiento."));
        }

        List<ImportRow> rows = new ArrayList<>();
        Set<String> docsInFile = new HashSet<>();
        for (int r = headerRow + 1; r <= sh.getLastRowNum(); r++) {
            Row row = sh.getRow(r);
            if (row == null || isEmpty(row, fmt)) {
                continue;
            }
            rows.add(parseRow(row, r + 1, cols, fmt, docsInFile));
        }
        return new ImportPreview(fileName, rows);
    }

    private ImportRow parseRow(Row row, int rowNumber, Map<Col, Integer> cols, DataFormatter fmt, Set<String> docsInFile) {
        List<String> errors = new ArrayList<>();
        String name = Texts.cleanName(text(row, cols.get(Col.NAME), fmt));
        String last = Texts.cleanName(text(row, cols.get(Col.LAST), fmt));
        if (!last.isEmpty()) {
            name = (name + " " + last).trim();
        }
        String doc = Texts.digitsOnly(text(row, cols.get(Col.DOCUMENT), fmt));
        String phone = text(row, cols.get(Col.PHONE), fmt);
        String country = text(row, cols.get(Col.COUNTRY), fmt);
        if (Texts.digitsOnly(country).isEmpty()) {
            errors.add("Falta el indicativo.");
        }
        String procName = Texts.cleanName(text(row, cols.get(Col.PROCESS), fmt));
        LocalDate today = time.today();
        LocalDate birth = date(row, cols.get(Col.BIRTH), fmt, "nacimiento", errors, today);
        LocalDate registered = date(row, cols.get(Col.REGISTERED), fmt, "registro", errors, today);

        Process proc = findProcess(procName);
        if (!procName.isEmpty() && proc == null) {
            errors.add("El procedimiento \"" + procName + "\" no existe. Créalo primero en Procedimientos.");
        }
        PatientInput input = new PatientInput(name, doc, phone, proc == null ? null : proc.id(), birth, country);
        try {
            patients.validate(0, input, null);
        } catch (ValidationException e) {
            boolean badDate = errors.stream().anyMatch(m -> m.startsWith("Fecha de"));
            e.fieldErrors().forEach((field, msg) -> {
                boolean dateField = "birthDate".equals(field);
                if (!"processId".equals(field) && !(dateField && badDate)) {
                    errors.add(msg.replace("Escribe el", "Falta el").replace("Elige la", "Falta la"));
                }
            });
        }
        if (!doc.isEmpty() && !docsInFile.add(doc)) {
            errors.add("Documento repetido dentro del archivo.");
        }
        return new ImportRow(rowNumber, input, procName, registered, errors);
    }

    /** Guarda las filas válidas. Devuelve cuántos pacientes se crearon. */
    public int commit(ImportPreview preview) {
        LocalDateTime now = time.now();
        return db.inTransaction(() -> {
            int n = 0;
            for (ImportRow row : preview.rows()) {
                if (!row.valid()) {
                    continue;
                }
                Patient p = patients.validate(0, row.input(), null, false);
                LocalDateTime reg = row.registered() == null ? now : row.registered().atTime(now.toLocalTime());
                patientRepo.insert(new Patient(0, p.fullName(), p.document(), p.phone(), p.countryCode(), p.processId(),
                        p.birthDate(), reg, true));
                n++;
            }
            return n;
        });
    }

    private Process findProcess(String name) {
        if (name.isEmpty()) {
            return null;
        }
        String key = Texts.searchKey(name);
        return processes.list().stream().filter(p -> Texts.searchKey(p.name()).equals(key)).findFirst().orElse(null);
    }

    static Col matchHeader(String raw) {
        String h = Texts.searchKey(raw).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        if (h.isEmpty()) {
            return null;
        }
        if (h.contains("nacimiento") || h.equals("edad")) {
            return h.equals("edad") ? null : Col.BIRTH;
        }
        if (h.contains("registro")) {
            return Col.REGISTERED;
        }
        if (h.startsWith("apellido")) {
            return Col.LAST;
        }
        if (h.startsWith("nombre") || h.equals("paciente") || h.startsWith("paciente ")) {
            return Col.NAME;
        }
        if (h.contains("documento") || h.contains("cedula") || h.equals("cc") || h.equals("doc")) {
            return h.startsWith("tipo") ? null : Col.DOCUMENT;
        }
        if (h.contains("indicativo") || h.contains("pais") || h.contains("country")) {
            return Col.COUNTRY;
        }
        if (h.contains("telefono") || h.contains("celular") || h.equals("tel")) {
            return Col.PHONE;
        }
        if (h.contains("proceso") || h.contains("procedimiento")) {
            return Col.PROCESS;
        }
        return null;
    }

    private static boolean isEmpty(Row row, DataFormatter fmt) {
        for (Cell c : row) {
            if (!fmt.formatCellValue(c).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String text(Row row, Integer col, DataFormatter fmt) {
        if (col == null) {
            return "";
        }
        Cell c = row.getCell(col);
        if (c == null) {
            return "";
        }
        CellType type = type(c);
        if (type == CellType.ERROR) {
            return "";
        }
        if (type == CellType.NUMERIC && !DateUtil.isCellDateFormatted(c)) {
            double v = c.getNumericCellValue();
            if (v == Math.rint(v)) {
                return String.valueOf((long) v);
            }
        }
        return fmt.formatCellValue(c).trim();
    }

    /** Tipo de la celda; en las fórmulas, el de su último valor calculado. */
    private static CellType type(Cell c) {
        return c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
    }

    /** Quita la hora si la celda trae «1990-10-13 00:00:00» o «1990-10-13T00:00:00». */
    private static String dateOnly(String raw) {
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == ' ' || ch == 'T') {
                return raw.substring(0, i).trim();
            }
        }
        return raw;
    }

    private static DateTimeFormatter strict(String pattern) {
        return DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
    }

    private static LocalDate date(Row row, Integer col, DataFormatter fmt, String label, List<String> errors,
                                  LocalDate today) {
        if (col == null) {
            return null;
        }
        Cell c = row.getCell(col);
        if (c == null) {
            return null;
        }
        if (type(c) == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(c) || DateUtil.isValidExcelDate(c.getNumericCellValue())) {
                return c.getLocalDateTimeCellValue().toLocalDate();
            }
        }
        String raw = fmt.formatCellValue(c).trim();
        if (raw.isEmpty()) {
            return null;
        }
        String s = dateOnly(raw);
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return pivotTwoDigitYear(s, LocalDate.parse(s, f), today);
            } catch (DateTimeParseException ignored) {
                // probar el siguiente formato
            }
        }
        errors.add("Fecha de " + label + " no válida: \"" + raw + "\" (usa dd/mm/aaaa o aaaa-mm-dd).");
        return null;
    }

    /**
     * Un año de dos cifras (13/10/96) no puede quedar en 2096: para una fecha de nacimiento
     * se lee dentro del siglo que termina en el año actual (96 → 1996, 10 → 2010).
     */
    static LocalDate pivotTwoDigitYear(String raw, LocalDate parsed, LocalDate today) {
        if (parsed == null || today == null || raw == null
                || !raw.matches("\\d{1,2}[./-]\\d{1,2}[./-]\\d{2}")) {
            return parsed;
        }
        int year = (today.getYear() / 100) * 100 + Math.floorMod(parsed.getYear(), 100);
        if (year > today.getYear()) {
            year -= 100;
        }
        int day = Math.min(parsed.getDayOfMonth(), parsed.getMonth().length(java.time.Year.isLeap(year)));
        return java.time.LocalDate.of(year, parsed.getMonth(), day);
    }
}
