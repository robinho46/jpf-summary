package gov.nasa.jpf;

import java.util.Objects;

class MethodSummary {
    public MethodContext context;
    public MethodModifications mods;

    MethodSummary(MethodContext context, MethodModifications mods) {
        this.context = context;
        this.mods = mods;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodSummary that = (MethodSummary) o;
        return context.equals(that.context) &&
                mods.equals(that.mods);
    }

    @Override
    public int hashCode() {
        return Objects.hash(context, mods);
    }
}