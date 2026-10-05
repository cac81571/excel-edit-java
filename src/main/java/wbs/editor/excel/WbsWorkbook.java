package wbs.editor.excel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ooxml.POIXMLDocumentPart;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import wbs.editor.model.AppSettings;
import wbs.editor.model.Hours;
import wbs.editor.model.LayoutConfig;
import wbs.editor.model.Text;
import wbs.editor.model.WbsFilter;
import wbs.editor.model.WbsItem;

public final class WbsWorkbook implements AutoCloseable {
    private static final Pattern JAPANESE_YMD = Pattern.compile("^(\\d{4})年(\\d{1,2})月(\\d{1,2})日$");
    private static final Pattern JAPANESE_MD = Pattern.compile("^(\\d{1,2})月(\\d{1,2})日$");
    private static final Pattern ISO = Pattern.compile("^(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})$");
    private static final Pattern MONTH_DAY = Pattern.compile("^(\\d{1,2})[/-](\\d{1,2})$");

    private final Workbook workbook;
    private final Sheet sheet;
    private final LayoutConfig layout;
    private final DataFormatter formatter = new DataFormatter();
    private final FormulaEvaluator evaluator;
    private final List<WbsItem> items;
    private final List<DateColumn> dateColumns = new ArrayList<>();
    private CellStyle hoursStyle;
    /** Last-modified time of the source file when it was opened (or last saved by this app). */
    private FileTime loadedFileTime;

    private WbsWorkbook(Workbook workbook, LayoutConfig layout) {
        this.workbook = workbook;
        this.layout = layout;
        this.evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        this.sheet = findSheet(workbook, layout);
        this.items = readItems();
        WbsFilter.linkParents(items);
        readDateColumns();
    }

    public static WbsWorkbook open(Path path, LayoutConfig layout) throws IOException {
        FileTime modified = Files.getLastModifiedTime(path);
        try (InputStream in = Files.newInputStream(path)) {
            WbsWorkbook opened = new WbsWorkbook(WorkbookFactory.create(in), layout);
            opened.loadedFileTime = modified;
            return opened;
        }
    }

    /**
     * Thrown when the file on disk was changed after this workbook was loaded
     * (or after the last successful save from this app).
     */
    public static final class ConcurrentFileChangeException extends IOException {
        public ConcurrentFileChangeException() {
            super("ファイルが読み込み後に他で更新されています。\n読み込み直してから再度保存してください。");
        }
    }

    public String sheetName() {
        return sheet.getSheetName();
    }

    public Optional<FileTime> loadedFileTime() {
        return Optional.ofNullable(loadedFileTime);
    }

    /** True when the workbook looks like a legacy Excel shared workbook (共有ブック). */
    public boolean isLegacyShared() {
        if (!(workbook instanceof XSSFWorkbook xssf)) {
            return false;
        }
        try {
            for (PackagePart part : xssf.getPackage().getParts()) {
                String name = part.getPartName().getName().toLowerCase(Locale.ROOT);
                if (name.contains("/revisions/")
                        || name.contains("revisionheaders")
                        || name.endsWith("/users.xml")) {
                    return true;
                }
            }
            var ct = xssf.getCTWorkbook();
            return ct != null && ct.isSetFileSharing();
        } catch (Exception ignored) {
            return false;
        }
    }

    public List<String> sheetNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            names.add(workbook.getSheetName(i));
        }
        return List.copyOf(names);
    }

    public List<WbsItem> items() {
        return List.copyOf(items);
    }

    public List<String> assignees() {
        List<String> names = new ArrayList<>();
        for (WbsItem item : items) {
            if (item.assignee().isBlank()) {
                continue;
            }
            if (!names.contains(item.assignee())) {
                names.add(item.assignee());
            }
            for (String token : WbsFilter.tokens(item.assignee())) {
                if (!names.contains(token)) {
                    names.add(token);
                }
            }
        }
        return List.copyOf(names);
    }

    public boolean hasDate(LocalDate date) {
        return columnOf(date).isPresent();
    }

    public OptionalInt columnOf(LocalDate date) {
        for (DateColumn column : dateColumns) {
            if (column.matches(date)) {
                return OptionalInt.of(column.column);
            }
        }
        return OptionalInt.empty();
    }

    public void loadDay(LocalDate date) {
        evaluator.clearAllCachedResultValues();
        OptionalInt column = columnOf(date);
        for (WbsItem item : items) {
            Double plan = column.isPresent() && item.planRow() >= 0
                    ? readNumber(item.planRow(), column.getAsInt())
                    : null;
            Double actual = column.isPresent() && item.actualRow() >= 0
                    ? readNumber(item.actualRow(), column.getAsInt())
                    : null;
            item.load(plan, actual);
        }
    }

    public boolean isDirty() {
        for (WbsItem item : items) {
            if (item.isDirty()) {
                return true;
            }
        }
        return false;
    }

    public void writeDay(LocalDate date) {
        boolean dirty = false;
        for (WbsItem item : items) {
            if (item.isDirty()) {
                dirty = true;
                break;
            }
        }
        if (!dirty) {
            return;
        }
        OptionalInt column = columnOf(date);
        if (column.isEmpty()) {
            throw new IllegalArgumentException(
                    "日付 " + String.format("%04d%02d%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth())
                            + " の列がありません。Excelに日付列を追加してから保存してください。");
        }
        int columnIndex = column.getAsInt();
        for (WbsItem item : items) {
            if (!item.isDirty()) {
                continue;
            }
            if (item.planDirty() && item.planRow() >= 0) {
                writeNumber(item.planRow(), columnIndex, item.plan());
            }
            if (item.actualDirty() && item.actualRow() >= 0) {
                writeNumber(item.actualRow(), columnIndex, item.actual());
            }
        }
    }

    /** Restores sheet cells for dirty items to their last loaded values (used after a failed save). */
    public void restoreDay(LocalDate date) {
        OptionalInt column = columnOf(date);
        if (column.isEmpty()) {
            return;
        }
        int columnIndex = column.getAsInt();
        for (WbsItem item : items) {
            if (!item.isDirty()) {
                continue;
            }
            if (item.planDirty() && item.planRow() >= 0) {
                writeNumber(item.planRow(), columnIndex, item.loadedPlan());
            }
            if (item.actualDirty() && item.actualRow() >= 0) {
                writeNumber(item.actualRow(), columnIndex, item.loadedActual());
            }
        }
    }

    public void markClean() {
        for (WbsItem item : items) {
            item.markClean();
        }
    }

    /**
     * Writes the workbook to {@code path}. If that file already exists, copies it first under
     * {@code ~/WBS実績入力/backup/} with a timestamp suffix
     * (for example {@code book_20261004_160945.xlsx}).
     *
     * @return the backup path when a backup was created, otherwise empty
     */
    public Optional<Path> save(Path path) throws IOException {
        checkNotModifiedExternally(path);
        Path backup = null;
        if (Files.isRegularFile(path)) {
            backup = backupPath(path);
            Files.createDirectories(backup.getParent());
            Files.copy(path, backup, StandardCopyOption.COPY_ATTRIBUTES);
        }
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(tmp)) {
                removeCalculationChain();
                workbook.setForceFormulaRecalculation(true);
                workbook.write(out);
            }
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            Files.deleteIfExists(tmp);
            throw ex;
        }
        loadedFileTime = Files.getLastModifiedTime(path);
        return Optional.ofNullable(backup);
    }

    void checkNotModifiedExternally(Path path) throws IOException {
        if (loadedFileTime == null || !Files.isRegularFile(path)) {
            return;
        }
        FileTime current = Files.getLastModifiedTime(path);
        if (current.toMillis() != loadedFileTime.toMillis()) {
            throw new ConcurrentFileChangeException();
        }
    }

    static Path backupDirectory() {
        return AppSettings.backupDirectory();
    }

    static Path backupPath(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        String stamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path directory = backupDirectory();
        Path candidate = directory.resolve(base + "_" + stamp + extension);
        if (!Files.exists(candidate)) {
            return candidate;
        }
        stamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        return directory.resolve(base + "_" + stamp + extension);
    }

    /**
     * Stale calcChain entries (common with shared formulas / column shifts) make Excel show
     * "found a problem with some content". Excel rebuilds the chain when it is absent.
     */
    private void removeCalculationChain() {
        if (!(workbook instanceof XSSFWorkbook xssf)) {
            return;
        }
        var chain = xssf.getCalculationChain();
        if (chain == null) {
            return;
        }
        try {
            var method = POIXMLDocumentPart.class.getDeclaredMethod(
                    "removeRelation", POIXMLDocumentPart.class, boolean.class);
            method.setAccessible(true);
            method.invoke(xssf, chain, true);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("calcChain を削除できませんでした。", ex);
        }
    }

    @Override
    public void close() {
        try {
            workbook.close();
        } catch (IOException ignored) {
            // The file is already loaded in memory.
        }
    }

    private static Sheet findSheet(Workbook workbook, LayoutConfig layout) {
        if (workbook.getNumberOfSheets() == 0) {
            throw new IllegalArgumentException("シートがありません。");
        }
        String name = layout.sheetName == null ? "" : layout.sheetName.trim();
        if (name.isEmpty()) {
            return workbook.getSheetAt(0);
        }
        Sheet sheet = workbook.getSheet(name);
        if (sheet != null) {
            return sheet;
        }
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            if (workbook.getSheetName(i).equalsIgnoreCase(name)) {
                return workbook.getSheetAt(i);
            }
        }
        List<String> names = new ArrayList<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            names.add(workbook.getSheetName(i));
        }
        throw new IllegalArgumentException(
                "シート「" + name + "」が見つかりません。存在するシート: " + String.join("、", names));
    }

    private List<WbsItem> readItems() {
        List<WbsItem> loaded = new ArrayList<>();
        int row = layout.dataStartRow - 1;
        int last = sheet.getLastRowNum();
        int blankRun = 0;
        while (row <= last) {
            if (!hasIdentity(row)) {
                if (kindAt(row).isEmpty() && ++blankRun >= 100) {
                    break;
                }
                if (!kindAt(row).isEmpty()) {
                    blankRun = 0;
                }
                row++;
                continue;
            }
            blankRun = 0;
            int planRow = row;
            int actualRow = -1;
            if (isCompanion(row, row + 1)) {
                String kind = kindAt(row);
                String nextKind = kindAt(row + 1);
                if (layout.actualLabel.equals(kind) && layout.planLabel.equals(nextKind)) {
                    actualRow = row;
                    planRow = row + 1;
                } else {
                    planRow = row;
                    actualRow = row + 1;
                }
                row += 2;
            } else {
                if (layout.actualLabel.equals(kindAt(row))) {
                    actualRow = row;
                    planRow = -1;
                }
                row++;
            }
            int identityRow = hasIdentity(planRow) ? planRow : actualRow;
            String wbs = readText(identityRow, layout.wbsColumn);
            String name = readText(identityRow, layout.nameColumn);
            String assignee = readAssignee(identityRow);
            if (assignee.isEmpty() && actualRow >= 0 && planRow >= 0) {
                int other = identityRow == planRow ? actualRow : planRow;
                assignee = readAssignee(other);
            }
            int level = parseLevel(readText(identityRow, layout.levelColumn), wbs);
            loaded.add(new WbsItem(level, wbs, name, assignee, planRow, actualRow));
        }
        return loaded;
    }

    private boolean isCompanion(int current, int next) {
        if (next > sheet.getLastRowNum()) {
            return false;
        }
        String kind = kindAt(current);
        String nextKind = kindAt(next);
        boolean labelsMatch = (layout.planLabel.equals(kind) && layout.actualLabel.equals(nextKind))
                || (layout.actualLabel.equals(kind) && layout.planLabel.equals(nextKind))
                || (kind.isEmpty() && (layout.actualLabel.equals(nextKind) || layout.planLabel.equals(nextKind)));
        return labelsMatch && !differentItem(current, next);
    }

    private boolean differentItem(int current, int next) {
        String currentWbs = readText(current, layout.wbsColumn);
        String nextWbs = readText(next, layout.wbsColumn);
        if (!nextWbs.isEmpty() && !nextWbs.equals(currentWbs)) {
            return true;
        }
        String currentName = readText(current, layout.nameColumn);
        String nextName = readText(next, layout.nameColumn);
        return !nextName.isEmpty() && !currentName.isEmpty() && !nextName.equals(currentName) && nextWbs.isEmpty();
    }

    private boolean hasIdentity(int row) {
        if (row < 0) {
            return false;
        }
        return !readText(row, layout.levelColumn).isEmpty()
                || !readText(row, layout.wbsColumn).isEmpty()
                || !readText(row, layout.nameColumn).isEmpty()
                || !readAssignee(row).isEmpty();
    }

    private String kindAt(int row) {
        return readText(row, layout.kindColumn);
    }

    private int parseLevel(String text, String wbs) {
        if (!text.isEmpty()) {
            try {
                int level = (int) Double.parseDouble(text);
                if (level > 0) {
                    return level;
                }
            } catch (NumberFormatException ignored) {
                // Use the WBS depth when the level cell is not a number.
            }
        }
        if (wbs.isEmpty()) {
            return 1;
        }
        return Math.max(1, wbs.split("\\.", -1).length);
    }

    private void readDateColumns() {
        int last = layout.firstDateColumn;
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row != null) {
                last = Math.max(last, row.getLastCellNum() - 1);
            }
        }
        int max = Math.min(last, layout.firstDateColumn + 400);
        int blanks = 0;
        for (int column = layout.firstDateColumn; column <= max; column++) {
            DateColumn parsed = parseHeader(column);
            if (parsed == null) {
                if (++blanks >= 8) {
                    break;
                }
                continue;
            }
            blanks = 0;
            dateColumns.add(parsed);
        }
    }

    private DateColumn parseHeader(int column) {
        Cell cell = cell(layout.dateRow - 1, column);
        if (cell == null) {
            return null;
        }
        try {
            LocalDate excel = excelDate(cell);
            if (excel != null) {
                return DateColumn.exact(column, excel);
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return DateColumn.fromText(column, readText(layout.dateRow - 1, column));
    }

    private boolean isExcelDate(Cell cell) {
        return excelDate(cell) != null;
    }

    private LocalDate excelDate(Cell cell) {
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            CellValue value = evaluator.evaluate(cell);
            if (value == null || value.getCellType() != CellType.NUMERIC) {
                return null;
            }
            return excelSerialDate(value.getNumberValue(), cell);
        }
        if (type == CellType.NUMERIC) {
            return excelSerialDate(cell.getNumericCellValue(), cell);
        }
        return null;
    }

    /**
     * Accepts normal date-formatted cells, and also numeric/formula serials that Excel shows as
     * dates via locale formats POI does not mark as date-formatted (for example format index 56).
     */
    private static LocalDate excelSerialDate(double serial, Cell cell) {
        if (!DateUtil.isValidExcelDate(serial) || serial < 32874) {
            // 32874 ≈ 1990-01-01. Smaller values are usually hours or plain numbers.
            return null;
        }
        boolean formatted;
        try {
            formatted = DateUtil.isCellDateFormatted(cell);
        } catch (RuntimeException ex) {
            formatted = false;
        }
        LocalDate date = DateUtil.getLocalDateTime(serial).toLocalDate();
        if (formatted) {
            return date;
        }
        int year = date.getYear();
        if (year < 1990 || year > 2100) {
            return null;
        }
        return date;
    }

    private void writeNumber(int rowIndex, int column, Double value) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        Cell cell = row.getCell(column);
        if (value == null) {
            if (cell != null) {
                cell.setBlank();
            }
            return;
        }
        if (cell == null) {
            cell = row.createCell(column);
        }
        applyHoursFormat(cell, rowIndex, column);
        cell.setCellValue(value);
    }

    private void applyHoursFormat(Cell cell, int rowIndex, int column) {
        CellStyle current = cell.getCellStyle();
        if (current != null) {
            String format = current.getDataFormatString();
            if (format != null && format.startsWith("0.00")) {
                return;
            }
        }
        cell.setCellStyle(hoursStyle(rowIndex, column, cell));
    }

    private CellStyle hoursStyle(int rowIndex, int column, Cell cell) {
        if (hoursStyle == null) {
            hoursStyle = workbook.createCellStyle();
            CellStyle sample = cell.getCellStyle();
            if (sample == null || sample.getIndex() == 0) {
                sample = sampleNumberStyle(rowIndex, column);
            }
            if (sample != null && sample.getIndex() != 0) {
                hoursStyle.cloneStyleFrom(sample);
            }
            short format = workbook.createDataFormat().getFormat("0.00");
            hoursStyle.setDataFormat(format);
        }
        return hoursStyle;
    }

    private CellStyle sampleNumberStyle(int rowIndex, int column) {
        for (DateColumn dateColumn : dateColumns) {
            if (dateColumn.column == column) {
                continue;
            }
            Cell cell = cell(rowIndex, dateColumn.column);
            if (cell != null && cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
                return cell.getCellStyle();
            }
        }
        return null;
    }

    private Double readNumber(int rowIndex, int column) {
        Cell cell = cell(rowIndex, column);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.FORMULA) {
                CellValue value = evaluator.evaluate(cell);
                if (value == null || value.getCellType() == CellType.BLANK) {
                    return null;
                }
                if (value.getCellType() == CellType.NUMERIC) {
                    return value.getNumberValue();
                }
                if (value.getCellType() == CellType.STRING) {
                    return Hours.parse(value.getStringValue());
                }
                return null;
            }
            if (cell.getCellType() == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(cell)) {
                    return null;
                }
                return cell.getNumericCellValue();
            }
            if (cell.getCellType() == CellType.STRING) {
                String text = cell.getStringCellValue();
                return text.isBlank() ? null : Hours.parse(text);
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private String readAssignee(int rowIndex) {
        return normalizeAssignee(readText(rowIndex, layout.assigneeColumn));
    }

    private static String normalizeAssignee(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty() || "0".equals(value)) {
            return "";
        }
        try {
            if (Double.parseDouble(value) == 0.0) {
                return "";
            }
        } catch (NumberFormatException ignored) {
            // keep non-numeric assignee text
        }
        return value;
    }

    private String readText(int rowIndex, int column) {
        Cell cell = cell(rowIndex, column);
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            return formatter.formatCellValue(cell, evaluator).trim();
        }
        if (type == CellType.STRING) {
            return cell.getStringCellValue().trim();
        }
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return formatter.formatCellValue(cell).trim();
            }
            double number = cell.getNumericCellValue();
            if (!Double.isNaN(number) && number == Math.rint(number) && Math.abs(number) < Long.MAX_VALUE) {
                return Long.toString((long) number);
            }
            return formatter.formatCellValue(cell).trim();
        }
        if (type == CellType.BLANK) {
            return "";
        }
        return formatter.formatCellValue(cell, evaluator).trim();
    }

    private Cell cell(int rowIndex, int column) {
        if (rowIndex < 0 || column < 0) {
            return null;
        }
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            return null;
        }
        return row.getCell(column);
    }

    private static final class DateColumn {
        private int column;
        private final LocalDate exact;
        private final int month;
        private final int day;

        private DateColumn(int column, LocalDate exact, int month, int day) {
            this.column = column;
            this.exact = exact;
            this.month = month;
            this.day = day;
        }

        static DateColumn exact(int column, LocalDate date) {
            return new DateColumn(column, date, date.getMonthValue(), date.getDayOfMonth());
        }

        static DateColumn fromText(int column, String raw) {
            String text = Text.halfWidth(raw).trim();
            text = text.replaceAll("[（(][月火水木金土日][)）]\\s*$", "").trim();
            if (text.isEmpty()) {
                return null;
            }
            Matcher japaneseYmd = JAPANESE_YMD.matcher(text);
            if (japaneseYmd.matches()) {
                LocalDate parsed = date(japaneseYmd.group(1), japaneseYmd.group(2), japaneseYmd.group(3));
                return parsed == null ? null : exact(column, parsed);
            }
            Matcher iso = ISO.matcher(text);
            if (iso.matches()) {
                LocalDate parsed = date(iso.group(1), iso.group(2), iso.group(3));
                return parsed == null ? null : exact(column, parsed);
            }
            Matcher japaneseMd = JAPANESE_MD.matcher(text);
            if (japaneseMd.matches()) {
                return monthDay(column, japaneseMd.group(1), japaneseMd.group(2));
            }
            Matcher monthDay = MONTH_DAY.matcher(text);
            if (monthDay.matches()) {
                return monthDay(column, monthDay.group(1), monthDay.group(2));
            }
            return null;
        }

        private static DateColumn monthDay(int column, String month, String day) {
            int monthValue = Integer.parseInt(month);
            int dayValue = Integer.parseInt(day);
            if (monthValue < 1 || monthValue > 12 || dayValue < 1 || dayValue > 31) {
                return null;
            }
            return new DateColumn(column, null, monthValue, dayValue);
        }

        private static LocalDate date(String year, String month, String day) {
            try {
                return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
            } catch (DateTimeException ex) {
                return null;
            }
        }

        boolean matches(LocalDate date) {
            if (exact != null) {
                return exact.equals(date);
            }
            return month == date.getMonthValue() && day == date.getDayOfMonth();
        }
    }
}
