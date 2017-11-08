public class IntegerExample {
  private int x;
  public IntegerExample() {
    x = 0;
  }

  public int getXPlusFive() {
    int i = 100;
    int y = 500;
    while(i-- != 0) {
      System.out.println("Simulate work " + i);
      y += 47;
      y *= 22;
      y--;
    }

    return x+5;

  }

  public void incrementX() {
    x++;
  }

  public static void main(String[] args) {
    IntegerExample ex = new IntegerExample();
    assert(ex.getXPlusFive() == 5);

    for(int i=0;i<100; i++) {
      assert(ex.getXPlusFive() == 5);
    }
    ex.incrementX();
    for(int i=0;i<10; i++) {
      assert(ex.getXPlusFive() == 6);
    }
  }
}