class IntegerExample {
  private int x;
  private IntegerExample() {
    x = 0;
  }

  private int getXPlusFive() {
    int i = 100;
    int y = 500;
    while(i-- != 0) {
      y += 47;
      y *= 22;
      y--;
    }

    return x+5;

  }

  private void incrementX() {
    x++;
  }

  public static void main(String[] args) {
    IntegerExample ex = new IntegerExample();
    assert(ex.getXPlusFive() == 5);

    for(int i=0;i<10000; i++) {
      assert(ex.getXPlusFive() == 5);
    }
    ex.incrementX();
    for(int i=0;i<10; i++) {
      assert(ex.getXPlusFive() == 6);
    }
  }
}