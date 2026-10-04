package wbs.editor.model;

public final class WbsItem {
    private final int level;
    private final String wbsNo;
    private final String name;
    private final String assignee;
    private final int planRow;
    private final int actualRow;
    private int parentIndex = -1;
    private Double plan;
    private Double actual;
    private Double loadedPlan;
    private Double loadedActual;

    public WbsItem(int level, String wbsNo, String name, String assignee, int planRow, int actualRow) {
        this.level = level;
        this.wbsNo = wbsNo;
        this.name = name;
        this.assignee = assignee;
        this.planRow = planRow;
        this.actualRow = actualRow;
    }

    public int level() {
        return level;
    }

    public String wbsNo() {
        return wbsNo;
    }

    public String name() {
        return name;
    }

    public String assignee() {
        return assignee;
    }

    public int planRow() {
        return planRow;
    }

    public int actualRow() {
        return actualRow;
    }

    public int parentIndex() {
        return parentIndex;
    }

    public void setParentIndex(int parentIndex) {
        this.parentIndex = parentIndex;
    }

    public Double plan() {
        return plan;
    }

    public Double actual() {
        return actual;
    }

    public void setPlan(Double plan) {
        this.plan = plan;
    }

    public void setActual(Double actual) {
        this.actual = actual;
    }

    public void load(Double plan, Double actual) {
        this.plan = plan;
        this.actual = actual;
        this.loadedPlan = plan;
        this.loadedActual = actual;
    }

    public boolean planDirty() {
        return !Hours.same(plan, loadedPlan);
    }

    public boolean actualDirty() {
        return !Hours.same(actual, loadedActual);
    }

    public boolean isDirty() {
        return planDirty() || actualDirty();
    }

    public void markClean() {
        this.loadedPlan = plan;
        this.loadedActual = actual;
    }
}
