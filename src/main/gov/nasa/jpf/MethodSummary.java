package gov.nasa.jpf;

class MethodSummary {
    public MethodContext context;
    public MethodModifications mods;

    MethodSummary(MethodContext context, MethodModifications mods) {
        this.context = context;
        this.mods = mods;
    }
}