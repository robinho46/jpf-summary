
class NestedExample {
  private int outer;
  private int inner;

  private NestedExample() {
    outer = 5;
    inner = 10;
  }

  private void outerCall() {
    if(innerCall()) {
      outer = 42;
    }else{
      outer = 33;
    }
  }

  private boolean innerCall() {
    if(inner == 10) {
      return true;
    } else {
      return false;
    }
  }

  private void setInner(int value) {
    inner = value;
  }

  private int getInner() {
    return inner;
  }

  private int getOuter() {
    return outer;
  }

  public static void main(String[] args) {
    NestedExample nest = new NestedExample();
    assert(nest.getOuter() == 5);
    assert(nest.getInner() == 10);
    nest.outerCall();
    assert(nest.getOuter() == 42);
    assert(nest.getInner() == 10);
    nest.setInner(-1);
    nest.outerCall();
    assert(nest.getOuter() == 33);
    assert(nest.getInner() == -1);

  }
}