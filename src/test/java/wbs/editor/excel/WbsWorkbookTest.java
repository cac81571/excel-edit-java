package wbs.editor.excel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wbs.editor.model.JapaneseDates;
import wbs.editor.model.LayoutConfig;
import wbs.editor.model.WbsFilter;
import wbs.editor.model.WbsItem;
import wbs.editor.model.WbsRow;

class WbsWorkbookTest {
    @TempDir
    Path temp;

    @Test
    void showsOwnTasksAndParentsOnly() throws Exception {
        Path file = sampleLikeScreenshot(temp.resolve("wbs.xlsx"));
        try (WbsWorkbook book = WbsWorkbook.open(file, new LayoutConfig())) {
            book.loadDay(LocalDate.of(2026, 10, 4));
            List<WbsRow> rows = WbsFilter.visible(book.items(), "菅原");

            assertEquals(List.of("1", "1.1", "1.1.1", "1.1.2", "1.10", "A", "B", "3"), wbsOf(rows));
            assertFalse(row(rows, "1").owned());
            assertFalse(row(rows, "1.1").owned());
            assertEquals("田中", row(rows, "1.1").item().assignee());
            assertTrue(row(rows, "1.1.1").owned());
            assertTrue(row(rows, "1.10").owned());
            assertTrue(row(rows, "3").owned());
            assertFalse(row(rows, "A").owned());
            assertEquals(2.5, row(rows, "1.1.1").item().actual(), 0.001);
            assertEquals(1.5, row(rows, "1.1.2").item().actual(), 0.001);
            assertEquals("1", book.items().get(find(book, "1.10").parentIndex()).wbsNo());
            assertEquals("1.1", book.items().get(find(book, "1.1.1").parentIndex()).wbsNo());
            assertEquals("A", book.items().get(find(book, "B").parentIndex()).wbsNo());
            assertTrue(book.assignees().contains("菅原"));
            assertTrue(book.assignees().contains("佐藤"));
            assertFalse(rows.stream().anyMatch(item -> item.item().wbsNo().equals("1.2")));
            assertFalse(rows.stream().anyMatch(item -> item.item().wbsNo().equals("2")));
        }
    }

    @Test
    void rejectsMissingDateWithoutAddingColumn() throws Exception {
        Path file = sampleLikeScreenshot(temp.resolve("missing-date.xlsx"));
        LocalDate october4 = LocalDate.of(2026, 10, 4);
        LocalDate september30 = LocalDate.of(2026, 9, 30);
        try (WbsWorkbook book = WbsWorkbook.open(file, new LayoutConfig())) {
            book.loadDay(october4);
            WbsItem item = find(book, "1.1.1");
            item.setPlan(4.0);
            item.setActual(8.0);
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> book.writeDay(september30));
            assertTrue(error.getMessage().contains("20260930"));
            assertFalse(book.hasDate(september30));
            assertEquals(8.0, item.actual(), 0.001);
            assertEquals(4.0, item.plan(), 0.001);
            assertTrue(item.isDirty());
        }
    }

    @Test
    void doesNotAddColumnWhenNothingChanged() throws Exception {
        Path file = sampleLikeScreenshot(temp.resolve("clean.xlsx"));
        try (WbsWorkbook book = WbsWorkbook.open(file, new LayoutConfig())) {
            LocalDate missing = LocalDate.of(2026, 10, 8);
            book.loadDay(missing);
            assertFalse(book.hasDate(missing));
            book.writeDay(missing);
            assertFalse(book.hasDate(missing));
        }
    }

    @Test
    void createsTimestampedBackupBeforeOverwrite() throws Exception {
        Path file = sampleLikeScreenshot(temp.resolve("wbs.xlsx"));
        byte[] before = Files.readAllBytes(file);
        try (WbsWorkbook book = WbsWorkbook.open(file, new LayoutConfig())) {
            book.loadDay(LocalDate.of(2026, 10, 4));
            find(book, "1.1.1").setActual(9.0);
            book.writeDay(LocalDate.of(2026, 10, 4));
            Optional<Path> backup = book.save(file);
            assertTrue(backup.isPresent());
            assertTrue(Files.isRegularFile(backup.get()));
            assertEquals(WbsWorkbook.backupDirectory(), backup.get().getParent());
            assertTrue(backup.get().getFileName().toString().matches("wbs_\\d{8}_\\d{6}\\.xlsx"));
            assertArrayEquals(before, Files.readAllBytes(backup.get()));
            Files.deleteIfExists(backup.get());
        }
        try (WbsWorkbook book = WbsWorkbook.open(file, new LayoutConfig())) {
            book.loadDay(LocalDate.of(2026, 10, 4));
            assertEquals(9.0, find(book, "1.1.1").actual(), 0.001);
        }
    }

    @Test
    void clearsAndWritesZero() throws Exception {
        Path file = sampleLikeScreenshot(temp.resolve("clear.xlsx"));
        LocalDate october4 = LocalDate.of(2026, 10, 4);
        try (WbsWorkbook book = WbsWorkbook.open(file, new LayoutConfig())) {
            book.loadDay(october4);
            find(book, "1.1.1").setActual(null);
            find(book, "1.1.2").setActual(0.0);
            book.writeDay(october4);
            book.save(file);
        }
        try (WbsWorkbook book = WbsWorkbook.open(file, new LayoutConfig())) {
            book.loadDay(october4);
            assertNull(find(book, "1.1.1").actual());
            assertEquals(0.0, find(book, "1.1.2").actual(), 0.001);
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            Cell zero = sheet.getRow(10).getCell(8);
            assertEquals("0.00", zero.getCellStyle().getDataFormatString());
            assertNull(workbook.getCalculationChain());
        }
    }

    @Test
    void readsConfiguredSheetAndCellPositions() throws Exception {
        Path file = temp.resolve("custom.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet cover = workbook.createSheet("表紙");
            Row wrong = cover.createRow(8);
            wrong.createCell(2).setCellValue(1);
            wrong.createCell(3).setCellValue("9");
            wrong.createCell(4).setCellValue("読み間違い");
            wrong.createCell(6).setCellValue("予定");

            Sheet sheet = workbook.createSheet("作業");
            sheet.createRow(5).createCell(7).setCellValue("10月1日");
            sheet.createRow(6).createCell(7).setCellValue("木");
            writeCustomItem(sheet, 8, 1, "1", "大項目", "", null);
            writeCustomItem(sheet, 10, 2, "1.1", "小項目", "菅原", 4.0);
            try (OutputStream out = Files.newOutputStream(file)) {
                workbook.write(out);
            }
        }

        LayoutConfig config = new LayoutConfig();
        config.sheetName = "作業";
        config.dateRow = 6;
        config.weekdayRow = 7;
        config.dataStartRow = 9;
        config.levelColumn = 2;
        config.wbsColumn = 3;
        config.nameColumn = 4;
        config.assigneeColumn = 5;
        config.kindColumn = 6;
        config.firstDateColumn = 7;

        try (WbsWorkbook book = WbsWorkbook.open(file, config)) {
            assertEquals("作業", book.sheetName());
            book.loadDay(LocalDate.of(2026, 10, 1));
            List<WbsRow> rows = WbsFilter.visible(book.items(), "菅原");
            assertEquals(List.of("1", "1.1"), wbsOf(rows));
            assertFalse(row(rows, "1").owned());
            assertEquals(4.0, row(rows, "1.1").item().actual(), 0.001);
            assertFalse(book.items().stream().anyMatch(item -> "読み間違い".equals(item.name())));
        }
    }

    @Test
    void usesConfiguredPlanLabel() throws Exception {
        Path file = temp.resolve("label.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            sheet.createRow(1).createCell(5).setCellValue("10月1日");
            Row plan = sheet.createRow(3);
            plan.createCell(0).setCellValue(1);
            plan.createCell(1).setCellValue("1");
            plan.createCell(2).setCellValue("作業");
            plan.createCell(3).setCellValue("菅原");
            plan.createCell(4).setCellValue("計画");
            Row actual = sheet.createRow(4);
            actual.createCell(4).setCellValue("実績");
            actual.createCell(5).setCellValue(4);
            try (OutputStream out = Files.newOutputStream(file)) {
                workbook.write(out);
            }
        }

        try (WbsWorkbook defaults = WbsWorkbook.open(file, new LayoutConfig())) {
            defaults.loadDay(LocalDate.of(2026, 10, 1));
            assertNull(find(defaults, "1").actual());
        }
        LayoutConfig config = new LayoutConfig();
        config.planLabel = "計画";
        try (WbsWorkbook book = WbsWorkbook.open(file, config)) {
            book.loadDay(LocalDate.of(2026, 10, 1));
            assertEquals(4.0, find(book, "1").actual(), 0.001);
        }
    }

    @Test
    void readsNumericAndFormulaDateHeadersWithoutDateFormat() throws Exception {
        Path file = temp.resolve("serial-dates.xlsx");
        LocalDate start = LocalDate.of(2026, 10, 1);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("WBS");
            Row dates = sheet.createRow(1);
            Row weeks = sheet.createRow(2);
            dates.createCell(5).setCellValue("10月1日");
            weeks.createCell(5).setCellValue("木");
            // Plain serial without date format — POI does not treat this as isCellDateFormatted.
            dates.createCell(6).setCellValue(DateUtil.getExcelDate(java.sql.Date.valueOf(start)));
            weeks.createCell(6).setCellFormula("TEXT(G2,\"aaa\")");
            for (int i = 1; i < 7; i++) {
                char prev = (char) ('G' + i - 1);
                char col = (char) ('G' + i);
                dates.createCell(6 + i).setCellFormula(prev + "2+1");
                weeks.createCell(6 + i).setCellFormula("TEXT(" + col + "2,\"aaa\")");
            }
            int row = pair(sheet, 3, 1, "1", "大項目", "", null);
            row = pair(sheet, row, 2, "1.1", "中項目", "田中", null);
            pair(sheet, row, 3, "1.1.1", "小項目1", "菅原", null);
            sheet.getRow(8).createCell(9).setCellValue(2.5);
            try (OutputStream out = Files.newOutputStream(file)) {
                workbook.write(out);
            }
        }

        try (WbsWorkbook book = WbsWorkbook.open(file, new LayoutConfig())) {
            LocalDate october4 = LocalDate.of(2026, 10, 4);
            assertTrue(book.hasDate(october4));
            book.loadDay(october4);
            assertEquals(2.5, find(book, "1.1.1").actual(), 0.001);

            find(book, "1.1.1").setActual(3.0);
            book.writeDay(october4);
            book.save(file);
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("10月1日", sheet.getRow(1).getCell(5).getStringCellValue());
            assertEquals(CellType.NUMERIC, sheet.getRow(1).getCell(6).getCellType());
            assertEquals(3.0, sheet.getRow(8).getCell(9).getNumericCellValue(), 0.001);
            assertEquals(CellType.FORMULA, sheet.getRow(1).getCell(9).getCellType());
        }
    }

    @Test
    void rejectsWriteWhenExcelDateColumnIsMissing() throws Exception {
        Path file = temp.resolve("dates.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("m\"月\"d\"日\""));
            CellStyle weekStyle = workbook.createCellStyle();
            weekStyle.setDataFormat(workbook.createDataFormat().getFormat("aaa"));
            Row dates = sheet.createRow(1);
            Row weeks = sheet.createRow(2);
            for (int i = 0; i < 2; i++) {
                LocalDate date = LocalDate.of(2026, 10, 1).plusDays(i);
                Cell dateCell = dates.createCell(5 + i);
                dateCell.setCellValue(date);
                dateCell.setCellStyle(dateStyle);
                Cell weekCell = weeks.createCell(5 + i);
                weekCell.setCellValue(date);
                weekCell.setCellStyle(weekStyle);
            }
            Row plan = sheet.createRow(3);
            plan.createCell(0).setCellValue(1);
            plan.createCell(1).setCellValue("1");
            plan.createCell(2).setCellValue("作業");
            plan.createCell(3).setCellValue("菅原");
            plan.createCell(4).setCellValue("予定");
            sheet.createRow(4).createCell(4).setCellValue("実績");
            try (OutputStream out = Files.newOutputStream(file)) {
                workbook.write(out);
            }
        }

        try (WbsWorkbook book = WbsWorkbook.open(file, new LayoutConfig())) {
            book.loadDay(LocalDate.of(2026, 10, 1));
            find(book, "1").setActual(1.25);
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> book.writeDay(LocalDate.of(2026, 10, 3)));
            assertTrue(error.getMessage().contains("20261003"));
            assertFalse(book.hasDate(LocalDate.of(2026, 10, 3)));
        }
    }

    @Test
    void reportsMissingSheet() throws Exception {
        Path file = sampleLikeScreenshot(temp.resolve("missing.xlsx"));
        LayoutConfig config = new LayoutConfig();
        config.sheetName = "ない";
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> WbsWorkbook.open(file, config));
        assertTrue(error.getMessage().contains("ない"));
        assertTrue(error.getMessage().contains("WBS"));
    }

    private static Path sampleLikeScreenshot(Path file) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("WBS");
            sheet.createRow(0).createCell(14).setCellValue("メモ");
            Row dates = sheet.createRow(1);
            Row header = sheet.createRow(2);
            header.createCell(0).setCellValue("Level");
            header.createCell(1).setCellValue("WBS No");
            header.createCell(3).setCellValue("担当");
            LocalDate start = LocalDate.of(2026, 10, 1);
            for (int i = 0; i < 7; i++) {
                LocalDate date = start.plusDays(i);
                dates.createCell(5 + i).setCellValue(date.getMonthValue() + "月" + date.getDayOfMonth() + "日");
                header.createCell(5 + i).setCellValue(JapaneseDates.weekday(date));
            }
            int row = 3;
            row = pair(sheet, row, 1, "1", "大項目", "", null);
            row = pair(sheet, row, 2, "1.1", "中項目", "田中", null);
            row = pair(sheet, row, 3, "1.1.1", "小項目1", "菅原", 2.5);
            row = pair(sheet, row, 3, "1.1.2", "小項目2", "菅原", 1.5);
            row = pair(sheet, row, 2, "1.10", "十項目", "菅原", null);
            row = pair(sheet, row, 2, "1.2", "別中項目", "田中", null);
            row = pair(sheet, row, 3, "1.2.1", "対象外", "田中", 9.0);
            row = pair(sheet, row, 1, "2", "別大項目", "田中", null);
            row = pair(sheet, row, 1, "A", "階層A", "", null);
            row = pair(sheet, row, 2, "B", "階層B", "菅原", 1.0);
            pair(sheet, row, 1, "3", "兼務", "佐藤、菅原", null);
            try (OutputStream out = Files.newOutputStream(file)) {
                workbook.write(out);
            }
        }
        return file;
    }

    private static int pair(Sheet sheet, int row, int level, String wbs, String name, String assignee, Double actual) {
        Row plan = sheet.createRow(row);
        plan.createCell(0).setCellValue(level);
        plan.createCell(1).setCellValue(wbs);
        plan.createCell(2).setCellValue(name);
        if (!assignee.isEmpty()) {
            plan.createCell(3).setCellValue(assignee);
        }
        plan.createCell(4).setCellValue("予定");
        Row actualRow = sheet.createRow(row + 1);
        actualRow.createCell(4).setCellValue("実績");
        if (actual != null) {
            actualRow.createCell(8).setCellValue(actual);
        }
        return row + 2;
    }

    private static void writeCustomItem(Sheet sheet, int row, int level, String wbs, String name, String assignee, Double actual) {
        Row plan = sheet.createRow(row);
        plan.createCell(2).setCellValue(level);
        plan.createCell(3).setCellValue(wbs);
        plan.createCell(4).setCellValue(name);
        if (!assignee.isEmpty()) {
            plan.createCell(5).setCellValue(assignee);
        }
        plan.createCell(6).setCellValue("予定");
        Row actualRow = sheet.createRow(row + 1);
        actualRow.createCell(6).setCellValue("実績");
        if (actual != null) {
            actualRow.createCell(7).setCellValue(actual);
        }
    }

    private static WbsItem find(WbsWorkbook book, String wbs) {
        return book.items().stream().filter(item -> item.wbsNo().equals(wbs)).findFirst().orElseThrow();
    }

    private static WbsRow row(List<WbsRow> rows, String wbs) {
        return rows.stream().filter(item -> item.item().wbsNo().equals(wbs)).findFirst().orElseThrow();
    }

    private static List<String> wbsOf(List<WbsRow> rows) {
        return rows.stream().map(item -> item.item().wbsNo()).toList();
    }

    private static int findHeader(Row row, String text) {
        for (int column = 0; column < 20; column++) {
            Cell cell = row.getCell(column);
            if (cell != null && text.equals(cell.getStringCellValue())) {
                return column;
            }
        }
        throw new AssertionError("header not found: " + text);
    }
}
