/**
 * A small example showing that summaries correctly
 * modify the state in the presence of nested calls.
 **/


public class NestedExample {
  private int outer;
  private int inner;

  public NestedExample() {
    outer = 5;
    inner = 10;
  }

  public void outerCall() {
    innerCall();
  }

  public boolean innerCall() {
    inner = 47;

    return true;
  }

  public void setInner(int value) {
    inner = value;
  }

  public int getInner() {
    return inner;
  }

  public int getOuter() {
    return outer;
  }

  public static void main(String[] args) {
    NestedExample nest = new NestedExample();
    assert(nest.getOuter() == 5);
    assert(nest.getInner() == 10);
    nest.innerCall();

    assert(nest.getOuter() == 5);
    assert(nest.getInner() == 47);
    nest.setInner(-1);

    // outer
      // inner
    nest.outerCall();
    assert(nest.getOuter() == 5);
    assert(nest.getInner() == 47);
    // modify inner through other means
    nest.setInner(-1);
    
    // outer (summarised)
    nest.outerCall();

    assert(nest.getOuter() == 5);
    System.out.println(nest.getInner());
    assert(nest.getInner() == 47);
  }
}