package gov.nasa.jpf;

class MethodSummary {
    MethodContext context;
    MethodModifications mods;

    MethodSummary(MethodContext context, MethodModifications mods) {
        this.context = context;
        this.mods = mods;
    }
}