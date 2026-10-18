package co.sv.consultorio.service;

import co.sv.consultorio.domain.Appointment;
import co.sv.consultorio.domain.AppointmentType;
import co.sv.consultorio.domain.AppointmentView;
import co.sv.consultorio.domain.Patient;
import co.sv.consultorio.domain.PatientOverview;
import co.sv.consultorio.domain.Process;
import co.sv.consultorio.util.Fmt;
import co.sv.consultorio.util.Texts;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Reportes de pacientes en Excel y PDF. */
public class ReportService {

    /**
     * @param range true = solo los registrados entre {@code from} y {@code to}; false = total de pacientes
     */
    public record ReportRequest(boolean range, LocalDate from, LocalDate to, Long processId,
                                PatientFilter.CitaFilter cita, PatientFilter.WhatsAppFilter whatsApp,
                                boolean includeInactive) {
    }

    static final String[] COLUMNS = {"Nombre", "Documento", "Fecha de nacimiento", "Edad", "Teléfono", "Procedimiento",
            "N.º de citas", "N.º de controles", "Citas", "WhatsApp", "Fecha de registro", "Estado"};

    private final PatientService patients;
    private final ProcessService processes;
    private final AppointmentService appointments;
    private final TimeSource time;

    public ReportService(PatientService patients, ProcessService processes, AppointmentService appointments, TimeSource time) {
        this.patients = patients;
        this.processes = processes;
        this.appointments = appointments;
        this.time = time;
    }

    public List<PatientOverview> rows(ReportRequest r) {
        return patients.search(new PatientFilter(null, r.processId(), r.cita(), r.whatsApp(),
                r.range() ? r.from() : null, r.range() ? r.to() : null, false, r.includeInactive()));
    }

    /** Conteo por proceso (en orden alfabético, incluye los que tienen 0). */
    public Map<String, Integer> countsByProcess(ReportRequest r, List<PatientOverview> rows) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Process p : processes.list()) {
            if (r.processId() == null || r.processId() == p.id()) {
                out.put(p.name(), 0);
            }
        }
        for (PatientOverview o : rows) {
            if (o.process() != null) {
                out.merge(o.process().name(), 1, Integer::sum);
            }
        }
        return out;
    }

    /** "Del 1 de septiembre al 13 de octubre de 2026" o "Todos los pacientes al 13 de octubre de 2026". */
    public String describe(ReportRequest r) {
        LocalDate today = time.today();
        if (!r.range()) {
            return "Total al " + Fmt.longDateYear(today);
        }
        LocalDate from = r.from();
        LocalDate to = r.to() == null ? today : r.to();
        if (from == null) {
            return "Hasta el " + Fmt.longDateYear(to);
        }
        String a = from.getYear() == to.getYear() ? Fmt.longDate(from) : Fmt.longDateYear(from);
        return "Del " + a + " al " + Fmt.longDateYear(to);
    }

    public void writeExcel(List<PatientOverview> rows, Path file) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(file)) {
            Sheet sh = wb.createSheet("Pacientes");
            CreationHelper ch = wb.getCreationHelper();

            CellStyle head = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font bold = wb.createFont();
            bold.setBold(true);
            head.setFont(bold);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            head.setBorderBottom(BorderStyle.THIN);

            CellStyle date = wb.createCellStyle();
            date.setDataFormat(ch.createDataFormat().getFormat("dd/mm/yyyy"));
            CellStyle text = wb.createCellStyle();
            text.setDataFormat(ch.createDataFormat().getFormat("@"));
            CellStyle wrap = wb.createCellStyle();
            wrap.setWrapText(true);
            wrap.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.TOP);

            Row h = sh.createRow(0);
            for (int i = 0; i < COLUMNS.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(COLUMNS[i]);
                c.setCellStyle(head);
            }
            Map<Long, List<AppointmentView>> history = appointments.byPatient();
            int r = 1;
            for (PatientOverview o : rows) {
                Patient p = o.patient();
                Row row = sh.createRow(r++);
                row.createCell(0).setCellValue(p.fullName());
                Cell doc = row.createCell(1);
                doc.setCellValue(p.document());
                doc.setCellStyle(text);
                setDate(row.createCell(2), p.birthDate(), date);
                row.createCell(3).setCellValue(p.age(time.today()) == null ? "" : String.valueOf(p.age(time.today())));
                row.createCell(4).setCellValue(Texts.formatPhone(p.phone()));
                row.createCell(5).setCellValue(o.process() == null ? "" : o.process().name());
                List<AppointmentView> visits = history.getOrDefault(p.id(), List.of());
                row.createCell(6).setCellValue(count(visits, AppointmentType.CITA));
                row.createCell(7).setCellValue(count(visits, AppointmentType.CONTROL));
                Cell citas = row.createCell(8);
                citas.setCellValue(historyText(visits));
                citas.setCellStyle(wrap);
                row.createCell(9).setCellValue(whatsAppText(o));
                setDate(row.createCell(10), p.registeredAt().toLocalDate(), date);
                row.createCell(11).setCellValue(p.active() ? "Activo" : "Inactivo");
                row.setHeight((short) -1);
            }
            sh.createFreezePane(0, 1);
            for (int i = 0; i < COLUMNS.length; i++) {
                sh.autoSizeColumn(i);
                sh.setColumnWidth(i, Math.min(sh.getColumnWidth(i) + 600, 12000));
            }
            wb.write(out);
        }
    }

    private static void setDate(Cell c, LocalDate d, CellStyle style) {
        if (d == null) {
            c.setCellValue("");
            return;
        }
        c.setCellValue(d);
        c.setCellStyle(style);
    }

    public void writePdf(List<PatientOverview> rows, Path file, String title, String subtitle) throws IOException {
        writePdf(rows, file, title, subtitle, null);
    }

    public void writePdf(List<PatientOverview> rows, Path file, String title, String subtitle, byte[] logo) throws IOException {
        LocalDate today = time.today();
        Document doc = new Document(PageSize.A4.rotate(), 32, 32, 32, 32);
        try (OutputStream out = Files.newOutputStream(file)) {
            PdfWriter.getInstance(doc, out);
            doc.addTitle(title);
            doc.addAuthor("Consultorio Stephany Valencia");
            doc.open();
            try {
                writePdfBody(doc, rows, title, subtitle, today, logo);
            } finally {
                doc.close();
            }
        }
    }

    private void writePdfBody(Document doc, List<PatientOverview> rows, String title, String subtitle,
                              LocalDate today, byte[] logo) throws IOException {
        Font brand = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(0x66, 0x66, 0x6C));
        Font h1 = new Font(Font.TIMES_ROMAN, 22, Font.BOLD, new Color(0x0B, 0x0B, 0x0C));
        Font sub = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(0x66, 0x66, 0x6C));
        Font th = new Font(Font.HELVETICA, 8, Font.BOLD, new Color(0x55, 0x55, 0x5A));
        Font td = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(0x0B, 0x0B, 0x0C));
        Font tdMuted = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(0x66, 0x66, 0x6C));

        PdfPTable banner = new PdfPTable(new float[]{1.1f, 8.9f});
        banner.setWidthPercentage(100);
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        logoCell.setPadding(0);
        if (logo != null && logo.length > 0) {
            Image img = Image.getInstance(logo);
            img.scaleToFit(64, 64);
            logoCell.addElement(img);
        }
        banner.addCell(logoCell);
        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        titleCell.addElement(new Paragraph("STEPHANY VALENCIA · CONTROL DE PACIENTES", brand));
        Paragraph t = new Paragraph(title, h1);
        t.setSpacingBefore(4);
        titleCell.addElement(t);
        Paragraph s = new Paragraph(subtitle + " · generado el " + Fmt.longDateYear(today), sub);
        s.setSpacingBefore(2);
        titleCell.addElement(s);
        banner.addCell(titleCell);
        banner.setSpacingAfter(14);
        doc.add(banner);

        String[] cols = {"PACIENTE", "EDAD", "TELÉFONO", "PROCEDIMIENTO", "CITAS", "CONTROLES", "FECHA Y HORA", "WHATSAPP", "REGISTRO", "ESTADO"};
        PdfPTable table = new PdfPTable(new float[]{2.1f, 0.55f, 1.1f, 1.25f, 0.55f, 0.75f, 2.3f, 1.15f, 0.8f, 0.7f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        Color headBg = new Color(0xF1, 0xF0, 0xEC);
        Color line = new Color(0xE2, 0xE0, 0xDA);
        for (String c : cols) {
            PdfPCell cell = new PdfPCell(new Phrase(c, th));
            cell.setBackgroundColor(headBg);
            cell.setBorderColor(line);
            cell.setPadding(6);
            table.addCell(cell);
        }
        Map<Long, List<AppointmentView>> history = appointments.byPatient();
        int citas = 0;
        int controles = 0;
        for (PatientOverview o : rows) {
            Patient p = o.patient();
            List<AppointmentView> visits = history.getOrDefault(p.id(), List.of());
            int nCitas = count(visits, AppointmentType.CITA);
            int nControles = count(visits, AppointmentType.CONTROL);
            citas += nCitas;
            controles += nControles;
            Phrase name = new Phrase();
            name.add(new Phrase(p.fullName() + "\n", td));
            name.add(new Phrase("CC " + p.document(), tdMuted));
            addCell(table, name, line);
            addCell(table, new Phrase(p.age(today) == null ? "" : p.age(today) + " años", td), line);
            addCell(table, new Phrase(Texts.formatPhone(p.phone()), td), line);
            addCell(table, new Phrase(o.process() == null ? "" : o.process().name(), td), line);
            addCell(table, new Phrase(String.valueOf(nCitas), td), line);
            addCell(table, new Phrase(String.valueOf(nControles), td), line);
            addCell(table, new Phrase(historyText(visits), td), line);
            addCell(table, new Phrase(whatsAppText(o), td), line);
            addCell(table, new Phrase(Fmt.shortDate(p.registeredAt().toLocalDate(), today), td), line);
            addCell(table, new Phrase(p.active() ? "Activo" : "Inactivo", td), line);
        }
        doc.add(table);
        Paragraph total = new Paragraph(Fmt.count(rows.size(), "paciente", "pacientes")
                + " · " + Fmt.count(citas, "cita", "citas")
                + " · " + Fmt.count(controles, "control", "controles"), sub);
        total.setSpacingBefore(10);
        total.setAlignment(Element.ALIGN_RIGHT);
        doc.add(total);
    }

    private static int count(List<AppointmentView> list, AppointmentType type) {
        int n = 0;
        for (AppointmentView v : list) {
            if (v.appointment().type() == type) {
                n++;
            }
        }
        return n;
    }

    private static String historyText(List<AppointmentView> list) {
        if (list.isEmpty()) {
            return "Sin citas";
        }
        return list.stream().map(v -> {
            Appointment a = v.appointment();
            return a.type().numbered(v.citaNumber()) + " · " + Fmt.appointmentLong(a.startsAt());
        }).collect(Collectors.joining("\n"));
    }

    private static void addCell(PdfPTable table, Phrase phrase, Color line) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorderColor(line);
        cell.setPadding(6);
        table.addCell(cell);
    }

    static String whatsAppText(PatientOverview o) {
        return switch (o.waStatus()) {
            case NONE -> "Sin avisar";
            case AVISADO -> o.appointment().notifiedAt() == null
                    ? "Avisado · sin respuesta"
                    : "Avisado el " + Fmt.shortDate(o.appointment().notifiedAt().toLocalDate()) + " · sin respuesta";
            case AGENDADO -> "Agendado";
            case NO_RESPONDIO -> "No respondió";
        };
    }
}
