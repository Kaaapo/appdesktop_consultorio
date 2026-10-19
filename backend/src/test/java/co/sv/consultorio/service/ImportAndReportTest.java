package co.sv.consultorio.service;

import co.sv.consultorio.TestEnv;
import co.sv.consultorio.domain.Appointment;
import co.sv.consultorio.domain.AppointmentType;
import co.sv.consultorio.domain.Patient;
import co.sv.consultorio.domain.PatientOverview;
import co.sv.consultorio.domain.Process;
import co.sv.consultorio.domain.WaStatus;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportAndReportTest {

    private final TestEnv env = new TestEnv();

    @TempDir
    Path tmp;

    @AfterEach
    void close() {
        env.close();
    }

    @Test
    void importReviewsRowsAndSavesOnlyValidOnes() {
        Process b = env.process("Proceso B", 1);
        env.patient("Camila", "Rojas", "1017220431", b, TestEnv.TODAY);

        Workbook wb = new XSSFWorkbook();
        Sheet sh = wb.createSheet();
        row(sh, 0, "Nombre", "Número de documento", "Teléfono", "Procedimiento", "Fecha de nacimiento");
        row(sh, 1, "Valeria Acosta", "1.020.553.198", "321 555 0187", "proceso b", "13/10/1996");
        row(sh, 2, "Repetida Rojas", "1017220431", "3105550142", "Proceso B", "13/10/1990");
        row(sh, 3, "Sin Proceso", "555666", "3105550142", "Proceso Z", "13/10/1990");
        row(sh, 4, "Mala Fecha", "777888", "3105550142", "Proceso B", "31/31/2026");
        row(sh, 5, "Valeria Doble", "1020553198", "3215550187", "Proceso B", "2026-10-14");
        Row withDate = sh.createRow(6);
        withDate.createCell(0).setCellValue("Numérica Fecha");
        withDate.createCell(1).setCellValue(43555666d);
        withDate.createCell(2).setCellValue(3005550000d);
        withDate.createCell(3).setCellValue("Proceso B");
        withDate.createCell(4).setCellValue(LocalDate.of(1990, 11, 2));
        sh.createRow(7);

        ImportService.ImportPreview preview = env.s.imports().parse("test.xlsx", wb);
        assertEquals(6, preview.rows().size());
        assertEquals(2, preview.validCount());
        assertTrue(preview.rows().get(1).errors().get(0).contains("Camila Rojas"));
        assertTrue(preview.rows().get(2).errors().get(0).contains("Proceso Z"));
        assertTrue(preview.rows().get(3).errors().get(0).contains("31/31/2026"));
        assertTrue(preview.rows().get(4).errors().contains("Documento repetido dentro del archivo."));

        assertEquals(2, env.s.imports().commit(preview));
        Patient v = env.s.patients().duplicateOf("1020553198", 0);
        assertEquals("Valeria Acosta", v.fullName());
        assertEquals(LocalDate.of(1996, 10, 13), v.birthDate());
        Patient n = env.s.patients().duplicateOf("43555666", 0);
        assertEquals(LocalDate.of(1990, 11, 2), n.birthDate());
        assertEquals("3005550000", n.phone());
    }

    @Test
    void twoDigitBirthYearStaysInThePast() {
        env.process("Proceso B", 1);
        Workbook wb = new XSSFWorkbook();
        Sheet sh = wb.createSheet();
        row(sh, 0, "Nombre", "Documento", "Teléfono", "Procedimiento", "Fecha de nacimiento");
        row(sh, 1, "Nacida Noventa", "888111", "3105550142", "Proceso B", "13/10/96");
        row(sh, 2, "Nacido Diez", "888222", "3105550142", "Proceso B", "5/1/10");
        row(sh, 3, "Nacido Veintisiete", "888333", "3105550142", "Proceso B", "1/3/27");

        ImportService.ImportPreview preview = env.s.imports().parse("yy.xlsx", wb);
        assertEquals(0, preview.errorCount(), preview.rows().toString());
        assertEquals(LocalDate.of(1996, 10, 13), preview.rows().get(0).input().birthDate());
        assertEquals(LocalDate.of(2010, 1, 5), preview.rows().get(1).input().birthDate());
        assertEquals(LocalDate.of(1927, 3, 1), preview.rows().get(2).input().birthDate());
    }

    @Test
    void documentTypeColumnIsNotTakenAsDocument() {
        env.process("Proceso B", 1);
        Workbook wb = new XSSFWorkbook();
        Sheet sh = wb.createSheet();
        row(sh, 0, "Nombre", "Tipo de documento", "Número de documento", "Teléfono", "Procedimiento", "Fecha de nacimiento");
        row(sh, 1, "Laura Mejía", "CC", "43987210", "3105550142", "Proceso B", "13/10/1990");

        ImportService.ImportPreview preview = env.s.imports().parse("tipo.xlsx", wb);
        assertEquals(1, preview.validCount(), preview.rows().toString());
        assertEquals("43987210", preview.rows().get(0).input().document());
    }

    @Test
    void formulaCellsUseTheirCalculatedValue() {
        env.process("Proceso B", 1);
        Workbook wb = new XSSFWorkbook();
        Sheet sh = wb.createSheet();
        row(sh, 0, "Nombre", "Documento", "Teléfono", "Procedimiento", "Fecha de nacimiento");
        Row r = sh.createRow(1);
        r.createCell(0).setCellFormula("G2&\" \"&H2");
        r.createCell(1).setCellFormula("I2");
        r.createCell(2).setCellValue("3105550142");
        r.createCell(3).setCellValue("Proceso B");
        r.createCell(4).setCellFormula("DATE(1990,11,2)");
        r.createCell(6).setCellValue("Sara");
        r.createCell(7).setCellValue("Gil");
        r.createCell(8).setCellValue(52998877d);
        sh.createRow(2).createCell(0).setCellFormula("IF(G3=\"\",\"\",G3)");
        Row error = sh.createRow(3);
        error.createCell(0).setCellFormula("NA()");
        error.createCell(1).setCellValue("77788899");
        wb.getCreationHelper().createFormulaEvaluator().evaluateAll();

        ImportService.ImportPreview preview = env.s.imports().parse("formulas.xlsx", wb);
        assertEquals(2, preview.rows().size(), preview.rows().toString());
        ImportService.ImportRow sara = preview.rows().get(0);
        assertTrue(sara.valid(), sara.errors().toString());
        assertEquals("Sara Gil", sara.fullName());
        assertEquals("52998877", sara.input().document());
        assertEquals(LocalDate.of(1990, 11, 2), sara.input().birthDate());
        assertTrue(preview.rows().get(1).errors().contains("Falta el nombre completo."));
    }

    @Test
    void impossibleBirthDateIsReportedInsteadOfAdjusted() {
        env.process("Proceso B", 1);
        Workbook wb = new XSSFWorkbook();
        Sheet sh = wb.createSheet();
        row(sh, 0, "Nombre", "Documento", "Teléfono", "Procedimiento", "Fecha de nacimiento");
        row(sh, 1, "Ana Uno", "888111", "3105550142", "Proceso B", "31/02/1990");

        ImportService.ImportRow row = env.s.imports().parse("fecha.xlsx", wb).rows().get(0);
        assertNull(row.input().birthDate());
        assertTrue(row.errors().get(0).contains("31/02/1990"), row.errors().toString());
    }

    @Test
    void whatsAppTextDoesNotCrashWithoutNotificationDate() {
        Appointment a = new Appointment(1, 1, TestEnv.NOW, AppointmentType.CITA, null, null,
                WaStatus.AVISADO, TestEnv.NOW);
        Patient p = new Patient(1, "Ana Uno", "1111", "3105550142", 1,
                TestEnv.TODAY.minusYears(30), TestEnv.NOW, true);
        PatientOverview o = new PatientOverview(p, null, a, 1, 0, null);
        assertEquals("Avisado · sin respuesta", ReportService.whatsAppText(o));
    }

    @Test
    void templateCanBeReadBack() throws Exception {
        env.process("Proceso B", 1);
        Path file = tmp.resolve("plantilla.xlsx");
        env.s.imports().writeTemplate(file);
        try (InputStream in = Files.newInputStream(file); Workbook wb = WorkbookFactory.create(in)) {
            Row h = wb.getSheetAt(0).getRow(0);
            assertEquals("Nombre", h.getCell(0).getStringCellValue());
            assertEquals("Fecha de nacimiento", h.getCell(4).getStringCellValue());
            assertEquals(0, env.s.imports().parse("plantilla.xlsx", wb).rows().size());
        }
    }

    @Test
    void reportsFilterAndExport() throws Exception {
        Process a = env.process("Proceso A", 2);
        Process b = env.process("Proceso B", 1);
        env.patient("Ana", "Uno", "1111", a, TestEnv.TODAY);
        env.patient("Bea", "Dos", "2222", b, TestEnv.TODAY);
        Patient c = env.patient("Cata", "Tres", "3333", b, TestEnv.TODAY);
        env.s.patients().deactivate(c.id());

        ReportService.ReportRequest total = new ReportService.ReportRequest(false, null, null, null,
                PatientFilter.CitaFilter.ALL, PatientFilter.WhatsAppFilter.ALL, false);
        List<PatientOverview> rows = env.s.reports().rows(total);
        assertEquals(2, rows.size());
        Map<String, Integer> counts = env.s.reports().countsByProcess(total, rows);
        assertEquals(1, counts.get("Proceso A"));
        assertEquals(1, counts.get("Proceso B"));

        ReportService.ReportRequest withInactive = new ReportService.ReportRequest(true, LocalDate.of(2026, 9, 1),
                TestEnv.TODAY, b.id(), PatientFilter.CitaFilter.ALL, PatientFilter.WhatsAppFilter.ALL, true);
        assertEquals(2, env.s.reports().rows(withInactive).size());
        assertEquals("Del 1 de septiembre al 13 de octubre de 2026", env.s.reports().describe(withInactive));

        Path xlsx = tmp.resolve("r.xlsx");
        Path pdf = tmp.resolve("r.pdf");
        env.s.reports().writeExcel(rows, xlsx);
        env.s.reports().writePdf(rows, pdf, "2 pacientes", "Total");
        assertTrue(Files.size(xlsx) > 3000);
        assertTrue(Files.size(pdf) > 1000);
        try (InputStream in = Files.newInputStream(xlsx); Workbook wb = WorkbookFactory.create(in)) {
            assertEquals(3, wb.getSheetAt(0).getPhysicalNumberOfRows());
        }
    }

    private static void row(Sheet sh, int r, String... values) {
        Row row = sh.createRow(r);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }
}
